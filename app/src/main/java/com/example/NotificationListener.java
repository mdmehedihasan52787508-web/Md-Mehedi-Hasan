package com.example;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AirLink Notification Listener Service
 * Intercepts notifications when user explicitly enables access in Android Settings.
 * Safely sanitizes content and makes alerts available to authenticated paired devices.
 */
public class NotificationListener extends NotificationListenerService {

    public static class CapturedNotification {
        public final String packageName;
        public final String title;
        public final String text;
        public final long timestamp;

        public CapturedNotification(String packageName, String title, String text, long timestamp) {
            this.packageName = packageName;
            this.title = title;
            this.text = text;
            this.timestamp = timestamp;
        }

        public JSONObject toJson() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("app", packageName);
                obj.put("title", title);
                obj.put("body", text);
                obj.put("time", "Just now");
                return obj;
            } catch (Exception e) {
                return new JSONObject();
            }
        }
    }

    private static final List<CapturedNotification> capturedList = Collections.synchronizedList(new ArrayList<>());
    private static boolean isConnected = false;

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        isConnected = true;
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        isConnected = false;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        Notification notification = sbn.getNotification();
        if (notification == null) return;

        Bundle extras = notification.extras;
        if (extras == null) return;

        String packageName = sbn.getPackageName();
        if (getPackageName().equals(packageName)) {
            // Ignore own notifications
            return;
        }

        CharSequence titleChar = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence textChar = extras.getCharSequence(Notification.EXTRA_TEXT);

        String title = titleChar != null ? titleChar.toString() : "Notification";
        String text = textChar != null ? textChar.toString() : "";

        // Privacy filter: skip empty or password-related sensitive alerts
        if (title.toLowerCase().contains("otp") || title.toLowerCase().contains("passcode")) {
            text = "Sensitive notification hidden for privacy";
        }

        CapturedNotification captured = new CapturedNotification(
                packageName, title, text, System.currentTimeMillis());

        capturedList.add(0, captured);
        // Retain max 50 recent items
        while (capturedList.size() > 50) {
            capturedList.remove(capturedList.size() - 1);
        }
    }

    public static boolean isNotificationAccessEnabled(Context context) {
        String pkgName = context.getPackageName();
        final String flat = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
        if (!TextUtils.isEmpty(flat)) {
            final String[] names = flat.split(":");
            for (String name : names) {
                final ComponentName cn = ComponentName.unflattenFromString(name);
                if (cn != null && TextUtils.equals(pkgName, cn.getPackageName())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static String getNotificationsJson() {
        JSONArray arr = new JSONArray();
        synchronized (capturedList) {
            for (CapturedNotification n : capturedList) {
                arr.put(n.toJson());
            }
        }
        return arr.toString();
    }

    public static void clearAll() {
        capturedList.clear();
    }
}
