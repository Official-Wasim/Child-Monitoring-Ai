package com.childmonitorai;

import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.util.Log;
import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AppUsageService extends Service {
    private static final String TAG = "AppUsageService";
    private String userId;
    private String phoneModel;
    private DatabaseHelper databaseHelper;
    private ScheduledExecutorService scheduler;
    private UsageStatsManager usageStatsManager;
    private Map<String, AppUsageData> appUsageMap;

    @Override
    public void onCreate() {
        super.onCreate();
        databaseHelper = new DatabaseHelper();
        scheduler = Executors.newScheduledThreadPool(1);
        usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        appUsageMap = new HashMap<>();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            userId = intent.getStringExtra("userId");
            phoneModel = intent.getStringExtra("phoneModel");
            
            Log.d(TAG, "Service started for user: " + userId + ", device: " + phoneModel);
            
            // Start as foreground service
            startForeground(2, createNotification());
            
            if (checkUsageStatsPermission()) {
                Log.d(TAG, "Usage stats permission granted, starting tracking");
                startTracking();
            } else {
                Log.e(TAG, "Usage stats permission not granted");
                stopSelf();
            }
        } else {
            Log.e(TAG, "Service started with null intent");
        }
        return START_STICKY;
    }

    private Notification createNotification() {
        String CHANNEL_ID = "AppUsageServiceChannel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "App Usage Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("App Usage Monitoring")
                .setContentText("Monitoring app usage")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void startTracking() {
        Log.d(TAG, "Starting usage tracking scheduler");
        scheduler.scheduleAtFixedRate(() -> {
            try {
                long endTime = System.currentTimeMillis();
                long startTime = endTime - (60 * 1000); // Last minute
                Log.d(TAG, "Querying usage stats from " + startTime + " to " + endTime);

                UsageEvents events = usageStatsManager.queryEvents(startTime, endTime);
                if (events == null) {
                    Log.e(TAG, "Failed to query usage events");
                    return;
                }

                processUsageEvents(events);
                uploadUsageData();
                
            } catch (Exception e) {
                Log.e(TAG, "Error in usage tracking: " + e.getMessage(), e);
            }
        }, 0, 1, TimeUnit.MINUTES);
    }

    private void processUsageEvents(UsageEvents events) {
        UsageEvents.Event event = new UsageEvents.Event();
        Map<String, Long> lastEventTimeMap = new HashMap<>();
        int eventCount = 0;

        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            eventCount++;
            
            String packageName = event.getPackageName();
            if (isSystemApp(packageName)) {
                Log.v(TAG, "Skipping system app: " + packageName);
                continue;
            }

            AppUsageData usageData = appUsageMap.computeIfAbsent(packageName, k -> {
                String appName = getAppName(k);
                Log.d(TAG, "New app detected: " + appName + " (" + k + ")");
                return new AppUsageData(k, appName, 0, System.currentTimeMillis());
            });

            // Track last event time to prevent duplicate events
            Long lastEventTime = lastEventTimeMap.get(packageName);
            if (lastEventTime != null && event.getTimeStamp() <= lastEventTime) {
                Log.v(TAG, "Skipping duplicate event for: " + packageName);
                continue;
            }
            lastEventTimeMap.put(packageName, event.getTimeStamp());

            handleUsageEvent(event, usageData);
        }
        Log.d(TAG, "Processed " + eventCount + " usage events");
    }

    private void handleUsageEvent(UsageEvents.Event event, AppUsageData usageData) {
        long eventTime = event.getTimeStamp();
        String packageName = usageData.getPackageName();
        
        switch (event.getEventType()) {
            case UsageEvents.Event.MOVE_TO_FOREGROUND:
                if (!usageData.isForeground()) {
                    usageData.setForeground(true);
                    usageData.setLastForegroundTime(eventTime);
                    usageData.setLaunchCount(usageData.getLaunchCount() + 1);
                    usageData.setLastTimeUsed(eventTime);
                    Log.i(TAG, String.format("App launched: %s (%s) at %d", 
                        usageData.getAppName(), packageName, eventTime));
                }
                break;

            case UsageEvents.Event.MOVE_TO_BACKGROUND:
                if (usageData.isForeground()) {
                    long duration = eventTime - usageData.getLastForegroundTime();
                    if (duration > 0) {
                        usageData.setUsageDuration(usageData.getUsageDuration() + duration);
                        Log.i(TAG, String.format("App session ended: %s (%s), duration: %d ms", 
                            usageData.getAppName(), packageName, duration));
                    }
                    usageData.setForeground(false);
                }
                break;
        }
    }

    private boolean checkUsageStatsPermission() {
        try {
            PackageManager packageManager = getPackageManager();
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo(getPackageName(), 0);
            AppOpsManager appOpsManager = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            int mode = appOpsManager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    applicationInfo.uid, applicationInfo.packageName);
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            Log.e(TAG, "Error checking usage stats permission: " + e.getMessage());
            return false;
        }
    }

    private boolean isSystemApp(String packageName) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            return (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void uploadUsageData() {
        Log.d(TAG, "Starting usage data upload");
        long currentTime = System.currentTimeMillis();
        int uploadCount = 0;

        for (AppUsageData usageData : appUsageMap.values()) {
            // Update duration for apps still in foreground
            if (usageData.isForeground()) {
                long additionalDuration = currentTime - usageData.getLastForegroundTime();
                usageData.setUsageDuration(usageData.getUsageDuration() + additionalDuration);
                usageData.setLastForegroundTime(currentTime);
                Log.d(TAG, String.format("Updated ongoing session for %s: +%d ms",
                    usageData.getAppName(), additionalDuration));
            }

            // Only upload if there's actual usage
            if (usageData.getUsageDuration() > 0 || usageData.getLaunchCount() > 0) {
                try {
                    databaseHelper.uploadAppUsageDataByDate(userId, phoneModel, usageData);
                    Log.i(TAG, String.format("Uploaded usage for %s (%s): duration=%d ms, launches=%d",
                        usageData.getAppName(),
                        usageData.getPackageName(), 
                        usageData.getUsageDuration(),
                        usageData.getLaunchCount()));
                    uploadCount++;
                } catch (Exception e) {
                    Log.e(TAG, "Failed to upload usage data for " + usageData.getPackageName(), e);
                }
            }
        }
        Log.d(TAG, "Upload complete: processed " + uploadCount + " apps");
        appUsageMap.clear();
    }

    private String getAppName(String packageName) {
        try {
            PackageManager packageManager = getPackageManager();
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo(packageName, 0);
            return packageManager.getApplicationLabel(applicationInfo).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }
}
