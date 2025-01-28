package com.childmonitorai;
import com.childmonitorai.models.WebVisitData;


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
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

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
    private KeywordMonitor keywordMonitor;
    private Map<String, WebVisitData> activeVisits = new HashMap<>();
    private static final long INACTIVE_THRESHOLD = 30 * 1000; // 30 seconds threshold
    private static final long UPDATE_INTERVAL = 5 * 1000; // Update duration every 5 seconds
    private String currentUrl = null;
    private static final String FCM_BASE_URL = "https://fcm.googleapis.com/";
    private FcmApiService fcmApiService;

    @Override
    public void onCreate() {
        super.onCreate();

        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        userId = prefs.getString("userId", "defaultUserId");
        phoneModel = prefs.getString("phoneModel", "defaultPhoneModel");
        dbHelper = new DatabaseHelper(); // Initialize DatabaseHelper
        keywordMonitor = new KeywordMonitor(userId);

        Log.d(TAG, "WebMonitor started with userId: " + userId + " and phoneModel: " + phoneModel);

        // Initialize Retrofit and FCM API service
        Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(FCM_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build();
        fcmApiService = retrofit.create(FcmApiService.class);
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
        if (userId == null || phoneModel == null) return;

        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {

            String packageName = event.getPackageName().toString();
            if (isBrowserPackage(packageName)) {
                AccessibilityNodeInfo rootNode = event.getSource();
                if (rootNode != null) {
                    String url = extractUrlFromNode(rootNode);
                    handleUrlVisit(url, packageName);
                }
            }
        }
    }

    private boolean containsFlaggedWords(String url) {
        String urlLower = url.toLowerCase();
        for (String flaggedUrl : Constants.FLAGGED_URLS) {
            if (urlLower.contains(flaggedUrl.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private void handleUrlVisit(String url, String packageName) {
        if (url == null || url.equals("No URL Found")) return;

        long currentTime = System.currentTimeMillis();
        
        // Check for flagged URLs immediately
        if (containsFlaggedWords(url)) {
            sendNotificationToParent(url);
        }

        // Only process if URL has changed
        if (!url.equals(currentUrl)) {
            Log.d(TAG, "URL changed from " + currentUrl + " to " + url);
            
            // Close tracking for previous URL
            if (currentUrl != null) {
                WebVisitData previousVisit = activeVisits.get(currentUrl);
                if (previousVisit != null) {
                    previousVisit.updateDuration(currentTime);
                    previousVisit.setActive(false);
                    // Final upload for previous URL
                    dbHelper.uploadWebVisitDataByDate(userId, phoneModel, previousVisit)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Final data uploaded for previous URL: " + currentUrl);
                            activeVisits.remove(currentUrl);
                        });
                }
            }

            // Start tracking new URL
            WebVisitData newVisit = new WebVisitData(url, packageName, currentTime);
            activeVisits.put(url, newVisit);
            
            if (keywordMonitor.isFlaggedContent(url)) {
                sendNotificationToParent(url);
            }

            // Upload initial visit data
            dbHelper.uploadWebVisitDataByDate(userId, phoneModel, newVisit)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Initial web visit data uploaded"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to upload initial web visit: " + e.getMessage()));

            visitedUrlsCache.put(url, currentTime);
            currentUrl = url;
        } else {
            // Update duration for current URL
            WebVisitData currentVisit = activeVisits.get(url);
            if (currentVisit != null) {
                currentVisit.updateDuration(currentTime);
                
                // Periodic updates for long visits
                if (currentTime - currentVisit.getTimestamp() % UPDATE_INTERVAL < 1000) {
                    // Use same reference key for updates
                    dbHelper.uploadWebVisitDataByDate(userId, phoneModel, currentVisit)
                        .addOnSuccessListener(aVoid -> Log.d(TAG, "Updated duration for: " + url));
                }
            }
        }
    }

    private void updateActiveVisits(long currentTime) {
        // Only update current URL if it exists
        if (currentUrl != null && activeVisits.containsKey(currentUrl)) {
            WebVisitData visit = activeVisits.get(currentUrl);
            if (visit.isActive()) {
                visit.updateDuration(currentTime);
            }
        }
    }

    private void sendNotificationToParent(String flaggedUrl) {
        DatabaseReference notificationsRef = FirebaseDatabase.getInstance()
            .getReference("notifications")
            .child(userId);

        // Create main message container
        Map<String, Object> fcmMessage = new HashMap<>();
        fcmMessage.put("to", Constants.PARENT_FCM_TOKEN); // Use specific token instead of topic
        fcmMessage.put("priority", "high");

        // Create notification payload
        Map<String, Object> notification = createNotificationPayload(flaggedUrl);
        fcmMessage.put("notification", notification);

        // Create data payload
        Map<String, Object> data = createDataPayload(flaggedUrl);
        fcmMessage.put("data", data);

        // Store in database and send FCM message
        notificationsRef.push().setValue(fcmMessage)
            .addOnSuccessListener(aVoid -> {
                Log.d(TAG, "Notification stored, sending to token: " + Constants.PARENT_FCM_TOKEN);
                sendDirectFcmMessage(fcmMessage);
            })
            .addOnFailureListener(e -> Log.e(TAG, "Failed to store notification: " + e.getMessage()));
    }

    private void sendDirectFcmMessage(Map<String, Object> fcmMessage) {
        fcmApiService.sendMessage(fcmMessage).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful()) {
                    Log.d(TAG, "FCM message sent successfully: " + response.body());
                } else {
                    Log.e(TAG, "Failed to send FCM message: " + response.errorBody());
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Log.e(TAG, "Error sending FCM message", t);
            }
        });
    }

    private Map<String, Object> createNotificationPayload(String flaggedUrl) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("title", "⚠️ Web Alert");
        notification.put("body", "Restricted website access detected: " + flaggedUrl);
        notification.put("sound", "default");
        notification.put("android_channel_id", "high_importance_channel");
        notification.put("priority", "high");
        notification.put("badge", "1");
        return notification;
    }

    private Map<String, Object> createDataPayload(String flaggedUrl) {
        Map<String, Object> data = new HashMap<>();
        data.put("click_action", "FLUTTER_NOTIFICATION_CLICK");
        data.put("screen", "/DashboardScreen");
        data.put("url", flaggedUrl);
        data.put("deviceModel", phoneModel);
        data.put("type", "web_alert");
        data.put("userId", userId);
        data.put("timestamp", String.valueOf(System.currentTimeMillis()));
        data.put("status", "new");
        return data;
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

    @Override
    public void onDestroy() {
        if (currentUrl != null) {
            WebVisitData visit = activeVisits.get(currentUrl);
            if (visit != null) {
                visit.updateDuration(System.currentTimeMillis());
                visit.setActive(false);
                dbHelper.uploadWebVisitDataByDate(userId, phoneModel, visit);
            }
        }
        super.onDestroy();
    }
}
