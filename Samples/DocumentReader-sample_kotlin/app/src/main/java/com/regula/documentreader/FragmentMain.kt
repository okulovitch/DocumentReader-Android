package com.regula.documentreader

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.ListView
import android.widget.TextView
import com.regula.documentreader.MainActivity.Companion.sharedPreferences
import com.regula.documentreader.api.DocumentReader
import com.regula.documentreader.api.completions.IDocumentReaderCompletion
import com.regula.documentreader.api.completions.IDocumentReaderInitCompletion
import com.regula.documentreader.api.completions.IDocumentReaderPrepareCompletion
import java.util.*

class FragmentMain : Fragment() {
    private var selectedPosition = 0
    private var doRfid = false
    private var showScanner: TextView? = null
    private var recognizeImage: TextView? = null
    private var doRfidCb: CheckBox? = null
    private var scenarioLv: ListView? = null
    private var activity: Activity? = null
    private var completion: IDocumentReaderCompletion? = null
    override fun onAttach(context: Context) {
        super.onAttach(context)
        activity = context as Activity
    }

    override fun onDetach() {
        super.onDetach()
        activity = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        showScanner = view.findViewById(R.id.showScannerLink)
        recognizeImage = view.findViewById(R.id.recognizeImageLink)
        scenarioLv = view.findViewById(R.id.scenariosList)
        doRfidCb = view.findViewById(R.id.doRfidCb)
    }

    override fun onResume() {
        super.onResume()
        if (DocumentReader.Instance().documentReaderIsReady) {
            successfulInit()
        }
    }

    fun prepareDatabase(
        context: Context?,
        completion: IDocumentReaderPrepareCompletion
    ) {
        //preparing database files, it will be downloaded from network only one time and stored on user device
        DocumentReader.Instance()
            .prepareDatabase(context!!, "Full", object : IDocumentReaderPrepareCompletion {
                override fun onPrepareProgressChanged(i: Int) {
                    completion.onPrepareProgressChanged(i)
                }

                override fun onPrepareCompleted(
                    b: Boolean,
                    s: String
                ) {
                    completion.onPrepareCompleted(b, s)
                }
            })
    }

    fun init(
        context: Context?,
        initCompletion: IDocumentReaderInitCompletion
    ) {
        try {
            val licInput = resources.openRawResource(R.raw.regula)
            val available = licInput.available()
            val license = ByteArray(available)
            licInput.read(license)

            //Initializing the reader
            DocumentReader.Instance()
                .initializeReader(context, license) { success, error ->
                    DocumentReader.Instance().customization().edit()
                        .setShowResultStatusMessages(true)
                        .setShowStatusMessages(true)
                    DocumentReader.Instance().functionality().isVideoCaptureMotionControl = true

                    //initialization successful
                    if (success) {
                        successfulInit()
                    }
                    initCompletion.onInitCompleted(success, error)
                }
            licInput.close()
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    private fun successfulInit() {
        showScanner!!.setOnClickListener { //starting video processing
            DocumentReader.Instance().showScanner(context!!, completion!!)
        }
        recognizeImage!!.setOnClickListener {
            //checking for image browsing permissions
            if (ContextCompat.checkSelfPermission(
                    activity!!,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    activity!!,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    MainActivity.PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE
                )
            } else {
                //start image browsing
                createImageBrowsingRequest()
            }
        }
        if (DocumentReader.Instance().canRFID) {
            //reading shared preferences
            doRfid = sharedPreferences?.getBoolean(MainActivity.DO_RFID, false) ?: false
            doRfidCb!!.isChecked = doRfid
            doRfidCb!!.setOnCheckedChangeListener { compoundButton, checked ->
                doRfid = checked
                sharedPreferences?.edit()?.putBoolean(MainActivity.DO_RFID, checked)
                    ?.apply()
            }
        } else {
            doRfidCb!!.visibility = View.GONE
        }

        //getting current processing scenario and loading available scenarios to ListView
        var currentScenario = DocumentReader.Instance().processParams().scenario
        val scenarios = ArrayList<String>()
        for (scenario in DocumentReader.Instance().availableScenarios) {
            scenarios.add(scenario.name)
        }

        //setting default scenario
        if (currentScenario == null || currentScenario.isEmpty()) {
            currentScenario = scenarios[0]
            DocumentReader.Instance().processParams().scenario = currentScenario
        }
        val adapter =
            ScenarioAdapter(
                activity!!,
                android.R.layout.simple_list_item_1,
                scenarios
            )
        selectedPosition = 0
        try {
            selectedPosition = adapter.getPosition(currentScenario)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        scenarioLv!!.adapter = adapter
        scenarioLv!!.setSelection(selectedPosition)
        scenarioLv!!.onItemClickListener =
            OnItemClickListener { adapterView, view, i, l -> //setting selected scenario to DocumentReader params
                DocumentReader.Instance().processParams().scenario = adapter.getItem(i)
                selectedPosition = i
                adapter.notifyDataSetChanged()
            }
    }

    // creates and starts image browsing intent
    // results will be handled in onActivityResult method
    fun createImageBrowsingRequest() {
        val intent = Intent()
        intent.type = "image/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(
            Intent.createChooser(intent, "Select Picture"),
            MainActivity.REQUEST_BROWSE_PICTURE
        )
    }

    internal inner class ScenarioAdapter(
        context: Context,
        resource: Int,
        objects: List<String?>
    ) : ArrayAdapter<String?>(context, resource, objects) {
        override fun getView(
            position: Int,
            convertView: View?,
            parent: ViewGroup
        ): View {
            val view = super.getView(position, convertView, parent)
            if (position == selectedPosition) {
                view.setBackgroundColor(Color.LTGRAY)
            } else {
                view.setBackgroundColor(Color.TRANSPARENT)
            }
            return view
        }
    }

    companion object {
        private var instance: FragmentMain? = null
        fun getInstance(completion: IDocumentReaderCompletion?): FragmentMain? {
            if (instance == null) {
                instance = FragmentMain()
            }
            instance!!.completion = completion
            return instance
        }
    }
}