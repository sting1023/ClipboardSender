package com.sting.clipboardsender;

import android.app.Application;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class App extends Application {

    private static final String TAG = "ClipboardSender";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "App started, launching ClipboardService");

        Intent serviceIntent = new Intent(this, ClipboardService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }
}
