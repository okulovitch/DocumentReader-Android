package com.regula.documentreader;

import android.os.Bundle;

import com.regula.documentreader.api.DocumentReader;
import com.regula.documentreader.api.params.DocReaderConfig;
import com.regula.documentreader.util.LicenseUtil;

public class MainActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    protected void initializeReader() {

        showDialog("Initializing");
        DocReaderConfig config = new DocReaderConfig(LicenseUtil.getLicense(this));
        config.setLicenseUpdate(true);

        //Initializing the reader
        DocumentReader.Instance().initializeReader(MainActivity.this, config, initCompletion);
    }

    @Override
    protected void onPrepareDbCompleted() {
        initializeReader();
    }

}
