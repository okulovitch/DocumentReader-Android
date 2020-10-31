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
import com.regula.documentreader.api.enums.eVisualFieldType;
import com.regula.documentreader.api.results.DocumentReaderResults;
import com.regula.documentreader.api.results.DocumentReaderTextField;
import com.regula.facesdk.results.MatchFacesResponse;
import com.regula.facesdk.results.MatchedFacesPair;
import com.regula.facesdk.structs.Image;
import com.regula.facesdk.structs.MatchFacesRequest;

public class FragmentResults extends Fragment {

    private TextView nameTv;
    private TextView comparisonTv;

    private ImageView portraitIv;
    private ImageView docImageIv;
    private ImageView etalonIv;
    private ImageView realIv;

    private LinearLayout authResults;

    private DocumentReaderResults results;
    private MatchFacesRequest request;
    private MatchFacesResponse response;

    private static FragmentResults instance;

    static FragmentResults getInstance(DocumentReaderResults results){
        return getInstance(results, null, null);
    }

    static FragmentResults getInstance(DocumentReaderResults results, MatchFacesRequest request, MatchFacesResponse response){
        if(instance==null){
            instance = new FragmentResults();
        }
        instance.results = results;
        instance.request = request;
        instance.response = response;

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

        if(results != null) {
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

            Bitmap portrait = results.getGraphicFieldImageByType(eGraphicFieldType.GF_PORTRAIT);
            if(portrait!=null){
                portraitIv.setImageBitmap(portrait);
            }

            Bitmap documentImage = results.getGraphicFieldImageByType(eGraphicFieldType.GF_DOCUMENT_IMAGE);
            if(documentImage!=null){
                double aspectRatio = (double) documentImage.getWidth() / (double) documentImage.getHeight();
                documentImage = Bitmap.createScaledBitmap(documentImage, (int)(480 * aspectRatio), 480, false);
                docImageIv.setImageBitmap(documentImage);
            }
        }

        if(response != null && response.matchedFaces != null && response.matchedFaces.size() > 0){
            MatchedFacesPair pair = response.matchedFaces.get(0);
            comparisonTv.setText("Value: " + (int)(pair.similarity * 100) + "%" );

            for(Image img : request.images){
                if(img.id == MainActivity.CAPTURED_FACE){
                    realIv.setImageBitmap(img.image());
                } else if(img.id == MainActivity.LIVENESS_FACE){
                    etalonIv.setImageBitmap(img.image());
                }
            }

        } else {
            authResults.setVisibility(View.INVISIBLE);
        }
    }
}
