package com.childmonitorai;

import android.app.AppOpsManager;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;
import com.google.firebase.auth.FirebaseAuth;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class AppUsageService extends Service {

    private static final String TAG = "AppUsageService";
    private static final long CHECK_INTERVAL = TimeUnit.MINUTES.toMillis(1); // 1-minute interval

    private UsageStatsManager usageStatsManager;
    private Handler handler;
    private Runnable appUsageTask;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "AppUsageService started");

        // Initialize UsageStatsManager
        usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);

        // Initialize background handler
        HandlerThread handlerThread = new HandlerThread("AppUsageHandlerThread");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());

        // Define the periodic task
        appUsageTask = new Runnable() {
            @Override
            public void run() {
                trackOtherAppUsage(); // Track other apps' usage (foreground or background)
                handler.postDelayed(this, CHECK_INTERVAL); // Schedule the task again
            }
        };

        // Start the periodic task
        handler.post(appUsageTask);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "AppUsageService destroyed");

        if (handler != null && appUsageTask != null) {
            handler.removeCallbacks(appUsageTask);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // This service does not support binding
    }

    private void trackOtherAppUsage() {
        if (!hasUsageStatsPermission()) {
            Log.e(TAG, "Usage stats permission not granted. Redirecting to settings.");
            requestUsageStatsPermission(); // Show prompt to the user to enable usage stats
            return;
        }

        String activePackageName = getActivePackageName();
        if (activePackageName == null) {
            Log.w(TAG, "No active package detected.");
            return;
        }

        long currentTime = System.currentTimeMillis();
        long usageDuration = calculateUsageDuration(activePackageName);

        // Create AppUsageData object to hold usage information
        AppUsageData appUsageData = new AppUsageData(activePackageName, usageDuration, currentTime);

        // Upload data to Firebase
        String userId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? sanitizePath(FirebaseAuth.getInstance().getCurrentUser().getUid())
                : null;

        if (userId == null) {
            Log.e(TAG, "User not signed in. Skipping upload.");
            return;
        }

        // Upload app usage data
        String phoneModel = sanitizePath(Build.MODEL);
        DatabaseHelper dbHelper = new DatabaseHelper();
        dbHelper.uploadAppUsageDataByDate(userId, phoneModel, appUsageData);

        Log.d(TAG, "Uploaded app usage data for package: " + activePackageName);
    }

    private String getActivePackageName() {
        long currentTime = System.currentTimeMillis();
        long startTime = currentTime - 60000; // Look at the last 1 minute

        List<UsageStats> usageStatsList = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, startTime, currentTime);

        if (usageStatsList == null || usageStatsList.isEmpty()) {
            Log.e(TAG, "No usage stats available.");
            return null;
        }

        UsageStats recentStats = null;
        for (UsageStats stats : usageStatsList) {
            Log.d(TAG, "Package: " + stats.getPackageName() + ", Last Used: " + stats.getLastTimeUsed());
            if (recentStats == null || stats.getLastTimeUsed() > recentStats.getLastTimeUsed()) {
                recentStats = stats;
            }
        }

        if (recentStats != null && recentStats.getLastTimeUsed() > startTime) {
            return recentStats.getPackageName(); // Return the active package name
        } else {
            Log.e(TAG, "No recently used app detected.");
            return null;
        }
    }

    private long calculateUsageDuration(String packageName) {
        // Calculate the total time an app was used within a time range
        long currentTime = System.currentTimeMillis();
        long startTime = currentTime - 60000; // Look at the last 1 minute

        List<UsageStats> usageStatsList = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, startTime, currentTime);

        if (usageStatsList == null || usageStatsList.isEmpty()) {
            return 0;
        }

        for (UsageStats stats : usageStatsList) {
            if (stats.getPackageName().equals(packageName)) {
                return stats.getTotalTimeInForeground(); // Returns the time the app spent in the foreground
            }
        }
        return 0;
    }

    private boolean hasUsageStatsPermission() {
        AppOpsManager appOpsManager = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private void requestUsageStatsPermission() {
        // Here you can show a dialog or redirect the user to the settings page to enable usage stats permission
        Intent intent = new Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private String sanitizePath(String path) {
        if (path == null) return "";
        return path.replaceAll("[.#$\\[\\]]", "_");
    }
}
