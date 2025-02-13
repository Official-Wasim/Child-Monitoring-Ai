package com.childmonitorai.services;
import com.childmonitorai.helpers.FlaggedContents;
import com.childmonitorai.database.DatabaseHelper;
import com.childmonitorai.models.WebVisitData;


import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
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
import com.childmonitorai.helpers.Preferences;


import java.util.ArrayList;

public class WebMonitorService extends AccessibilityService implements FlaggedContents.FlaggedContentListener {
    private static final String TAG = "WebMonitor";
    private String userId;
    private String phoneModel;
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?:https?://)?(?:www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b(?:[-a-zA-Z0-9()@:%_\\+.~#?&//=]*)");

    // Cache to store the visited URLs and their timestamps
    private Map<String, Long> visitedUrlsCache = new HashMap<>();
    private static final long CACHE_EXPIRY_TIME = 5 * 1000; // 5 seconds cache expiry
    private DatabaseHelper dbHelper;
    private Map<String, WebVisitData> activeVisits = new HashMap<>();
    private static final long INACTIVE_THRESHOLD = 30 * 1000; // 30 seconds threshold
    private static final long UPDATE_INTERVAL = 5 * 1000; // Update duration every 5 seconds
    private String currentUrl = null;
    private static final String CHANNEL_ID = "flagged_content_channel";
    private FlaggedContents flaggedContents;
    private List<String> currentFlaggedKeywords = new ArrayList<>();
    private List<String> currentFlaggedUrls = new ArrayList<>();
    private Preferences preferences;

    private boolean getUserInfo() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        userId = prefs.getString("userId", null);
        phoneModel = prefs.getString("phoneModel", null);

        if (userId == null || phoneModel == null) {
            Log.e(TAG, "User ID or Phone Model not found. Service cannot start.");
            stopSelf();
            return false;
        }
        
        Log.d(TAG, "User info retrieved - UserId: " + userId + ", Phone: " + phoneModel);
        return true;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        
        if (!getUserInfo()) {
            return;
        }

        dbHelper = new DatabaseHelper();
        flaggedContents = new FlaggedContents();
        preferences = new Preferences(); // Initialize Preferences
        FlaggedContents.initialize();
        FlaggedContents.addContentListener(this);

        Log.d(TAG, "WebMonitor started with userId: " + userId + " and phoneModel: " + phoneModel);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!getUserInfo()) {
            return START_NOT_STICKY;
        }

        if (intent != null) {
            String intentUserId = intent.getStringExtra("userId");
            String intentPhoneModel = intent.getStringExtra("phoneModel");

            if (intentUserId != null && intentPhoneModel != null) {
                userId = intentUserId;
                phoneModel = intentPhoneModel;
                Log.d(TAG, "WebMonitor updated with userId: " + userId + " and phoneModel: " + phoneModel);
            }
        }

        return START_STICKY; // Important: Use START_STICKY
    }
    

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (userId == null || phoneModel == null) {
            if (!getUserInfo()) {
                return;
            }
        }

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


    private void handleUrlVisit(String url, String packageName) {
        if (url == null || url.equals("No URL Found")) return;

        long currentTime = System.currentTimeMillis();

        // Only process if URL has changed
        if (!url.equals(currentUrl)) {
            Log.d(TAG, "URL changed from " + currentUrl + " to " + url);
            
            // Close tracking for previous URL
            if (currentUrl != null) {
                WebVisitData previousVisit = activeVisits.get(currentUrl);
                if (previousVisit != null) {
                    previousVisit.updateDuration(currentTime);
                    previousVisit.setActive(false);
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
            
            // Check for flagged content
            if (isFlaggedContent(url)) {
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

    private String getCurrentDate() {
        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyyMMdd");
        return dateFormat.format(new java.util.Date());
    }

    private void sendNotificationToParent(String flaggedUrl) {
        // Check if blocked website alerts are enabled
        if (!preferences.isBlockedWebsite()) {
            Log.d(TAG, "Blocked website alerts are disabled. Skipping notification.");
            return;
        }

        // Rest of the notification sending code remains the same
        Intent fcmIntent = new Intent(this, FcmService.class);
        fcmIntent.putExtra("url", flaggedUrl);
        fcmIntent.putExtra("title", "Flagged Content Alert");
        fcmIntent.putExtra("message", "Child attempted to visit: " + flaggedUrl);
        fcmIntent.putExtra("userId", userId); 
        startService(fcmIntent);
        
        // Get current date for organization
        String currentDate = getCurrentDate();
        
        // Log the flagged content for record keeping (without notification)
        DatabaseReference notificationsRef = FirebaseDatabase.getInstance()
            .getReference("users")
            .child(userId)
            .child("phones")
            .child(phoneModel)
            .child("notifications")
            .child(currentDate);  

        Map<String, Object> notification = new HashMap<>();
        notification.put("title", "Flagged Content Alert");
        notification.put("body", "Child attempted to visit: " + flaggedUrl);
        notification.put("timestamp", System.currentTimeMillis());
        notification.put("type", "web_alert");
        notification.put("url", flaggedUrl);
        notification.put("deviceModel", phoneModel);

        notificationsRef.push().setValue(notification)
            .addOnSuccessListener(aVoid -> Log.d(TAG, "Flagged content logged to database for date: " + currentDate))
            .addOnFailureListener(e -> Log.e(TAG, "Failed to log flagged content: " + e.getMessage()));
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
    public void onFlaggedContentUpdated(List<String> keywords, List<String> urls) {
        currentFlaggedKeywords = keywords;
        currentFlaggedUrls = urls;
        
        // Check current URL against new flagged content
        if (currentUrl != null) {
            if (isFlaggedContent(currentUrl)) {
                sendNotificationToParent(currentUrl);
            }
        }
    }

    @Override
    public void onFlaggedContentRemoved(String removedItem, String type) {
        Log.d(TAG, "Flagged content removed - " + type + ": " + removedItem);
        if ("keyword".equals(type)) {
            currentFlaggedKeywords.remove(removedItem.toLowerCase());
        } else if ("url".equals(type)) {
            currentFlaggedUrls.remove(removedItem.toLowerCase());
        }
    }

    private boolean isFlaggedContent(String url) {
        if (url == null) return false;
        
        String lowerUrl = url.toLowerCase();
        
        // Check against current keywords
        for (String keyword : currentFlaggedKeywords) {
            if (lowerUrl.contains(keyword)) {
                Log.d(TAG, "Flagged content detected - keyword: " + keyword);
                return true;
            }
        }
        
        // Check against current URLs
        for (String flaggedUrl : currentFlaggedUrls) {
            if (lowerUrl.contains(flaggedUrl)) {
                Log.d(TAG, "Flagged content detected - URL: " + flaggedUrl);
                return true;
            }
        }
        
        return false;
    }

    public void stopMonitor() {
        // Save final data for current URL if exists
        if (currentUrl != null) {
            WebVisitData visit = activeVisits.get(currentUrl);
            if (visit != null) {
                visit.updateDuration(System.currentTimeMillis());
                visit.setActive(false);
                dbHelper.uploadWebVisitDataByDate(userId, phoneModel, visit)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Final data uploaded for URL: " + currentUrl))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to upload final data: " + e.getMessage()));
            }
            Log.i(TAG, "WebMonitor monitoring stopped successfully");
        }

        // Clear all active visits and cache
        activeVisits.clear();
        visitedUrlsCache.clear();
        currentUrl = null;

        // Remove content listener
        FlaggedContents.removeContentListener(this);

        // Stop the service
        stopSelf();

        Log.d(TAG, "Web monitoring stopped");
    }
}
