package com.sting.clipboardsender;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.AccessibilityServiceInfoCompat;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private TextView statusText;
    private Button testButton;
    private Button btnAccessibility;
    private Button btnAppSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        testButton = findViewById(R.id.testButton);
        btnAccessibility = findViewById(R.id.btnAccessibility);
        btnAppSettings = findViewById(R.id.btnAppSettings);

        updateStatus();

        btnAccessibility.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    Toast.makeText(MainActivity.this, "请找到 ClipboardSender 并开启", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "无法打开无障碍设置", Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnAppSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "无法打开设置", Toast.LENGTH_SHORT).show();
                }
            }
        });

        testButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                                new java.net.URL("http://127.0.0.1:8888/send").openConnection();
                            conn.setRequestMethod("POST");
                            conn.setDoOutput(true);
                            conn.getOutputStream().write("测试消息 from ClipboardSender".getBytes("UTF-8"));
                            final int code = conn.getResponseCode();
                            conn.disconnect();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(MainActivity.this, "发送完成! HTTP " + code, Toast.LENGTH_SHORT).show();
                                }
                            });
                        } catch (Exception e) {
                            final String err = e.getMessage();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(MainActivity.this, "发送失败: " + err, Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    }
                }).start();
            }
        });
    }

    private boolean isAccessibilityEnabled() {
        try {
            ComponentName name = new ComponentName(this, ClipboardAccessibilityService.class);
            PackageManager pm = getPackageManager();
            AccessibilityServiceInfo info = pm.getAccessibilityServiceInfo(name, PackageManager.GET_META_DATA);
            return info != null && info.getId() != null;
        } catch (Exception e) {
            return false;
        }
    }

    private void updateStatus() {
        boolean accEnabled = isAccessibilityEnabled();
        String status = "无障碍服务: " + (accEnabled ? "✅ 已开启" : "❌ 未开启") +
                "\n服务端口: 8888" +
                "\n监听路径: POST /send";
        statusText.setText(status);
        btnAccessibility.setAlpha(accEnabled ? 0.5f : 1.0f);
        btnAccessibility.setEnabled(!accEnabled);
        if (accEnabled) {
            btnAccessibility.setText("🔧 无障碍服务已开启");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }
}
