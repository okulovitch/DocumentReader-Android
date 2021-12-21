package com.regula.documentreader;

import static com.regula.documentreader.BaseActivity.DO_RFID;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.regula.documentreader.api.DocumentReader;
import com.regula.documentreader.api.enums.eGraphicFieldType;
import com.regula.documentreader.api.enums.eVisualFieldType;
import com.regula.documentreader.api.results.DocumentReaderResults;
import com.regula.documentreader.api.results.DocumentReaderTextField;

public class MainFragment extends Fragment {

    private TextView nameTv;
    private TextView showScanner;
    private TextView recognizeImage;
    private TextView showCameraActivity;

    private ImageView portraitIv;
    private ImageView docImageIv;

    private CheckBox doRfidCb;

    private ListView scenarioLv;

    private volatile MainCallbacks mCallbacks;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_main, container, false);

        nameTv = root.findViewById(R.id.nameTv);
        showScanner = root.findViewById(R.id.showScannerLink);
        recognizeImage = root.findViewById(R.id.recognizeImageLink);
        showCameraActivity = root.findViewById(R.id.showCameraActivity);

        portraitIv = root.findViewById(R.id.portraitIv);
        docImageIv = root.findViewById(R.id.documentImageIv);

        scenarioLv = root.findViewById(R.id.scenariosList);

        doRfidCb = root.findViewById(R.id.doRfidCb);
        initView();
        return root;
    }


    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mCallbacks = (MainCallbacks) getActivity();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mCallbacks = null;
    }


    private void initView() {
        recognizeImage.setOnClickListener(view -> {
            if (!DocumentReader.Instance().getDocumentReaderIsReady())
                return;

            clearResults();
            mCallbacks.recognizeImage();
            //checking for image browsing permissions
        });

        showScanner.setOnClickListener(view -> {

            clearResults();
            mCallbacks.showScanner();
        });

        scenarioLv.setOnItemClickListener((adapterView, view, i, l) -> {

            ScenarioAdapter adapter = (ScenarioAdapter) adapterView.getAdapter();
            mCallbacks.scenarioLv(adapter.getItem(i));

            adapter.setSelectedPosition(i);
            adapter.notifyDataSetChanged();
        });

        showCameraActivity.setOnClickListener(view -> {
            mCallbacks.showCameraActivity();
        });
    }

    public void displayResults(DocumentReaderResults results) {
        if (results != null) {
            String name = results.getTextFieldValueByType(eVisualFieldType.FT_SURNAME_AND_GIVEN_NAMES);
            if (name != null) {
                nameTv.setText(name);
            }

            // through all text fields
            if (results.textResult != null) {
                for (DocumentReaderTextField textField : results.textResult.fields) {
                    String value = results.getTextFieldValueByType(textField.fieldType, textField.lcid);
                }
            }

            Bitmap portrait = results.getGraphicFieldImageByType(eGraphicFieldType.GF_PORTRAIT);
            if (portrait != null) {
                portraitIv.setImageBitmap(portrait);
            }

            Bitmap documentImage = results.getGraphicFieldImageByType(eGraphicFieldType.GF_DOCUMENT_IMAGE);
            if (documentImage != null) {
                double aspectRatio = (double) documentImage.getWidth() / (double) documentImage.getHeight();
                documentImage = Bitmap.createScaledBitmap(documentImage, (int) (480 * aspectRatio), 480, false);
                docImageIv.setImageBitmap(documentImage);
            }
        }
    }

    private void clearResults() {
        nameTv.setText("");
        portraitIv.setImageResource(R.drawable.portrait);
        docImageIv.setImageResource(R.drawable.id);
    }

    public void setAdapter(ScenarioAdapter adapter) {
        scenarioLv.setAdapter(adapter);
    }

    public void setDoRfid(boolean rfidAvailable, SharedPreferences sharedPreferences) {
        if (rfidAvailable) {
            boolean doRfid = sharedPreferences.getBoolean(DO_RFID, false);

            doRfidCb.setChecked(doRfid);
            doRfidCb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                    sharedPreferences.edit().putBoolean(DO_RFID, checked).apply();
                    mCallbacks.setDoRFID(checked);
                }
            });
        } else {
            doRfidCb.setVisibility(View.GONE);
        }
    }

    interface MainCallbacks {

        void showCameraActivity();

        void scenarioLv(String item);

        void showScanner();

        void recognizeImage();

        void setDoRFID(boolean checked);
    }


}