package com.regula.documentreader

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.FragmentManager
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.widget.Toast
import com.regula.documentreader.api.DocumentReader
import com.regula.documentreader.api.completions.IDocumentReaderCompletion
import com.regula.documentreader.api.completions.IDocumentReaderInitCompletion
import com.regula.documentreader.api.completions.IDocumentReaderPrepareCompletion
import com.regula.documentreader.api.enums.*
import com.regula.documentreader.api.results.DocumentReaderResults
import com.regula.facesdk.FaceReaderService
import com.regula.facesdk.enums.eInputFaceType
import com.regula.facesdk.structs.Image
import com.regula.facesdk.structs.MatchFacesRequest
import java.io.FileNotFoundException
import java.io.InputStream

class MainActivity : AppCompatActivity() {
    private var loadingDialog: AlertDialog? = null
    private var mainFragment: FragmentMain? = null
    private var resultsFragment: FragmentResults? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        sharedPreferences = getSharedPreferences(
            MY_SHARED_PREFS,
            Context.MODE_PRIVATE
        )

        mainFragment = FragmentMain.getInstance(completion)
        supportFragmentManager.beginTransaction().replace(R.id.mainFragment, mainFragment!!).commit()
    }

    override fun onResume() {
        super.onResume()

//        if(!DocumentReader.Instance().getDocumentReaderIsReady()) {
        val initDialog = showDialog("Initializing")
        mainFragment!!.prepareDatabase(
            this@MainActivity,
            object : IDocumentReaderPrepareCompletion {
                override fun onPrepareProgressChanged(i: Int) {
                    initDialog.setTitle("Downloading database: $i%")
                }

                override fun onPrepareCompleted(
                    b: Boolean,
                    s: String
                ) {
                    mainFragment!!.init(this@MainActivity,
                        IDocumentReaderInitCompletion { b, s ->
                            if (initDialog.isShowing) {
                                initDialog.dismiss()
                            }
                            if (!b) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Init failed:$s",
                                    Toast.LENGTH_LONG
                                )
                                    .show()
                            }
                        })
                }
            })
        //        }
    }

    override fun onPause() {
        super.onPause()
        if (loadingDialog != null) {
            loadingDialog!!.dismiss()
            loadingDialog = null
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        if (supportFragmentManager!!.backStackEntryCount > 0) {
            supportFragmentManager!!.popBackStack()
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            //Image browsing intent processed successfully
            if (requestCode == REQUEST_BROWSE_PICTURE) {
                if (data!!.data != null) {
                    val selectedImage = data.data
                    val bmp = getBitmap(selectedImage, 1920, 1080)
                    loadingDialog = showDialog("Processing image")
                    DocumentReader.Instance().recognizeImage(bmp, completion)
                }
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

                // If request is cancelled, the result arrays are empty.
                if (grantResults.size > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    //access to gallery is allowed
                    mainFragment!!.createImageBrowsingRequest()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "Permission required, to browse images",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    //DocumentReader processing callback
    private val completion =
        IDocumentReaderCompletion { action, results, error ->
            //processing is finished, all results are ready
            if (action == DocReaderAction.COMPLETE) {
                if (loadingDialog != null && loadingDialog!!.isShowing) {
                    loadingDialog!!.dismiss()
                }

                //Checking, if nfc chip reading should be performed
                if (sharedPreferences!!.getBoolean(
                        DO_RFID,
                        false
                    ) && results != null && results.chipPage != 0
                ) {
                    //setting the chip's access key - mrz on car access number
                    var accessKey: String? = null
                    if (results.getTextFieldValueByType(eVisualFieldType.FT_MRZ_STRINGS)
                            .also { accessKey = it } != null && !accessKey!!.isEmpty()
                    ) {
                        accessKey = results.getTextFieldValueByType(eVisualFieldType.FT_MRZ_STRINGS)
                            .replace("^", "").replace("\n", "")
                        DocumentReader.Instance().rfidScenario().setMrz(accessKey)
                        DocumentReader.Instance().rfidScenario().setPacePasswordType(
                            eRFID_Password_Type.PPT_MRZ
                        )
                    } else if (results.getTextFieldValueByType(eVisualFieldType.FT_CARD_ACCESS_NUMBER)
                            .also { accessKey = it } != null && !accessKey!!.isEmpty()
                    ) {
                        DocumentReader.Instance().rfidScenario().setPassword(accessKey)
                        DocumentReader.Instance().rfidScenario().setPacePasswordType(
                            eRFID_Password_Type.PPT_CAN
                        )
                    }

                    //starting chip reading
                    DocumentReader.Instance().startRFIDReader(
                        this@MainActivity
                    ) { rfidAction, results, error ->
                        if (rfidAction == DocReaderAction.COMPLETE) {
                            val rfidPortrait = results.getGraphicFieldImageByType(
                                eGraphicFieldType.GF_PORTRAIT,
                                eRPRM_ResultType.RFID_RESULT_TYPE_RFID_IMAGE_DATA
                            )
                            if (rfidPortrait != null) {
                                matchFace(results, rfidPortrait)
                            } else {
                                resultsFragment = FragmentResults.getInstance(results)
                                supportFragmentManager!!.beginTransaction()
                                    .replace(R.id.mainFragment, resultsFragment!!)
                                    .addToBackStack("xxx").commitAllowingStateLoss()
                            }
                        }
                    }
                } else if (results != null) {
                    val portrait = results.getGraphicFieldImageByType(
                        eGraphicFieldType.GF_PORTRAIT
                    )
                    if (portrait != null) {
                        matchFace(results, portrait)
                    } else {
                        resultsFragment = FragmentResults.getInstance(results)
                        supportFragmentManager!!.beginTransaction()
                            .replace(R.id.mainFragment, resultsFragment!!).addToBackStack("xxx")
                            .commitAllowingStateLoss()
                    }
                } else {
                    resultsFragment = FragmentResults.getInstance(results)
                    supportFragmentManager!!.beginTransaction().replace(
                        R.id.mainFragment,
                        resultsFragment!!
                    )
                        .addToBackStack("xxx").commitAllowingStateLoss()
                }
            } else {
                //something happened before all results were ready
                if (action == DocReaderAction.CANCEL) {
                    Toast.makeText(this@MainActivity, "Scanning was cancelled", Toast.LENGTH_LONG)
                        .show()
                } else if (action == DocReaderAction.ERROR) {
                    Toast.makeText(this@MainActivity, "Error:$error", Toast.LENGTH_LONG).show()
                }
            }
        }

    private fun matchFace(results: DocumentReaderResults, portrait: Bitmap) {
        FaceReaderService.Instance().getFaceFromCamera(
            this@MainActivity
        ) { action, capturedFace, s ->
            if (capturedFace != null) {
                val request = MatchFacesRequest()
                request.similarityThreshold = 0f
                val img = Image()
                img.id = CAPTURED_FACE
                img.tag = ".jpg"
                img.imageType = eInputFaceType.ift_Live
                img.setImage(capturedFace.image())
                request.images.add(img)

                val port = Image()
                port.id = DOCUMENT_FACE
                port.tag = ".jpg"
                port.imageType = eInputFaceType.ift_DocumentPrinted
                port.setImage(portrait)
                request.images.add(port)
                FaceReaderService.Instance().matchFaces(
                    request
                ) { i, matchFacesResponse, s ->
                    resultsFragment =
                        FragmentResults.getInstance(results, request, matchFacesResponse)
                    supportFragmentManager!!.beginTransaction()
                        .replace(R.id.mainFragment, resultsFragment!!).addToBackStack("xxx")
                        .commitAllowingStateLoss()
                }
            }
        }
    }

    private fun showDialog(msg: String): AlertDialog {
        val dialog =
            AlertDialog.Builder(this@MainActivity)
        val dialogView =
            layoutInflater.inflate(R.layout.simple_dialog, null)
        dialog.setTitle(msg)
        dialog.setView(dialogView)
        dialog.setCancelable(false)
        return dialog.show()
    }

    // loads bitmap from uri
    private fun getBitmap(
        selectedImage: Uri,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap {
        val resolver = this@MainActivity.contentResolver
        var `is`: InputStream? = null
        try {
            `is` = resolver.openInputStream(selectedImage)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        }
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeStream(`is`, null, options)

        //Re-reading the input stream to move it's pointer to start
        try {
            `is` = resolver.openInputStream(selectedImage)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        }
        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, targetWidth, targetHeight)
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeStream(`is`, null, options)
    }

    // see https://developer.android.com/topic/performance/graphics/load-bitmap.html
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        bitmapWidth: Int,
        bitmapHeight: Int
    ): Int {
        // Raw height and width of image
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > bitmapHeight || width > bitmapWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while (halfHeight / inSampleSize > bitmapHeight
                && halfWidth / inSampleSize > bitmapWidth
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    companion object {
        const val REQUEST_BROWSE_PICTURE = 11
        const val PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 22
        const val MY_SHARED_PREFS = "MySharedPrefs"
        const val DO_RFID = "doRfid"
        const val RESULTS_TAG = "resultsFragment"
        const val CAPTURED_FACE = 1
        const val DOCUMENT_FACE = 2
        var sharedPreferences: SharedPreferences? = null
    }
}