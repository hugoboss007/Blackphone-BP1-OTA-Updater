package com.gp.updater;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

public class UpdateApplication extends Application implements
        Application.ActivityLifecycleCallbacks {

    private boolean mMainActivityActive;

    @Override
    public void onCreate() {
        mMainActivityActive = false;
        registerActivityLifecycleCallbacks(this);
    }

    @Override
    public void onActivityCreated (Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityDestroyed (Activity activity) {
    }

    @Override
    public void onActivityPaused (Activity activity) {
    }

    @Override
    public void onActivityResumed (Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState (Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityStarted (Activity activity) {
        if (activity instanceof OTA) {
            mMainActivityActive = true;
        }
    }

    @Override
    public void onActivityStopped (Activity activity) {
        if (activity instanceof OTA) {
            mMainActivityActive = false;
        }
    }

    public boolean isMainActivityActive() {
        return mMainActivityActive;
    }
}
