package com.childmonitorai.helpers;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.WorkManager;

import com.childmonitorai.database.DatabaseHelper;
import com.childmonitorai.models.AppUsageData;
import com.childmonitorai.monitors.AppUsageService;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class UsageTrackingWorker extends Worker {
    private static final String TAG = "UsageTrackingWorker";
    private final UsageStatsManager usageStatsManager;
    private final Context context;

    public UsageTrackingWorker(
            @NonNull Context context,
            @NonNull WorkerParameters params) {
        super(context, params);
        this.context = context;
        this.usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            // Get input data
            String userId = getInputData().getString("userId");
            String phoneModel = getInputData().getString("phoneModel");
            
            if (userId == null || phoneModel == null || userId.isEmpty() || phoneModel.isEmpty()) {
                Log.e(TAG, "Missing required userId or phoneModel parameters");
                return Result.failure();
            }

            Log.d(TAG, "Starting usage tracking work for user: " + userId + ", device: " + phoneModel);

            long endTime = System.currentTimeMillis();
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.MINUTE, -15);
            long startTime = calendar.getTimeInMillis();

            // Query usage stats
            UsageEvents events = usageStatsManager.queryEvents(startTime, endTime);
            
            if (events == null) {
                Log.e(TAG, "Failed to query usage events");
                return Result.failure();
            }

            // Process events here before starting service
            UsageEvents.Event event = new UsageEvents.Event();
            DatabaseHelper databaseHelper = new DatabaseHelper();
            Map<String, AppUsageData> usageDataMap = new HashMap<>();

            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                if (event.getPackageName() == null) continue;

                String packageName = event.getPackageName();
                AppUsageData appUsageData = usageDataMap.get(packageName);
                
                if (appUsageData == null) {
                    appUsageData = new AppUsageData(
                        packageName,
                        getAppName(packageName),
                        0,
                        event.getTimeStamp()
                    );
                    usageDataMap.put(packageName, appUsageData);
                }

                // Update usage data based on event type
                switch (event.getEventType()) {
                    case UsageEvents.Event.MOVE_TO_FOREGROUND:
                        appUsageData.setLaunchCount(appUsageData.getLaunchCount() + 1);
                        appUsageData.setLastForegroundTime(event.getTimeStamp());
                        appUsageData.setForeground(true);
                        break;
                        
                    case UsageEvents.Event.MOVE_TO_BACKGROUND:
                        if (appUsageData.isForeground()) {
                            long duration = event.getTimeStamp() - appUsageData.getLastForegroundTime();
                            appUsageData.setUsageDuration(appUsageData.getUsageDuration() + duration);
                            appUsageData.setForeground(false);
                        }
                        break;
                }
                appUsageData.setLastTimeUsed(event.getTimeStamp());
            }

            // Upload all collected usage data
            for (AppUsageData usageData : usageDataMap.values()) {
                if (usageData.getUsageDuration() > 0 || usageData.getLaunchCount() > 0) {
                    Log.d(TAG, "Uploading usage data for " + usageData.getPackageName() + 
                          " - Duration: " + usageData.getUsageDuration() + 
                          " - Launches: " + usageData.getLaunchCount());
                    databaseHelper.uploadAppUsageDataByDate(userId, phoneModel, usageData);
                }
            }

            // Pass all necessary parameters to the service
            Intent serviceIntent = new Intent(context, AppUsageService.class);
            serviceIntent.putExtra("userId", userId);
            serviceIntent.putExtra("phoneModel", phoneModel);
            serviceIntent.putExtra("startTime", startTime);
            serviceIntent.putExtra("endTime", endTime);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }

            Log.d(TAG, "Successfully processed usage events from " + startTime + " to " + endTime);
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "Error processing usage events", e);
            return Result.retry();
        }
    }

    private String getAppName(String packageName) {
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            return pm.getApplicationLabel(ai).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }

    public static void stopMonitoring(Context context) {
        Log.d(TAG, "Stopping usage tracking worker");
        WorkManager.getInstance(context).cancelAllWorkByTag("usage_tracking");
    }
}