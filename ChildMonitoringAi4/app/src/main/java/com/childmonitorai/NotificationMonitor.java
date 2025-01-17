package com.childmonitorai;

import android.app.Notification;
import android.content.Context;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.os.Bundle;
import android.util.Log;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import android.os.Build;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class NotificationMonitor extends NotificationListenerService {
    private static final String TAG = "NotificationMonitor";
    private static final String WHATSAPP_PACKAGE = "com.whatsapp";
    private static final String INSTAGRAM_PACKAGE = "com.instagram.android";
    private static final String SNAPCHAT_PACKAGE = "com.snapchat.android";
    private static final String TELEGRAM_PACKAGE = "org.telegram.messenger";
    private static final String SIGNAL_PACKAGE = "org.thoughtcrime.securesms";
    private static final String MESSENGER_PACKAGE = "com.facebook.orca";

    private DatabaseHelper databaseHelper;

    @Override
    public void onCreate() {
        super.onCreate();
        databaseHelper = new DatabaseHelper();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            String packageName = sbn.getPackageName();
            
            // Check if the notification is from a messaging app we're monitoring
            if (!isTargetMessagingApp(packageName)) {
                return;
            }

            Notification notification = sbn.getNotification();
            Bundle extras = notification.extras;

            // Extract notification details
            String title = extras.getString(Notification.EXTRA_TITLE, "");
            String text = extras.getString(Notification.EXTRA_TEXT, "");
            
            // Skip if notification doesn't contain useful information
            if (title.isEmpty() || text.isEmpty()) {
                return;
            }

            // Process message based on app
            processMessage(packageName, title, text);

        } catch (Exception e) {
            Log.e(TAG, "Error processing notification: " + e.getMessage());
        }
    }

    private boolean isTargetMessagingApp(String packageName) {
        return packageName.equals(WHATSAPP_PACKAGE) ||
               packageName.equals(INSTAGRAM_PACKAGE) ||
               packageName.equals(SNAPCHAT_PACKAGE) ||
               packageName.equals(TELEGRAM_PACKAGE) ||
               packageName.equals(SIGNAL_PACKAGE) ||
               packageName.equals(MESSENGER_PACKAGE);
    }

    private void processMessage(String packageName, String sender, String messageText) {
        String platform = getPlatformName(packageName);
        String userId = getUserId();
        String phoneModel = getDeviceModel();
        String messageDate = getCurrentDate();
        
        // Create message data
        MessageData messageData = new MessageData(
            sender,
            "You", // receiver is always the device owner
            messageText,
            String.valueOf(System.currentTimeMillis()),
            "incoming", // notifications are always incoming messages
            platform
        );

        // Create unique message ID
        String uniqueMessageId = sanitizeData(sender) + "|" + 
                               System.currentTimeMillis() + "|" +
                               "incoming" + "|" + 
                               sanitizeData(messageText);

        // Upload to Firebase using the same path as AccessibilityService
        databaseHelper.uploadSocialMessageData(
            userId,
            phoneModel,
            messageData,
            uniqueMessageId,
            messageDate,
            platform
        );
    }

    private String getPlatformName(String packageName) {
        switch (packageName) {
            case WHATSAPP_PACKAGE:
                return "whatsapp";
            case INSTAGRAM_PACKAGE:
                return "instagram";
            case SNAPCHAT_PACKAGE:
                return "snapchat";
            case TELEGRAM_PACKAGE:
                return "telegram";
            case SIGNAL_PACKAGE:
                return "signal";
            case MESSENGER_PACKAGE:
                return "messenger";
            default:
                return "unknown";
        }
    }

    private String getUserId() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return (user != null) ? user.getUid() : "unknown";
    }

    private String getDeviceModel() {
        return Build.MODEL;
    }

    private String getCurrentDate() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    private String sanitizeData(String input) {
        if (input == null) {
            return "";
        }
        // Remove characters that Firebase doesn't allow in paths
        return input.replaceAll("[.#$\\[\\]/]", "_");
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Not needed for our use case
    }
}
