package com.example;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.IBinder;

/**
 * AirLink Screen Sharing Foreground Service
 * Manages the MediaProjection display stream with a persistent, non-dismissible notification.
 * Provides transparent security indicators required by Android OS and Google Play policies.
 */
public class ScreenSharingService extends Service {

    public static final String ACTION_START = "com.example.airlink.ACTION_START_STREAM";
    public static final String ACTION_STOP = "com.example.airlink.ACTION_STOP_STREAM";
    public static final String CHANNEL_ID = "airlink_screen_stream_channel";
    public static final int NOTIFICATION_ID = 2026;

    private static boolean isRunning = false;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;

    public static boolean isServiceRunning() {
        return isRunning;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopForegroundStream();
            return START_NOT_STICKY;
        }

        startForegroundStream();
        return START_STICKY;
    }

    private void startForegroundStream() {
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        isRunning = true;
    }

    private void stopForegroundStream() {
        isRunning = false;
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        stopForeground(true);
        stopSelf();
    }

    private Notification buildNotification() {
        Intent stopIntent = new Intent(this, ScreenSharingService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent activityIntent = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 0, activityIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        return builder
                .setContentTitle("AirLink Screen Sharing Active")
                .setContentText("Broadcasting display to paired device over local Wi-Fi")
                .setSmallIcon(android.R.drawable.ic_menu_slideshow)
                .setContentIntent(contentIntent)
                .addAction(android.R.drawable.ic_delete, "Stop Sharing", stopPendingIntent)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "AirLink Screen Stream",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows persistent status when screen mirroring is active");
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        stopForegroundStream();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
