package com.regula.documentreader;

import android.content.Context;
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
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.regula.documentreader.api.enums.eCheckResult;
import com.regula.documentreader.api.enums.eGraphicFieldType;
import com.regula.documentreader.api.enums.eRPRM_Authenticity;
import com.regula.documentreader.api.enums.eVisualFieldType;
import com.regula.documentreader.api.results.DocumentReaderResults;
import com.regula.documentreader.api.results.DocumentReaderTextField;
import com.regula.documentreader.api.results.authenticity.DocumentReaderAuthenticityCheck;
import com.regula.documentreader.api.results.authenticity.DocumentReaderAuthenticityElement;
import com.regula.documentreader.api.results.authenticity.DocumentReaderIdentResult;
import com.regula.facesdk.enums.ImageType;
import com.regula.facesdk.model.Image;
import com.regula.facesdk.model.results.ComparedFacesPair;
import com.regula.facesdk.model.results.MatchFacesResponse;
import com.regula.facesdk.request.MatchFacesRequest;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.UUID;

public class FragmentResults extends Fragment {

    private TextView nameTv;
    private TextView comparisonTv;

    private ImageView portraitIv;
    private ImageView docImageIv;
    private ImageView etalonIv;
    private ImageView realIv;

    private LinearLayout authResults;

    private RelativeLayout authenticityLayout;
    private ImageView authenticityResultImg;

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
        authenticityLayout = view.findViewById(R.id.authenticityLayout);
        authenticityResultImg = view.findViewById(R.id.authenticityResultImg);

        displayResults(this.results);
    }

    private void clearResults(){
        nameTv.setText("");
        portraitIv.setImageResource(R.drawable.portrait);
        docImageIv.setImageResource(R.drawable.id);
        authenticityLayout.setVisibility(View.GONE);
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

            // face results
            if(response!=null && response.getMatchedFaces().size() > 0){
                ComparedFacesPair pair = response.getMatchedFaces().get(0);
                comparisonTv.setText("Value: " + (int)(pair.getSimilarity() * 100) + "%" );

                for(Image img : request.getImages()){
                    if(img.getImageType() == ImageType.IMAGE_TYPE_LIVE){
                        realIv.setImageBitmap(img.getBitmap());
                    } else if(img.getImageType() == ImageType.IMAGE_TYPE_PRINTED || img.getImageType() == ImageType.IMAGE_TYPE_RFID){
                        etalonIv.setImageBitmap(img.getBitmap());
                    }
                }

            } else {
                authResults.setVisibility(View.INVISIBLE);
            }

            // auth results
            if (results.authenticityResult != null) {
                authenticityLayout.setVisibility(View.VISIBLE);
                authenticityResultImg.setImageResource(results.authenticityResult.getStatus() == eCheckResult.CH_CHECK_OK ? R.drawable.correct : R.drawable.incorrect);

                for (DocumentReaderAuthenticityCheck check : results.authenticityResult.checks) {
                    Log.d("FragmentResults", "check type: " + check.getTypeName(getContext()) + ", status: " + (check.getStatus() == eCheckResult.CH_CHECK_OK ? "Ok" : "Error"));
                    for (DocumentReaderAuthenticityElement element : check.elements) {
                        Log.d("FragmentResults", "Element type: " + element.elementType + ", status: " + (element.status == eCheckResult.CH_CHECK_OK ? "Ok" : "Error"));
                        if (check.type == eRPRM_Authenticity.IMAGE_PATTERN || check.type == eRPRM_Authenticity.PORTRAIT_COMPARISON) {
                            DocumentReaderIdentResult identResult = (DocumentReaderIdentResult) element;
                            Bitmap etalonImage = identResult.etalonImage.getBitmap(); // etalon bitmap (pattern)
                            Bitmap image = identResult.image.getBitmap();  // image bitmap for compare (current image)
                            int percentage = identResult.percentValue; // percent value of comparison
                            Log.d("FragmentResults", "Percentage: " + percentage);
                        }
                    }
                }
            } else {
                authenticityLayout.setVisibility(View.GONE);
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

            // save results
            saveResults(results);
        }
    }

    private void saveResults(final DocumentReaderResults docReaderResults) {
        if(docReaderResults == null){
            return;
        }

        final Context context = getContext();
        if (context == null)
            return;

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                File scanResultsDir = new File(context.getExternalFilesDir(null), "ScanResults");
                if (!scanResultsDir.exists()) {
                    scanResultsDir.mkdir();
                }

                final File jsonFile = new File(context.getExternalFilesDir(null), "ScanResults/" + UUID.randomUUID().toString() + ".json");
                try {
                    jsonFile.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                try {
                    BufferedWriter output = new BufferedWriter(new FileWriter(jsonFile));
                    String json = docReaderResults.toJson();  // move DocumentReaderResults to json string
                    if (json != null)
                        output.write(json);
                    output.close();
                    Log.d("MainActivity", "Results have been written to file");
                } catch (IOException e) {
                    e.printStackTrace();
                }

                // load results from File
                int size = (int) jsonFile.length();
                byte[] bytes = new byte[size];
                try {
                    BufferedInputStream buf = new BufferedInputStream(new FileInputStream(jsonFile));
                    buf.read(bytes, 0, bytes.length);
                    buf.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                DocumentReaderResults docResults = DocumentReaderResults.fromJson(new String(bytes)); // results from file
                if (docResults != null) {
                    Log.d("MainActivity", "Results have been loaded from file");
                }
            }
        };

        Thread thread = new Thread(runnable);
        thread.start();
    }
}
