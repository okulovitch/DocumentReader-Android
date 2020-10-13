package com.regula.documentreader;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.FrameLayout;

import com.regula.common.CameraCallbacks;
import com.regula.common.CameraFragment;
import com.regula.common.RegCameraFragment;
import com.regula.common.enums.CommonKeys;

/**
 * Created by Sergey Yakimchik on 10/13/20.
 * Copyright (c) 2020 Regula. All rights reserved.
 */

public class CustomRegActivity extends AppCompatActivity implements CameraCallbacks {

    private static final String FRAGMENT_TAG = "cameraFragmentTag";

    private FrameLayout mCameraUi;

    private RegCameraFragment cameraFragment;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_custom_reg);

        mCameraUi = findViewById(com.regula.documentreader.api.R.id.cameraUi);

        FragmentManager fragmentManager = getSupportFragmentManager();
        cameraFragment = (RegCameraFragment) fragmentManager.findFragmentByTag(FRAGMENT_TAG);
        if (cameraFragment == null) {
            cameraFragment = new CameraFragment();

            Bundle args = new Bundle();
            args.putInt(CommonKeys.CAMERA_ID, 0);
            cameraFragment.setArguments(args);
            fragmentManager.beginTransaction().add(com.regula.documentreader.api.R.id.cameraUi, cameraFragment, FRAGMENT_TAG).commit();
        }
    }

    public void onClickClosed(View view) {
        finish();
    }

    @Override
    public void onCameraOpened(boolean b) {

    }

    @Override
    public void onFrame(byte[] bytes) {

    }

    @Override
    public boolean isFocusMoving() {
        return false;
    }
}
