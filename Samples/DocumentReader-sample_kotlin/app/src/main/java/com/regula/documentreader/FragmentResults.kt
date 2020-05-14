package com.regula.documentreader

import android.graphics.Bitmap
import android.os.Bundle
import android.support.v4.app.Fragment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.regula.documentreader.MainActivity
import com.regula.documentreader.api.enums.eGraphicFieldType
import com.regula.documentreader.api.enums.eVisualFieldType
import com.regula.documentreader.api.results.DocumentReaderResults
import com.regula.facesdk.results.MatchFacesResponse
import com.regula.facesdk.structs.MatchFacesRequest

class FragmentResults : Fragment() {
    private var nameTv: TextView? = null
    private var comparisonTv: TextView? = null
    private var portraitIv: ImageView? = null
    private var docImageIv: ImageView? = null
    private var etalonIv: ImageView? = null
    private var realIv: ImageView? = null
    private var authResults: LinearLayout? = null
    private var results: DocumentReaderResults? = null
    private var request: MatchFacesRequest? = null
    private var response: MatchFacesResponse? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_results, container, false)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        nameTv = view.findViewById(R.id.nameTv)
        comparisonTv = view.findViewById(R.id.comparisonValue)
        portraitIv = view.findViewById(R.id.portraitIv)
        docImageIv = view.findViewById(R.id.documentImageIv)
        etalonIv = view.findViewById(R.id.etalonImage)
        realIv = view.findViewById(R.id.realImage)
        authResults = view.findViewById(R.id.authHolder)
        displayResults(results)
    }

    private fun clearResults() {
        nameTv!!.text = ""
        portraitIv!!.setImageResource(R.drawable.portrait)
        docImageIv!!.setImageResource(R.drawable.id)
    }

    //show received results on the UI
    private fun displayResults(results: DocumentReaderResults?) {
        clearResults()
        if (results != null) {
            val name =
                results.getTextFieldValueByType(eVisualFieldType.FT_SURNAME_AND_GIVEN_NAMES)
            if (name != null) {
                nameTv!!.text = name
            }

            // through all text fields
            if (results.textResult != null && results.textResult!!.fields != null) {
                for (textField in results.textResult!!.fields) {
                    val value =
                        results.getTextFieldValueByType(textField.fieldType, textField.lcid)
                    Log.d(
                        "MainActivity", """
     $value

     """.trimIndent()
                    )
                }
            }
            if (response != null && response!!.matchedFaces != null && response!!.matchedFaces.size > 0) {
                val pair = response!!.matchedFaces[0]
                comparisonTv!!.text = "Value: " + (pair.similarity * 100).toInt() + "%"
                for (img in request!!.images) {
                    if (img.id == MainActivity.CAPTURED_FACE) {
                        realIv!!.setImageBitmap(img.image())
                    } else if (img.id == MainActivity.DOCUMENT_FACE) {
                        etalonIv!!.setImageBitmap(img.image())
                    }
                }
            } else {
                authResults!!.visibility = View.INVISIBLE
            }
            val portrait =
                results.getGraphicFieldImageByType(eGraphicFieldType.GF_PORTRAIT)
            if (portrait != null) {
                portraitIv!!.setImageBitmap(portrait)
            }
            var documentImage =
                results.getGraphicFieldImageByType(eGraphicFieldType.GF_DOCUMENT_IMAGE)
            if (documentImage != null) {
                val aspectRatio =
                    documentImage.width.toDouble() / documentImage.height.toDouble()
                documentImage =
                    Bitmap.createScaledBitmap(
                        documentImage,
                        (480 * aspectRatio).toInt(),
                        480,
                        false
                    )
                docImageIv!!.setImageBitmap(documentImage)
            }
        }
    }

    companion object {
        private var instance: FragmentResults? = null
        fun getInstance(results: DocumentReaderResults?): FragmentResults? {
            return getInstance(results, null, null)
        }

        fun getInstance(
            results: DocumentReaderResults?,
            request: MatchFacesRequest?,
            response: MatchFacesResponse?
        ): FragmentResults? {
            if (instance == null) {
                instance = FragmentResults()
            }
            instance!!.results = results
            instance!!.request = request
            instance!!.response = response
            return instance
        }
    }
}