package com.sting.clipboardsender;

import android.app.Notification;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

public class ClipboardService extends Service {

    private static final String TAG = "ClipboardService";
    private static final int PORT = 8888;
    private static final String CHANNEL_ID = "clipboard_sender_channel";

    private static volatile boolean isRunning = false;
    private static final String PENDING_FILE = "pending_text.txt";
    private HttpServer httpServer;

    public static boolean isRunning() {
        return isRunning;
    }

    public static void setClipboardText(Context context, String text) {
        try {
            File file = new File(context.getFilesDir(), PENDING_FILE);
            FileOutputStream fos = context.openFileOutput(PENDING_FILE, Context.MODE_PRIVATE);
            fos.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            fos.close();
            Log.d(TAG, "Pending text written to file: " + text);
        } catch (Exception e) {
            Log.e(TAG, "Error writing to file", e);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (isRunning) {
            Log.d(TAG, "Service already running");
            return START_STICKY;
        }

        createNotificationChannel();
        startForeground(1, createNotification());



        // Start HTTP server in background thread
        httpServer = new HttpServer(PORT, this);
        new Thread(httpServer).start();

        isRunning = true;
        Log.d(TAG, "Service started, HTTP server listening on port " + PORT);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (httpServer != null) {
            httpServer.stop();
        }
        Log.d(TAG, "Service destroyed");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("ClipboardSender后台服务");
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, flags);

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(android.R.drawable.ic_menu_share)
                .setContentIntent(pendingIntent)
                .setOngoing(true);

        return builder.build();
    }
}
