package com.regula.documentreader

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.regula.documentreader.Helpers.Companion.PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE
import com.regula.documentreader.Helpers.Companion.REQUEST_BROWSE_PICTURE
import com.regula.documentreader.Helpers.Companion.RFID_RESULT
import com.regula.documentreader.Helpers.Companion.colorString
import com.regula.documentreader.Helpers.Companion.createImageBrowsingRequest
import com.regula.documentreader.Helpers.Companion.drawable
import com.regula.documentreader.Helpers.Companion.getBitmap
import com.regula.documentreader.Scan.Companion.ACTION_TYPE_GALLERY
import com.regula.documentreader.SettingsActivity.Companion.functionality
import com.regula.documentreader.SettingsActivity.Companion.isRfidEnabled
import com.regula.documentreader.SettingsActivity.Companion.useCustomRfidActivity
import com.regula.documentreader.api.DocumentReader.Instance
import com.regula.documentreader.api.completions.IDocumentReaderCompletion
import com.regula.documentreader.api.completions.IDocumentReaderPrepareCompletion
import com.regula.documentreader.api.enums.DocReaderAction
import com.regula.documentreader.api.enums.FrameShapeType
import com.regula.documentreader.api.enums.eRFID_Password_Type
import com.regula.documentreader.api.enums.eVisualFieldType
import com.regula.documentreader.api.errors.DocumentReaderException
import com.regula.documentreader.api.results.DocumentReaderResults
import com.regula.documentreader.databinding.ActivityMainBinding
import java.io.Serializable
import java.util.*

class MainActivity : FragmentActivity(), Serializable {
    @Transient
    private lateinit var binding: ActivityMainBinding

    @Transient
    private var loadingDialog: AlertDialog? = null

    @Transient
    private var initDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Helpers.opaqueStatusBar(binding.root)
        if (Instance().documentReaderIsReady)
            onInitComplete()

        binding.helpBtn.setOnClickListener(OnClickListenerSerializable {
            BottomSheet.newInstance(
                "Information",
                arrayListOf(
                    BSItem("Documents"),
                    BSItem("Core"),
                    BSItem("Scenarios")
                ),
                true,
            ) { Helpers.openLink(this, it.title) }.show(supportFragmentManager, "")
        })
        binding.settingsBtn.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = CommonRecyclerAdapter(getRvData())
        binding.recyclerView.addItemDecoration(DividerItemDecoration(this, 1))
    }

    override fun onResume() {
        super.onResume()
        if (Instance().documentReaderIsReady)
            return

        initDialog = showDialog("Initializing")

        val licInput = resources.openRawResource(R.raw.regula)
        val license = ByteArray(licInput.available())
        licInput.read(license)
        Instance().prepareDatabase(this, "Full", getPrepareCompletion(license))
        licInput.close()
    }

    private fun getPrepareCompletion(license: ByteArray) =
        object : IDocumentReaderPrepareCompletion {
            override fun onPrepareProgressChanged(progress: Int) {
                initDialog!!.setTitle("Downloading database: $progress%")
            }

            override fun onPrepareCompleted(status: Boolean, error: DocumentReaderException?) {
                Instance().initializeReader(this@MainActivity, license)
                { success, error_initializeReader ->
                    if (initDialog!!.isShowing)
                        initDialog!!.dismiss()
                    Instance().customization().edit().setShowHelpAnimation(false).apply()

                    if (success)
                        onInitComplete()
                    else
                        Toast.makeText(
                            this@MainActivity,
                            "Init failed:$error_initializeReader",
                            Toast.LENGTH_LONG
                        ).show()
                }
            }
        }

    private fun onInitComplete() {
        var currentScenario: String? = Instance().processParams().scenario
        val scenarios: Array<String?> = arrayOfNulls(Instance().availableScenarios.size)
        for ((i, scenario) in Instance().availableScenarios.withIndex())
            scenarios[i] = scenario.name
        if (currentScenario == null || currentScenario.isEmpty()) {
            currentScenario = scenarios[0]
            Instance().processParams().scenario = currentScenario
        }
        binding.scenarioPicker.visibility = View.VISIBLE
        binding.scenarioPicker.maxValue = scenarios.size - 1
        binding.scenarioPicker.wrapSelectorWheel = false
        binding.scenarioPicker.displayedValues = scenarios
        binding.scenarioPicker.value = scenarios.indexOf(currentScenario)
        binding.scenarioPicker.setOnValueChangedListener { _, _, newVal ->
            binding.scenarioPicker.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            Instance().processParams().scenario = scenarios[newVal]
        }

        binding.recyclerView.visibility = View.VISIBLE
    }

    override fun onPause() {
        super.onPause()
        loadingDialog?.dismiss()
        loadingDialog = null
    }

    private fun showDialog(msg: String): AlertDialog {
        val dialog = MaterialAlertDialogBuilder(this)
        val dialogView =
            layoutInflater.inflate(R.layout.simple_dialog, binding.root, false)
        dialog.setTitle(msg)
        dialog.background = ResourcesCompat.getDrawable(resources, R.drawable.rounded, theme)
        dialog.setView(dialogView)
        dialog.setCancelable(false)
        return dialog.show()
    }

    @Transient
    private val completion = IDocumentReaderCompletion { action, results, error ->
        if (action == DocReaderAction.COMPLETE) {
            if (loadingDialog != null && loadingDialog!!.isShowing)
                loadingDialog!!.dismiss()

            if (isRfidEnabled && results != null && results.chipPage != 0) {
                var accessKey: String?
                accessKey = results.getTextFieldValueByType(eVisualFieldType.FT_MRZ_STRINGS)
                if (accessKey != null && accessKey.isNotEmpty()) {
                    accessKey = results.getTextFieldValueByType(eVisualFieldType.FT_MRZ_STRINGS)
                        .replace("^", "").replace("\n", "")
                    Instance().rfidScenario().setMrz(accessKey)
                    Instance().rfidScenario().setPacePasswordType(eRFID_Password_Type.PPT_MRZ)
                } else {
                    accessKey =
                        results.getTextFieldValueByType(eVisualFieldType.FT_CARD_ACCESS_NUMBER)
                    if (accessKey != null && accessKey.isNotEmpty()) {
                        Instance().rfidScenario().setPassword(accessKey)
                        Instance().rfidScenario().setPacePasswordType(eRFID_Password_Type.PPT_CAN)
                    }
                }
                if (!useCustomRfidActivity)
                    Instance().startRFIDReader(this) { rfidAction, results_RFIDReader, _ ->
                        if (rfidAction == DocReaderAction.COMPLETE || rfidAction == DocReaderAction.CANCEL)
                            displayResults(results_RFIDReader)
                    }
                else {
                    MainActivity.results = results
                    val rfidIntent = Intent(this@MainActivity, CustomRfidActivity::class.java)
                    startActivityForResult(rfidIntent, RFID_RESULT)
                }
            } else
                displayResults(results)
        } else
            if (action == DocReaderAction.CANCEL)
                Toast.makeText(this, "Scanning was cancelled", Toast.LENGTH_LONG).show()
            else if (action == DocReaderAction.ERROR)
                Toast.makeText(this, "Error:$error", Toast.LENGTH_LONG).show()
    }

    private fun displayResults(documentReaderResults: DocumentReaderResults) {
        ResultsActivity.results = documentReaderResults
        startActivity(Intent(this, ResultsActivity::class.java))
    }

    fun showScanner() = Instance().showScanner(this, completion)

    fun recognizeImage() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE
            )
        } else
            createImageBrowsingRequest(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            RFID_RESULT -> results?.let { displayResults(it) }
            REQUEST_BROWSE_PICTURE -> if (resultCode == Activity.RESULT_OK) data!!.data?.let {
                val selectedImage = it
                val bmp = getBitmap(selectedImage, 1920, 1080, this)
                loadingDialog = showDialog("Processing image")
                bmp?.let { Instance().recognizeImage(bmp, completion) }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    createImageBrowsingRequest(this)
                else
                    Toast.makeText(this, "Permission required, to browse images", Toast.LENGTH_LONG)
                        .show()
            }
        }
    }

    private fun getRvData(): List<Base> {
        val rvData = mutableListOf<Base>()
        rvData.add(Section("Default"))
        rvData.add(Scan("Default (showScanner)", resetFunctionality = false) {
            Helpers.setFunctionality(functionality)
        })
        rvData.add(Scan("Gallery (recognizeImage)", ACTION_TYPE_GALLERY))
        rvData.add(Section("Custom camera frame"))
        rvData.add(Scan("Custom border width") {
            Instance().customization().edit().setCameraFrameBorderWidth(10).apply()
        })
        rvData.add(Scan("Custom border color") {
            Instance().customization().edit()
                .setCameraFrameDefaultColor(colorString(Color.RED)).apply()
            Instance().customization().edit()
                .setCameraFrameActiveColor(colorString(Color.MAGENTA)).apply()
        })
        rvData.add(Scan("Custom shape") {
            Instance().customization().edit().setCameraFrameShapeType(FrameShapeType.CORNER).apply()
            Instance().customization().edit().setCameraFrameLineLength(40).apply()
            Instance().customization().edit().setCameraFrameCornerRadius(10f).apply()
            Instance().customization().edit().setCameraFrameLineCap(Paint.Cap.ROUND).apply()
        })
        rvData.add(Scan("Custom offset") {
            Instance().customization().edit().setCameraFrameOffsetWidth(50).apply()
        })
        rvData.add(Scan("Custom aspect ratio") {
            Instance().customization().edit().setCameraFramePortraitAspectRatio(1f).apply()
            Instance().customization().edit().setCameraFrameLandscapeAspectRatio(1f).apply()
        })
        rvData.add(Scan("Custom position") {
            Instance().customization().edit().setCameraFrameVerticalPositionMultiplier(0.5f).apply()
        })
        rvData.add(Section("Custom toolbar"))
        rvData.add(Scan("Custom torch button") {
            Instance().functionality().edit().setShowTorchButton(true).apply()
            Instance().customization().edit().setTorchImageOn(drawable(R.drawable.light_on, this))
                .apply()
            Instance().customization().edit().setTorchImageOff(drawable(R.drawable.light_off, this))
                .apply()
        })
        rvData.add(Scan("Custom camera switch button") {
            Instance().functionality().edit().setShowCameraSwitchButton(true).apply()
            Instance().customization().edit()
                .setCameraSwitchButtonImage(drawable(R.drawable.camera, this)).apply()
        })
        rvData.add(Scan("Custom capture button") {
            Instance().functionality().edit().setShowCaptureButton(true).apply()
            Instance().functionality().edit().setShowCaptureButtonDelayFromStart(0).apply()
            Instance().functionality().edit().setShowCaptureButtonDelayFromDetect(0).apply()
            Instance().customization().edit()
                .setCaptureButtonImage(drawable(R.drawable.capture, this)).apply()
        })
        rvData.add(Scan("Custom change frame button") {
            Instance().functionality().edit().setShowChangeFrameButton(true).apply()
            Instance().customization().edit()
                .setChangeFrameExpandButtonImage(drawable(R.drawable.expand, this)).apply()
            Instance().customization().edit()
                .setChangeFrameCollapseButtonImage(drawable(R.drawable.collapse, this)).apply()
        })
        rvData.add(Scan("Custom close button") {
            Instance().functionality().edit().setShowCloseButton(true).apply()
            Instance().customization().edit().setCloseButtonImage(drawable(R.drawable.close, this))
                .apply()
        })
        rvData.add(Scan("Custom size of the toolbar") {
            Instance().customization().edit().setToolbarSize(120f).apply()
            Instance().customization().edit().setTorchImageOn(drawable(R.drawable.light_on, this))
                .apply()
            Instance().customization().edit().setTorchImageOff(drawable(R.drawable.light_off, this))
                .apply()
            Instance().customization().edit()
                .setCloseButtonImage(drawable(R.drawable.big_close, this)).apply()
        })
        rvData.add(Section("Custom status messages"))
        rvData.add(Scan("Custom text") {
            Instance().customization().edit().setShowStatusMessages(true).apply()
            Instance().customization().edit().setStatus("Custom status").apply()
        })
        rvData.add(Scan("Custom text font") {
            Instance().customization().edit().setShowStatusMessages(true).apply()
            Instance().customization().edit().setStatusTextFont(Typeface.DEFAULT_BOLD).apply()
            Instance().customization().edit().setStatusTextSize(24).apply()
        })
        rvData.add(Scan("Custom text color") {
            Instance().customization().edit().setShowStatusMessages(true).apply()
            Instance().customization().edit().setStatusTextColor(colorString(Color.BLUE)).apply()
        })
        rvData.add(Scan("Custom position") {
            Instance().customization().edit().setShowStatusMessages(true).apply()
            Instance().customization().edit().setStatusPositionMultiplier(0.5f).apply()
        })
        rvData.add(Section("Custom result status messages"))
        rvData.add(Scan("Custom text") {
            Instance().customization().edit().setShowResultStatusMessages(true).apply()
            Instance().customization().edit().setResultStatus("Custom result status").apply()
        })
        rvData.add(Scan("Custom text font") {
            Instance().customization().edit().setShowResultStatusMessages(true).apply()
            Instance().customization().edit().setResultStatusTextFont(Typeface.DEFAULT_BOLD).apply()
            Instance().customization().edit().setResultStatusTextSize(24).apply()
        })
        rvData.add(Scan("Custom text color") {
            Instance().customization().edit().setShowResultStatusMessages(true).apply()
            Instance().customization().edit().setResultStatusTextColor(colorString(Color.BLUE))
                .apply()
        })
        rvData.add(Scan("Custom background color") {
            Instance().customization().edit().setShowResultStatusMessages(true).apply()
            Instance().customization().edit()
                .setResultStatusBackgroundColor(colorString(Color.BLUE)).apply()
        })
        rvData.add(Scan("Custom position") {
            Instance().customization().edit().setShowResultStatusMessages(true).apply()
            Instance().customization().edit().setResultStatusPositionMultiplier(0.5f).apply()
        })
        rvData.add(Section("Free custom status"))
        rvData.add(Scan("Free text + position") {
            val status = SpannableString("Hello, world!")
            status.setSpan(AbsoluteSizeSpan(50), 0, status.length, 0)
            status.setSpan(ForegroundColorSpan(Color.RED), 0, status.length, 0)
            status.setSpan(StyleSpan(Typeface.ITALIC), 0, status.length, 0)
            Instance().customization().edit().setCustomLabelStatus(status).apply()
            Instance().customization().edit().setCustomStatusPositionMultiplier(0.5f).apply()
        })
        rvData.add(Section("Custom animations"))
        rvData.add(Scan("Help animation image") {
            Instance().customization().edit().setShowHelpAnimation(true).apply()
            Instance().customization().edit()
                .setHelpAnimationImage(drawable(R.drawable.credit_card, this)).apply()
        })
        rvData.add(Scan("Custom the next page animation") {
            Instance().customization().edit().setShowNextPageAnimation(true).apply()
            Instance().customization().edit()
                .setMultipageAnimationFrontImage(drawable(R.drawable.one, this)).apply()
            Instance().customization().edit()
                .setMultipageAnimationBackImage(drawable(R.drawable.two, this)).apply()
        })
        rvData.add(Section("Custom tint color"))
        rvData.add(Scan("Activity indicator") {
            Instance().customization().edit().setActivityIndicatorColor(colorString(Color.RED))
                .apply()
        })
        rvData.add(Scan("Next page button") {
            Instance().functionality().edit().setShowSkipNextPageButton(true).apply()
            Instance().customization().edit()
                .setMultipageButtonBackgroundColor(colorString(Color.RED)).apply()
        })
        rvData.add(Scan("All visual elements") {
            Instance().customization().edit().setTintColor(colorString(Color.BLUE)).apply()
        })
        rvData.add(Section("Custom background"))
        rvData.add(Scan("No background mask") {
            Instance().customization().edit().setShowBackgroundMask(false).apply()
        })
        rvData.add(Scan("Custom alpha") {
            Instance().customization().edit().setBackgroundMaskAlpha(0.8f).apply()
        })
        rvData.add(Scan("Custom background image") {
            Instance().customization().edit()
                .setBorderBackgroundImage(drawable(R.drawable.viewfinder, this)).apply()
        })
        rvData.add(Scan("", Scan.ACTION_TYPE_CUSTOM))
        return rvData
    }

    companion object {
        var results: DocumentReaderResults? = null
    }
}
