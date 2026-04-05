package com.sting.clipboardsender;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

public class ClipboardAccessibilityService extends AccessibilityService {

    private static final String TAG = "ClipboardAccessService";
    private static final String WECHAT_PACKAGE = "com.tencent.mm";
    private static final String EDIT_TEXT_ID = "com.tencent.mm:id/bkk";
    private static final String SEND_BTN_ID = "com.tencent.mm:id/bql";

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
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    // Tap the input field to activate it
                    Runtime.getRuntime().exec("/system/bin/input tap 324 1352").waitFor();
                    Log.d(TAG, "Tapped input field");
                    
                    // Paste from clipboard
                    Runtime.getRuntime().exec("/system/bin/input keyevent 279").waitFor();
                    Log.d(TAG, "Paste executed");
                    
                    // Click send button
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Runtime.getRuntime().exec("/system/bin/input tap 605 1356").waitFor();
                                Log.d(TAG, "Send button tapped");
                            } catch (Exception e) {
                                Log.e(TAG, "Send tap failed: " + e.getMessage());
                            }
                        }
                    }, 400);
                } catch (Exception e) {
                    Log.e(TAG, "performPaste error: " + e.getMessage());
                }
            }
        }, 500);
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

    private String getClipboardText() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null && cm.hasPrimaryClip()) {
            android.content.ClipData clip = cm.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0) {
                CharSequence text = clip.getItemAt(0).getText();
                if (text != null) {
                    return text.toString();
                }
            }
        }
        return null;
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "AccessibilityService interrupted");
    }
}
