package com.regula.documentreader;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.regula.documentreader.api.enums.eGraphicFieldType;
import com.regula.documentreader.api.enums.eRPRM_Authenticity;
import com.regula.documentreader.api.enums.eVisualFieldType;
import com.regula.documentreader.api.results.DocumentReaderResults;
import com.regula.documentreader.api.results.DocumentReaderTextField;
import com.regula.documentreader.api.results.authenticity.DocumentReaderAuthenticityCheckResult;
import com.regula.documentreader.api.results.authenticity.DocumentReaderIdentResult;

public class FragmentResults extends Fragment {

    private TextView nameTv;
    private TextView comparisonTv;

    private ImageView portraitIv;
    private ImageView docImageIv;
    private ImageView etalonIv;
    private ImageView realIv;

    private LinearLayout authResults;

    private DocumentReaderResults results;

    private static FragmentResults instance;

    static FragmentResults getInstance(DocumentReaderResults results){
        if(instance==null){
            instance = new FragmentResults();
        }
        instance.results = results;

        return instance;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_results,container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        nameTv = view.findViewById(R.id.nameTv);
        comparisonTv = view.findViewById(R.id.comparisonValue);

        portraitIv = view.findViewById(R.id.portraitIv);
        docImageIv = view.findViewById(R.id.documentImageIv);
        etalonIv = view.findViewById(R.id.etalonImage);
        realIv = view.findViewById(R.id.realImage);

        authResults = view.findViewById(R.id.authHolder);

        displayResults(this.results);
    }

    private void clearResults(){
        nameTv.setText("");
        portraitIv.setImageResource(R.drawable.portrait);
        docImageIv.setImageResource(R.drawable.id);
    }

    //show received results on the UI
    private void displayResults(DocumentReaderResults results){
        clearResults();

        if(results!=null) {
            String name = results.getTextFieldValueByType(eVisualFieldType.FT_SURNAME_AND_GIVEN_NAMES);
            if (name != null){
                nameTv.setText(name);
            }

            // through all text fields
            if(results.textResult != null && results.textResult.fields != null) {
                for (DocumentReaderTextField textField : results.textResult.fields) {
                    String value = results.getTextFieldValueByType(textField.fieldType, textField.lcid);
                    Log.d("MainActivity", value + "\n");
                }
            }

            if(results.authenticityCheckList!=null){
                authResults.setVisibility(View.VISIBLE);
                for (int i=0; i<results.authenticityCheckList.list.size(); i++) {
                    DocumentReaderAuthenticityCheckResult documentReaderAuthenticityCheckResult = results.authenticityCheckList.list.get(i);
                    if(documentReaderAuthenticityCheckResult.type == eRPRM_Authenticity.PORTRAIT_COMPARISON) {
                        Log.d("PORTRAIT_COMPARISON", "Overall result: " + documentReaderAuthenticityCheckResult.result);
                        Log.d("PORTRAIT_COMPARISON", "Comparison pairs: " + documentReaderAuthenticityCheckResult.list.size());
                        for( int k=0; k<documentReaderAuthenticityCheckResult.list.size(); k++){
                            DocumentReaderIdentResult authenticityResult =
                                    (DocumentReaderIdentResult) documentReaderAuthenticityCheckResult.list.get(k);
                            Log.d("PORTRAIT_COMPARISON", "Pair "+k+ " element type: " + authenticityResult.elementType);
                            Log.d("PORTRAIT_COMPARISON", "Pair "+k+ " comparison value %: " + authenticityResult.percentValue);
                            Log.d("PORTRAIT_COMPARISON", "Pair "+k+ " element result: " + authenticityResult.elementResult);

                            comparisonTv.setText("Value: " +authenticityResult.percentValue + "%" );

                            etalonIv.setImageBitmap(authenticityResult.etalonImage.getBitmap());
                            realIv.setImageBitmap(authenticityResult.image.getBitmap());
                        }
                    }
                }
            } else {
                authResults.setVisibility(View.INVISIBLE);
            }

            Bitmap portrait = results.getGraphicFieldImageByType(eGraphicFieldType.GF_PORTRAIT);
            if(portrait!=null){
                portraitIv.setImageBitmap(portrait);
            }

            Bitmap documentImage = results.getGraphicFieldImageByType(eGraphicFieldType.GT_DOCUMENT_FRONT);
            if(documentImage!=null){
                double aspectRatio = (double) documentImage.getWidth() / (double) documentImage.getHeight();
                documentImage = Bitmap.createScaledBitmap(documentImage, (int)(480 * aspectRatio), 480, false);
                docImageIv.setImageBitmap(documentImage);
            }
        }
    }
}
