package com.childmonitorai.monitors;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.childmonitorai.services.FcmService;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Monitors browser app foreground activity using {@link UsageStatsManager}.
 *
 * Requires the PACKAGE_USAGE_STATS permission (already declared in the manifest).
 *
 * When any supported browser package moves to the foreground the parent is
 * notified via FCM and the event is persisted in the Firebase notifications
 * node. A per-package cooldown prevents repeated alerts for the same browser
 * session.
 *
 * Works on Android 5.0+ (API 21+) without requiring Accessibility Service.
 */
public class BrowserActivityMonitor {

    private static final String TAG = "BrowserActivityMonitor";

    /** Polling interval – how often we query UsageStats events. */
    private static final long POLL_INTERVAL_MS = 10_000;

    /**
     * Minimum gap between two notifications for the same browser package.
     * Default: 5 minutes. Prevents alert spam when the child keeps Chrome open.
     */
    private static final long NOTIFICATION_COOLDOWN_MS = 5 * 60 * 1000;

    /** Known browser package names. Matched exactly against foreground event package names. */
    private static final Set<String> BROWSER_PACKAGES = new HashSet<>(Arrays.asList(
            "com.android.chrome",
            "com.google.android.apps.chrome",
            "org.mozilla.firefox",
            "org.mozilla.fenix",
            "com.microsoft.emmx",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.brave.browser",
            "com.UCMobile.intl",
            "com.uc.browser.en",
            "com.sec.android.app.sbrowser",
            "com.android.browser"
    ));

    private final Context context;
    private final String userId;
    private final String phoneModel;
    private final UsageStatsManager usageStatsManager;
    private final HandlerThread handlerThread;
    private final Handler handler;

    private volatile boolean isRunning = false;
    private long lastPollTime;

    /** Tracks last notification time per browser package to enforce cooldown. */
    private final Map<String, Long> lastNotificationTimeMap = new HashMap<>();

    public BrowserActivityMonitor(Context context, String userId, String phoneModel) {
        this.context = context.getApplicationContext();
        this.userId = userId;
        this.phoneModel = phoneModel;
        this.usageStatsManager =
                (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        this.handlerThread = new HandlerThread("BrowserActivityMonitorThread");
        this.handlerThread.start();
        this.handler = new Handler(handlerThread.getLooper());
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void startMonitoring() {
        if (isRunning) return;
        if (!hasUsageStatsPermission()) {
            Log.w(TAG, "PACKAGE_USAGE_STATS not granted – BrowserActivityMonitor will not start");
            return;
        }
        isRunning = true;
        lastPollTime = System.currentTimeMillis();
        Log.d(TAG, "BrowserActivityMonitor started for user: " + userId);
        handler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
    }

    public void stopMonitoring() {
        isRunning = false;
        handler.removeCallbacks(pollRunnable);
        handlerThread.quitSafely();
        Log.d(TAG, "BrowserActivityMonitor stopped");
    }

    // -------------------------------------------------------------------------
    // Polling
    // -------------------------------------------------------------------------

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRunning) return;
            try {
                checkBrowserForeground();
            } catch (Exception e) {
                Log.e(TAG, "Error polling browser activity", e);
            }
            if (isRunning) {
                handler.postDelayed(this, POLL_INTERVAL_MS);
            }
        }
    };

    /**
     * Queries UsageStats events since the last poll. Fires a notification for
     * each distinct browser package that moved to the foreground, subject to
     * the per-package cooldown.
     */
    private void checkBrowserForeground() {
        if (usageStatsManager == null) return;

        long now = System.currentTimeMillis();
        // Slight backward overlap to avoid missing events at boundary
        UsageEvents events = usageStatsManager.queryEvents(lastPollTime - 1_000, now);
        lastPollTime = now;

        if (events == null) return;

        // Collect unique browser packages that came to foreground in this window
        Set<String> activeBrowsers = new HashSet<>();
        UsageEvents.Event event = new UsageEvents.Event();

        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND
                    && isBrowserPackage(event.getPackageName())) {
                activeBrowsers.add(event.getPackageName());
            }
        }

        for (String pkg : activeBrowsers) {
            Long lastTime = lastNotificationTimeMap.get(pkg);
            if (lastTime == null || (now - lastTime) >= NOTIFICATION_COOLDOWN_MS) {
                lastNotificationTimeMap.put(pkg, now);
                sendBrowserActiveNotification(pkg, now);
            } else {
                Log.d(TAG, "Cooldown active for " + pkg + " – skipping notification");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Notification
    // -------------------------------------------------------------------------

    private void sendBrowserActiveNotification(String packageName, long timestamp) {
        String appName = getAppName(packageName);
        String title = "Browser Activity Detected";
        String message = appName + " is now active on your child's device";

        Log.d(TAG, "Sending browser active notification for: " + appName);

        // Send push notification to parent via FCM
        Intent fcmIntent = new Intent(context, FcmService.class);
        fcmIntent.putExtra("title", title);
        fcmIntent.putExtra("message", message);
        fcmIntent.putExtra("url", "browser_active:" + packageName);
        fcmIntent.putExtra("userId", userId);
        context.startService(fcmIntent);

        // Persist to Firebase so it appears in the parent dashboard
        String currentDate = new SimpleDateFormat("yyyyMMdd", Locale.getDefault())
                .format(new Date(timestamp));
        DatabaseReference notificationsRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(userId)
                .child("phones")
                .child(phoneModel)
                .child("notifications")
                .child(currentDate);

        Map<String, Object> notification = new HashMap<>();
        notification.put("title", title);
        notification.put("body", message);
        notification.put("timestamp", timestamp);
        notification.put("type", "browser_active");
        notification.put("packageName", packageName);
        notification.put("appName", appName);
        notification.put("deviceModel", phoneModel);

        notificationsRef.push().setValue(notification)
                .addOnSuccessListener(aVoid ->
                        Log.d(TAG, "Browser activity notification logged: " + packageName))
                .addOnFailureListener(e ->
                        Log.e(TAG, "Failed to log browser activity notification: " + e.getMessage()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean isBrowserPackage(String packageName) {
        if (packageName == null) return false;
        return BROWSER_PACKAGES.contains(packageName);
    }

    private String getAppName(String packageName) {
        try {
            PackageManager pm = context.getPackageManager();
            return pm.getApplicationLabel(
                    pm.getApplicationInfo(packageName, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }

    private boolean hasUsageStatsPermission() {
        try {
            AppOpsManager appOps =
                    (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            int mode = appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            Log.e(TAG, "Error checking usage stats permission", e);
            return false;
        }
    }
}
