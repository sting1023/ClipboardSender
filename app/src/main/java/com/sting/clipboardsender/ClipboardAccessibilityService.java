package com.sting.clipboardsender;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
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
    private static final String TAG = "ClipAccessService";
    private static final String WECHAT_PACKAGE = "com.tencent.mm";
    private static final String PENDING_FILE = "pending_text.txt";
    private static final String EDIT_TEXT_ID = "com.tencent.mm:id/bkk";
    private static final String SEND_BTN_ID = "com.tencent.mm:id/bqr";
    private static final String EDIT_TEXT_CLASS = "android.widget.EditText";

    private Handler handler;
    private String lastText = null;
    private static ClipboardAccessibilityService instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        handler = new Handler(Looper.getMainLooper());
        startPolling();
        Log.d(TAG, "Service created, polling started");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        Log.d(TAG, "Service destroyed");
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        info.notificationTimeout = 100;
        setServiceInfo(info);
        Log.d(TAG, "Service connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        if (!WECHAT_PACKAGE.equals(event.getPackageName().toString())) return;
        Log.d(TAG, "WeChat event: " + event.getEventType());
        checkAndInject();
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "Service interrupted");
    }

    private void startPolling() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                checkAndInject();
                handler.postDelayed(this, 800);
            }
        }, 800);
    }

    private void checkAndInject() {
        try {
            File file = new File(getFilesDir(), PENDING_FILE);
            if (!file.exists() || !file.canRead()) return;
            FileInputStream fis = new FileInputStream(file);
            byte[] buf = new byte[4096];
            int len = fis.read(buf);
            fis.close();
            if (len <= 0) return;
            String text = new String(buf, 0, len, StandardCharsets.UTF_8).trim();
            if (text.isEmpty() || text.equals(lastText)) return;
            lastText = text;
            file.delete();
            Log.d(TAG, "Got pending text: " + text);
            injectText(text);
        } catch (Exception e) {
            Log.e(TAG, "checkAndInject error: " + e.getMessage());
        }
    }

    private void injectText(String text) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                performInject(text);
            }
        }, 500);
    }

    private void performInject(String text) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) {
            Log.w(TAG, "Root node null");
            return;
        }
        Log.d(TAG, "Root class: " + rootNode.getClassName());

        AccessibilityNodeInfo editText = findNodeById(rootNode, EDIT_TEXT_ID);
        if (editText == null) {
            editText = findNodeByClass(rootNode, EDIT_TEXT_CLASS);
        }

        if (editText == null) {
            Log.w(TAG, "EditText not found in window");
            rootNode.recycle();
            return;
        }

        Log.d(TAG, "Found EditText, isFocusable=" + editText.isFocusable() + " isEditable=" + editText.isEditable());

        if (editText.isFocusable()) {
            editText.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            try { Thread.sleep(100); } catch (Exception e) {}
        }

        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        boolean success = editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        Log.d(TAG, "ACTION_SET_TEXT result: " + success);
        editText.recycle();

        if (success) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    clickSendButton();
                }
            }, 400);
        }
    }

    private AccessibilityNodeInfo findNodeById(AccessibilityNodeInfo root, String viewId) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(viewId);
        if (nodes != null && !nodes.isEmpty()) return nodes.get(0);
        return null;
    }

    private AccessibilityNodeInfo findNodeByClass(AccessibilityNodeInfo node, String className) {
        if (node == null) return null;
        if (className.equals(node.getClassName() != null ? node.getClassName().toString() : "")) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            AccessibilityNodeInfo found = findNodeByClass(child, className);
            if (found != null) return found;
        }
        return null;
    }

    private void clickSendButton() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        AccessibilityNodeInfo sendBtn = findNodeById(rootNode, SEND_BTN_ID);
        if (sendBtn != null && sendBtn.isClickable()) {
            Log.d(TAG, "Clicking send by ID");
            sendBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            sendBtn.recycle();
        } else {
            clickByText(rootNode, "发送");
        }
        rootNode.recycle();
    }

    private void clickByText(AccessibilityNodeInfo node, String text) {
        if (node == null) return;
        CharSequence nodeText = node.getText();
        if (text.equals(nodeText != null ? nodeText.toString() : "")) {
            if (node.isClickable()) {
                Log.d(TAG, "Clicking by text: " + text);
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                return;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) clickByText(child, text);
        }
    }

    public static void triggerPaste() {
        Log.d(TAG, "triggerPaste called");
        if (instance != null) {
            instance.checkAndInject();
        } else {
            Log.w(TAG, "Service instance not available");
        }
    }
}
