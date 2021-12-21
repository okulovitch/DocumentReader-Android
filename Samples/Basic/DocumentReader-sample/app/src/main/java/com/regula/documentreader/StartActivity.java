package com.regula.documentreader;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.regula.documentreader.api.DocumentReader;

public class StartActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);
        findViewById(R.id.btn_default).setOnClickListener(view -> {
                    if (DocumentReader.Instance().isReady()) {
                        DocumentReader.Instance().deinitializeReader();
                    }
                    startActivity(new Intent(StartActivity.this, MainActivity.class));
                }
        );
        findViewById(R.id.btn_7310).setOnClickListener(view -> {
            if (DocumentReader.Instance().isReady()) {
                DocumentReader.Instance().deinitializeReader();
            }
            startActivity(new Intent(StartActivity.this, DeviceActivity.class));
        });
    }
}