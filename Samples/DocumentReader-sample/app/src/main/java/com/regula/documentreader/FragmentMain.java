package com.regula.documentreader;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.TextView;

import com.regula.documentreader.api.DocumentReader;
import com.regula.documentreader.api.completions.IDocumentReaderCompletion;
import com.regula.documentreader.api.completions.IDocumentReaderInitCompletion;
import com.regula.documentreader.api.completions.IDocumentReaderPrepareCompletion;
import com.regula.documentreader.api.results.DocumentReaderScenario;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static com.regula.documentreader.MainActivity.DO_RFID;
import static com.regula.documentreader.MainActivity.PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE;
import static com.regula.documentreader.MainActivity.REQUEST_BROWSE_PICTURE;
import static com.regula.documentreader.MainActivity.sharedPreferences;

public class FragmentMain extends Fragment {

    private int selectedPosition;

    private boolean doRfid;

    private TextView showScanner;
    private TextView recognizeImage;

    private CheckBox doRfidCb;

    private ListView scenarioLv;

    private Activity activity;

    private IDocumentReaderCompletion completion;

    private static FragmentMain instance;

    static FragmentMain getInstance(IDocumentReaderCompletion completion){
        if(instance==null){
            instance = new FragmentMain();
        }
        instance.completion = completion;
        return instance;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        activity = (Activity)context;
    }

    @Override
    public void onDetach() {
        super.onDetach();

        activity = null;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main,container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        showScanner = view.findViewById(R.id.showScannerLink);
        recognizeImage = view.findViewById(R.id.recognizeImageLink);

        scenarioLv = view.findViewById(R.id.scenariosList);

        doRfidCb = view.findViewById(R.id.doRfidCb);
    }

    @Override
    public void onResume() {
        super.onResume();

        if(DocumentReader.Instance().getDocumentReaderIsReady()){
            successfulInit();
        }
    }

    void prepareDatabase(Context context, final IDocumentReaderPrepareCompletion completion){
        //preparing database files, it will be downloaded from network only one time and stored on user device
        DocumentReader.Instance().prepareDatabase(context, "Full", new
                IDocumentReaderPrepareCompletion() {

                    @Override
                    public void onPrepareProgressChanged(int i) {
                        completion.onPrepareProgressChanged(i);
                    }

                    @Override
                    public void onPrepareCompleted(boolean b, String s) {
                        completion.onPrepareCompleted(b, s);
                    }
                });
    }

    void init(final Context context,
                 final IDocumentReaderInitCompletion initCompletion){
        try {
            InputStream licInput = getResources().openRawResource(R.raw.regula);
            int available = licInput.available();
            final byte[] license = new byte[available];
            //noinspection ResultOfMethodCallIgnored
            licInput.read(license);

            //Initializing the reader
            DocumentReader.Instance().initializeReader(context, license, new IDocumentReaderInitCompletion() {
                @Override
                public void onInitCompleted(boolean success, String error) {
                    DocumentReader.Instance().customization().edit()
                            .setShowResultStatusMessages(true)
                            .setShowStatusMessages(true);
                    DocumentReader.Instance().functionality().edit()
                            .setVideoCaptureMotionControl(true).apply();

                    //initialization successful
                    if (success) {
                        successfulInit();
                    }

                    initCompletion.onInitCompleted(success, error);
                }
            });

            licInput.close();
        } catch (Exception ex){
            ex.printStackTrace();
        }
    }

    private void successfulInit() {
        showScanner.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //starting video processing
                DocumentReader.Instance().showScanner(getContext(), completion);
            }
        });

        recognizeImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //checking for image browsing permissions
                if (ContextCompat.checkSelfPermission(activity,
                        Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {

                    ActivityCompat.requestPermissions(activity,
                            new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                            PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
                } else {
                    //start image browsing
                    createImageBrowsingRequest();
                }
            }
        });

        if (DocumentReader.Instance().isRFIDAvailableForUse()) {
            //reading shared preferences
            doRfid = sharedPreferences.getBoolean(DO_RFID, false);
            doRfidCb.setChecked(doRfid);
            doRfidCb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                    doRfid = checked;
                    sharedPreferences.edit().putBoolean(DO_RFID, checked).apply();
                }
            });
        } else {
            doRfidCb.setVisibility(View.GONE);
        }

        //getting current processing scenario and loading available scenarios to ListView
        String currentScenario = DocumentReader.Instance().processParams().scenario;
        ArrayList<String> scenarios = new ArrayList<>();
        for (DocumentReaderScenario scenario : DocumentReader.Instance().availableScenarios) {
            scenarios.add(scenario.name);
        }

        //setting default scenario
        if (currentScenario == null || currentScenario.isEmpty()) {
            currentScenario = scenarios.get(0);
            DocumentReader.Instance().processParams().scenario = currentScenario;
        }

        final ScenarioAdapter adapter =
                new ScenarioAdapter(activity, android.R.layout.simple_list_item_1, scenarios);
        selectedPosition = 0;
        try {
            selectedPosition = adapter.getPosition(currentScenario);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        scenarioLv.setAdapter(adapter);

        scenarioLv.setSelection(selectedPosition);

        scenarioLv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                //setting selected scenario to DocumentReader params
                DocumentReader.Instance().processParams().scenario = adapter.getItem(i);
                selectedPosition = i;
                adapter.notifyDataSetChanged();

            }
        });
    }

    // creates and starts image browsing intent
    // results will be handled in onActivityResult method
    void createImageBrowsingRequest() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), REQUEST_BROWSE_PICTURE);
    }

    class ScenarioAdapter extends ArrayAdapter<String> {

        public ScenarioAdapter(@NonNull Context context, int resource, @NonNull List<String> objects) {
            super(context, resource, objects);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View view = super.getView(position, convertView, parent);

            if(position == selectedPosition){
                view.setBackgroundColor(Color.LTGRAY);
            } else {
                view.setBackgroundColor(Color.TRANSPARENT);
            }
            return view;
        }
    }
}
