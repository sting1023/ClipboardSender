package com.sting.clipboardsender;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity {

    private TextView statusText;
    private Button testButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        testButton = findViewById(R.id.testButton);

        updateStatus();

        testButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Test: send a test message to self
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                                new java.net.URL("http://127.0.0.1:8888/send").openConnection();
                            conn.setRequestMethod("POST");
                            conn.setDoOutput(true);
                            conn.getOutputStream().write("Hello from ClipboardSender!".getBytes());
                            conn.getResponseCode();
                            conn.disconnect();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
            }
        });
    }

    private void updateStatus() {
        boolean serviceRunning = ClipboardService.isRunning();
        statusText.setText("服务状态: " + (serviceRunning ? "运行中 ✓" : "未运行 ✗") +
                "\nHTTP端口: 8888" +
                "\n监听路径: POST /send");
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }
}
