package com.tracker.firrael.tracker;

import android.os.Bundle;
import android.os.Handler;

import com.tracker.firrael.tracker.base.SimpleFragment;

public class SplashFragment extends SimpleFragment {

    public static SplashFragment newInstance() {

        Bundle args = new Bundle();

        SplashFragment fragment = new SplashFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startLoading();
        getMainActivity().transparentStatusBar();
        getMainActivity().hideToolbar();

        Utils.verifyCameraPermission(getActivity());
        Utils.verifyStoragePermissions(getActivity());

        if (savedInstanceState == null) {
            startLoading();

            Handler handler = new Handler();
            handler.postDelayed(() -> {
                stopLoading();
                getMainActivity().toStart();
            }, 3500);
        }
    }

    @Override
    protected String getTitle() {
        return getString(R.string.app_name);
    }

    @Override
    protected int getViewId() {
        return R.layout.fragment_splash;
    }
}