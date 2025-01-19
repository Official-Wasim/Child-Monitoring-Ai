package com.childmonitorai;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;


public class SocialMediaMonitorService extends AccessibilityService {
    private static final String TAG = "SocialMediaMonitorService";
    private static final String WHATSAPP_PACKAGE = "com.whatsapp";
    private static final String INSTAGRAM_PACKAGE = "com.instagram.android";
    private static final String SNAPCHAT_PACKAGE = "com.snapchat.android";
    private static final String TELEGRAM_PACKAGE = "org.telegram.messenger";

    private DatabaseReference mDatabase;
    private Set<String> processedMessages = new HashSet<>();
    private static final int MAX_PROCESSED_MESSAGES = 100;
    private static final int MAX_MESSAGE_LENGTH = 20; // Limit for message length (10 characters)
    private WhatsappMonitor whatsappMonitor;
    private InstagramMonitor instagramMonitor;
    private SnapchatMonitor snapchatMonitor;
    private TelegramMonitor telegramMonitor;
    private DatabaseHelper databaseHelper;


    private static class MessageInfo {
        String message;
        boolean isOutgoing;

        MessageInfo(String message, boolean isOutgoing) {
            this.message = message;
            this.isOutgoing = isOutgoing;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mDatabase = FirebaseDatabase.getInstance().getReference("social_media_messages");
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        whatsappMonitor = new WhatsappMonitor(displayMetrics);
        instagramMonitor = new InstagramMonitor(displayMetrics);
        snapchatMonitor = new SnapchatMonitor(displayMetrics);
        telegramMonitor = new TelegramMonitor(displayMetrics);
        databaseHelper = new DatabaseHelper();
        Log.d(TAG, "Service created");
    }

    @Override
    public void onServiceConnected() {
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED |
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED |
                AccessibilityEvent.TYPE_VIEW_CLICKED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS |
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY |
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        info.notificationTimeout = 100;

        String[] packageNames = {WHATSAPP_PACKAGE, INSTAGRAM_PACKAGE, SNAPCHAT_PACKAGE, TELEGRAM_PACKAGE};
        info.packageNames = packageNames;

        setServiceInfo(info);
        Log.d(TAG, "Service connected and configured");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";

        if (!packageName.equals(WHATSAPP_PACKAGE) &&
                !packageName.equals(INSTAGRAM_PACKAGE) &&
                !packageName.equals(SNAPCHAT_PACKAGE) &&
                !packageName.equals(TELEGRAM_PACKAGE)) {
            return;
        }
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) {
            Log.e(TAG, "Root node is null for package: " + packageName);
            return;
        }

        try {
            if (packageName.equals(WHATSAPP_PACKAGE)) {
                whatsappMonitor.processMessages(rootNode, processedMessages, getUserId(), getDeviceModel(), MAX_MESSAGE_LENGTH);
            } else if (packageName.equals(INSTAGRAM_PACKAGE)) {
                instagramMonitor.processMessages(rootNode, processedMessages, getUserId(), getDeviceModel(), databaseHelper, MAX_MESSAGE_LENGTH);
            } else if (packageName.equals(SNAPCHAT_PACKAGE)) {
                snapchatMonitor.processMessages(rootNode, processedMessages, getUserId(), getDeviceModel(), MAX_MESSAGE_LENGTH);
            } else if (packageName.equals(TELEGRAM_PACKAGE)) {
                telegramMonitor.processMessages(rootNode, processedMessages, getUserId(), getDeviceModel(), MAX_MESSAGE_LENGTH);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing messages: " + e.getMessage(), e);
        } finally {
            rootNode.recycle();
        }

        // Cleanup processed messages if needed
        if (processedMessages.size() > MAX_PROCESSED_MESSAGES) {
            processedMessages.clear();
        }
    }
    public String getUserId() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return (user != null) ? user.getUid() : "No User Logged In"; // Returns user ID or a default message if no user is logged in
    }

    // Method to get the device model
    public String getDeviceModel() {
        return Build.MODEL;
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Service interrupted");
    }
}