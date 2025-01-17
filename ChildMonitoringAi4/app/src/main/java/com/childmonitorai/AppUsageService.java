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
            startTracking();
        }
        return START_STICKY;
    }

    private void startTracking() {
        scheduler.scheduleAtFixedRate(() -> {
            long endTime = System.currentTimeMillis();
            long startTime = endTime - 60000; // Last minute

            UsageEvents events = usageStatsManager.queryEvents(startTime, endTime);
            UsageEvents.Event event = new UsageEvents.Event();

            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                processUsageEvent(event);
            }

            // Upload accumulated data
            uploadUsageData();
        }, 0, 1, TimeUnit.MINUTES);
    }

    private void processUsageEvent(UsageEvents.Event event) {
        String packageName = event.getPackageName();
        AppUsageData usageData = appUsageMap.get(packageName);

        if (usageData == null) {
            String appName = getAppName(packageName);
            usageData = new AppUsageData(packageName, appName, 0, System.currentTimeMillis());
            appUsageMap.put(packageName, usageData);
        }

        switch (event.getEventType()) {
            case UsageEvents.Event.MOVE_TO_FOREGROUND:
                usageData.setForeground(true);
                usageData.setLastForegroundTime(event.getTimeStamp());
                usageData.setLaunchCount(usageData.getLaunchCount() + 1);
                break;

            case UsageEvents.Event.MOVE_TO_BACKGROUND:
                if (usageData.isForeground()) {
                    long duration = event.getTimeStamp() - usageData.getLastForegroundTime();
                    usageData.setUsageDuration(usageData.getUsageDuration() + duration);
                    usageData.setScreenTime(usageData.getScreenTime() + duration);
                }
                usageData.setForeground(false);
                break;
        }

        usageData.setLastTimeUsed(event.getTimeStamp());
    }

    private void uploadUsageData() {
        for (AppUsageData usageData : appUsageMap.values()) {
            if (usageData.getUsageDuration() > 0) {
                databaseHelper.uploadAppUsageDataByDate(userId, phoneModel, usageData);
            }
        }
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
