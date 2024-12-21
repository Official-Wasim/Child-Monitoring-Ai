package com.childmonitorai;

import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class AppUsageService extends Service {

    private static final String TAG = "AppUsageService";

    private Timer timer;
    private long lastUsageTime = 0;
    private UsageStatsManager usageStatsManager;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "AppUsageService started");

        // Initialize UsageStatsManager
        usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);

        // Schedule the task to check app usage periodically (every minute)
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                trackAppUsage();
            }
        }, 0, 60000); // Periodic check every 1 minute
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (timer != null) {
            timer.cancel();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void trackAppUsage() {
        // Get the current active app's package name and calculate the usage duration
        String appName = getActiveAppName();
        String packageName = getPackageNameFromApp(appName);
        long currentTime = System.currentTimeMillis();
        long usageDuration = currentTime - lastUsageTime;

        // Create AppUsageData object
        AppUsageData appUsageData = new AppUsageData(appName, packageName, usageDuration, currentTime);

        // Upload the usage data by date
        DatabaseHelper dbHelper = new DatabaseHelper();
        String userId = "user123";  // Replace with actual userId
        String phoneModel = "phone123";  // Replace with actual phone model
        dbHelper.uploadAppUsageDataByDate(userId, phoneModel, appUsageData);

        lastUsageTime = currentTime;
    }

    private String getActiveAppName() {
        // Get the list of recent app usage stats
        long currentTime = System.currentTimeMillis();
        long startTime = currentTime - 10000; // Look for usage stats from the last 10 seconds

        List<UsageStats> usageStatsList = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, startTime, currentTime);

        if (usageStatsList == null || usageStatsList.isEmpty()) {
            return "UnknownApp";
        }

        // Find the most recent active app
        UsageStats recentStats = null;
        for (UsageStats stats : usageStatsList) {
            if (recentStats == null || stats.getLastTimeUsed() > recentStats.getLastTimeUsed()) {
                recentStats = stats;
            }
        }

        if (recentStats != null) {
            return recentStats.getPackageName(); // Return package name of the active app
        } else {
            return "UnknownApp";
        }
    }

    private String getPackageNameFromApp(String appName) {
        // Logic to retrieve the package name from the app name
        // In this example, we assume the app name and package name are the same
        return appName;
    }
}
