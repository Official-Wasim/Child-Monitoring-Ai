package com.childmonitorai;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.android.gms.tasks.Task;

public class WebMonitor extends AccessibilityService {
    private static final String TAG = "WebMonitor";
    private String userId;
    private String phoneModel;
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?:https?://)?(?:www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b(?:[-a-zA-Z0-9()@:%_\\+.~#?&//=]*)");

    // Cache to store the visited URLs and their timestamps
    private Map<String, Long> visitedUrlsCache = new HashMap<>();
    private static final long CACHE_EXPIRY_TIME = 5 * 1000; // 5 seconds cache expiry
    private DatabaseHelper dbHelper;

    @Override
    public void onCreate() {
        super.onCreate();

        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        userId = prefs.getString("userId", "defaultUserId");
        phoneModel = prefs.getString("phoneModel", "defaultPhoneModel");
        dbHelper = new DatabaseHelper(); // Initialize DatabaseHelper

        Log.d(TAG, "WebMonitor started with userId: " + userId + " and phoneModel: " + phoneModel);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            userId = intent.getStringExtra("userId");
            phoneModel = intent.getStringExtra("phoneModel");

            if (userId != null && phoneModel != null) {
                Log.d(TAG, "WebMonitor started with userId: " + userId + " and phoneModel: " + phoneModel);
            } else {
                Log.e(TAG, "Failed to get userId or phoneModel from Intent");
            }
        }

        return START_STICKY; // Important: Use START_STICKY
    }
    

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (userId != null && phoneModel != null) {
            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                    event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {

                String packageName = event.getPackageName().toString();

                if (isBrowserPackage(packageName)) {
                    AccessibilityNodeInfo rootNode = event.getSource();
                    if (rootNode != null) {
                        String url = extractUrlFromNode(rootNode);

                        if (url != null && !url.equals("No URL Found")) {
                            // Check if the URL has already been uploaded recently
                            if (shouldUploadUrl(url)) {
                                String title = packageName;
                                long timestamp = System.currentTimeMillis();

                                WebVisitData visitData = new WebVisitData(url, title, timestamp);
                                dbHelper.uploadWebVisitDataByDate(userId, phoneModel, visitData)
                                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Web visit data uploaded successfully."))
                                    .addOnFailureListener(e -> Log.e(TAG, "Failed to upload web visit data: " + e.getMessage()));

                                // Update the cache with the new timestamp for this URL
                                visitedUrlsCache.put(url, timestamp);

                                Log.d(TAG, "URL Detected and Uploaded: " + url);
                            } else {
                                Log.d(TAG, "URL already uploaded recently: " + url);
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isBrowserPackage(String packageName) {
        return packageName.contains("chrome") || packageName.contains("browser") || packageName.contains("firefox");
    }

    private String extractUrlFromNode(AccessibilityNodeInfo node) {
        if (node == null) {
            return null;
        }

        List<AccessibilityNodeInfo> editTextNodes = node.findAccessibilityNodeInfosByViewId("com.android.chrome:id/url_bar");
        if (!editTextNodes.isEmpty()) {
            AccessibilityNodeInfo editTextNode = editTextNodes.get(0);
            CharSequence text = editTextNode.getText();
            if (text != null) {
                return extractUrlFromText(text.toString());
            }
        }

        // Check for other potential URL locations (e.g., web view titles)
        List<AccessibilityNodeInfo> webViews = node.findAccessibilityNodeInfosByViewId("android:id/title");
        if (!webViews.isEmpty()) {
            AccessibilityNodeInfo webView = webViews.get(0);
            CharSequence title = webView.getContentDescription();
            if (title != null) {
                return extractUrlFromText(title.toString());
            }
        }

        return null;
    }

    private String extractUrlFromText(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        Matcher matcher = URL_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(0);
        }
        return null;
    }

    private boolean shouldUploadUrl(String url) {
        // Check if the URL is in the cache and if it was visited recently
        if (visitedUrlsCache.containsKey(url)) {
            long lastVisitedTime = visitedUrlsCache.get(url);
            long currentTime = System.currentTimeMillis();
            // If the URL was visited within the last CACHE_EXPIRY_TIME (e.g., 5 seconds), do not upload
            if (currentTime - lastVisitedTime < CACHE_EXPIRY_TIME) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "WebMonitor interrupted");
    }
}
