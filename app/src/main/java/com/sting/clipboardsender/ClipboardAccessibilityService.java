package com.sting.clipboardsender;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ClipboardAccessibilityService extends AccessibilityService {

    private static final String TAG = "ClipboardAccessService";
    private static final String WECHAT_PACKAGE = "com.tencent.mm";
    private static final String EDIT_TEXT_ID = "com.tencent.mm:id/bkk";
    private static final String SEND_BTN_ID = "com.tencent.mm:id/bql";
    private static final String PENDING_FILE = "pending_text.txt";

    private static ClipboardAccessibilityService instance;
    private static volatile boolean pastePending = false;

    public static ClipboardAccessibilityService getInstance() {
        return instance;
    }

    public static void triggerPaste() {
        pastePending = true;
        Log.d(TAG, "Paste triggered");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Log.d(TAG, "AccessibilityService created");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        Log.d(TAG, "AccessibilityService destroyed");
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED |
                          AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS |
                     AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        info.notificationTimeout = 100;
        setServiceInfo(info);
        Log.d(TAG, "AccessibilityService connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;

        String packageName = event.getPackageName().toString();
        if (!WECHAT_PACKAGE.equals(packageName)) return;

        if (pastePending) {
            pastePending = false;
            performPaste();
        }
    }

    private void performPaste() {
        Log.d(TAG, "performPaste called");
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    // Read text from file
                    String text = readPendingText();
                    if (text == null || text.isEmpty()) {
                        Log.w(TAG, "No pending text to send");
                        return;
                    }
                    
                    // Tap the input field to activate it
                    Runtime.getRuntime().exec("/system/bin/input tap 324 1352").waitFor();
                    Thread.sleep(200);
                    Log.d(TAG, "Tapped input field");
                    
                    // Type the text character by character (handles Chinese)
                    for (char c : text.toCharArray()) {
                        String key = String.valueOf(c);
                        // Escape spaces and special chars for shell
                        key = key.replace(" ", "<space>");
                        Runtime.getRuntime().exec("/system/bin/input text " + key).waitFor();
                        Thread.sleep(30);
                    }
                    Thread.sleep(200);
                    
                    // Click send button
                    Runtime.getRuntime().exec("/system/bin/input tap 605 1356").waitFor();
                    Log.d(TAG, "Send completed");
                } catch (Exception e) {
                    Log.e(TAG, "performPaste error: " + e.getMessage());
                }
            }
        });
    }
    
    private String readPendingText() {
        try {
            File file = new File(getFilesDir(), PENDING_FILE);
            if (!file.exists()) {
                Log.w(TAG, "Pending file does not exist");
                return null;
            }
            FileInputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();
            return new String(data, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "Error reading pending text", e);
            return null;
        }
    }

    private void clickSendButton() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        List<AccessibilityNodeInfo> sendButtons = rootNode.findAccessibilityNodeInfosByViewId(SEND_BTN_ID);
        if (sendButtons != null && !sendButtons.isEmpty()) {
            AccessibilityNodeInfo sendBtn = sendButtons.get(0);
            Log.d(TAG, "Clicking send button");
            sendBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            sendBtn.recycle();
        } else {
            Log.w(TAG, "Send button not found");
        }
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "AccessibilityService interrupted");
    }
}
