package com.childmonitorai;

import android.app.Service;
import android.app.usage.UsageEvents;
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
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.childmonitorai.models.AppUsageData;
import com.childmonitorai.models.SessionData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class AppUsageService extends Service {
    private static final String TAG = "AppUsageService";
    private static final long MAX_SESSION_DURATION = TimeUnit.HOURS.toMillis(12);
    private static final long DUPLICATE_EVENT_THRESHOLD = 500; // ms
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private String userId;
    private String phoneModel;
    private DatabaseHelper databaseHelper;
    private UsageStatsManager usageStatsManager;
    private Map<String, AppUsageData> appUsageMap;
    private Map<String, com.childmonitorai.models.SessionData> activeSessions;
    private volatile boolean isServiceRunning;

    @Override
    public void onCreate() {
        super.onCreate();
        databaseHelper = new DatabaseHelper();
        usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        appUsageMap = new ConcurrentHashMap<>();
        activeSessions = new ConcurrentHashMap<>();
        isServiceRunning = false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            Log.e(TAG, "Service started with null intent");
            return START_NOT_STICKY;
        }

        userId = intent.getStringExtra("userId");
        phoneModel = intent.getStringExtra("phoneModel");
        
        if (userId == null || phoneModel == null) {
            Log.e(TAG, "Missing required parameters");
            stopSelf();
            return START_NOT_STICKY;
        }

        Log.d(TAG, "Service started for user: " + userId + ", device: " + phoneModel);
        
        startForeground(2, createNotification());
        
        if (!checkUsageStatsPermission()) {
            Log.e(TAG, "Usage stats permission not granted");
            stopSelf();
            return START_NOT_STICKY;
        }

        // Schedule periodic work using WorkManager
        scheduleUsageTracking();
        
        isServiceRunning = true;
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

    private void scheduleUsageTracking() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest trackingWork = 
            new PeriodicWorkRequest.Builder(UsageTrackingWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setInitialDelay(1, TimeUnit.MINUTES)
                .addTag("usage_tracking")
                .build();

        WorkManager.getInstance(this)
                .enqueue(trackingWork);
    }

    private void processUsageEvents(UsageEvents events) {
        if (events == null) return;

        UsageEvents.Event event = new UsageEvents.Event();
        Map<String, Long> lastEventTimeMap = new HashMap<>();
        int eventCount = 0;

        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            eventCount++;
            
            String packageName = event.getPackageName();
            if (isSystemApp(packageName)) continue;

            if (isDuplicateEvent(lastEventTimeMap, event)) continue;

            AppUsageData usageData = getOrCreateAppUsageData(packageName);
            handleUsageEvent(event, usageData);
        }
        
        Log.d(TAG, "Processed " + eventCount + " events");
    }

    private AppUsageData getOrCreateAppUsageData(String packageName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return appUsageMap.computeIfAbsent(packageName, k -> {
                String appName = getAppName(k);
                Log.d(TAG, "New app detected: " + appName + " (" + k + ")");
                return new AppUsageData(k, appName, 0, System.currentTimeMillis());
            });
        }
        // Fallback for older Android versions
        AppUsageData usageData = appUsageMap.get(packageName);
        if (usageData == null) {
            String appName = getAppName(packageName);
            usageData = new AppUsageData(packageName, appName, 0, System.currentTimeMillis());
            appUsageMap.put(packageName, usageData);
            Log.d(TAG, "New app detected: " + appName + " (" + packageName + ")");
        }
        return usageData;
    }

    private boolean isDuplicateEvent(Map<String, Long> lastEventTimeMap, UsageEvents.Event event) {
        String packageName = event.getPackageName();
        long eventTime = event.getTimeStamp();
        Long lastEventTime = lastEventTimeMap.get(packageName);
        
        if (lastEventTime != null && 
            eventTime - lastEventTime < DUPLICATE_EVENT_THRESHOLD) {
            return true;
        }
        
        lastEventTimeMap.put(packageName, eventTime);
        return false;
    }

    private void handleUsageEvent(UsageEvents.Event event, AppUsageData usageData) {
        long eventTime = event.getTimeStamp();
        String packageName = usageData.getPackageName();

        try {
            switch (event.getEventType()) {
                case UsageEvents.Event.MOVE_TO_FOREGROUND:
                    handleForegroundEvent(usageData, eventTime);
                    // Update additional stats
                    usageData.setDayLaunchCount(usageData.getDayLaunchCount() + 1);
                    if (usageData.getFirstTimeUsed() == 0) {
                        usageData.setFirstTimeUsed(eventTime);
                    }
                    usageData.setSystemApp(isSystemApp(packageName));
                    usageData.setCategory(getAppCategory(packageName));
                    break;

                case UsageEvents.Event.MOVE_TO_BACKGROUND:
                    handleBackgroundEvent(usageData, eventTime);
                    // Update total usage time
                    long sessionDuration = eventTime - usageData.getLastForegroundTime();
                    usageData.setDayUsageTime(usageData.getDayUsageTime() + sessionDuration);
                    usageData.setTotalForegroundTime(usageData.getTotalForegroundTime() + sessionDuration);
                    break;
            }
            usageData.setLastUpdateTime(System.currentTimeMillis());
        } catch (Exception e) {
            Log.e(TAG, "Error handling event for " + packageName, e);
        }
    }

    private String getAppCategory(String packageName) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                return "system";
            }
            
            // Check common categories based on permissions and features
            if (pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) &&
                pm.checkPermission("android.permission.CAMERA", packageName) == PackageManager.PERMISSION_GRANTED) {
                return "photography";
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (packageName.contains("game") ||
                    packageName.contains("gaming") ||
                    ai.category == ApplicationInfo.CATEGORY_GAME) {
                    return "game";
                }
            }

            if (packageName.contains("browser") || 
                packageName.contains("chrome") || 
                packageName.contains("firefox") || 
                packageName.contains("opera")) {
                return "browser";
            }
            
            if (packageName.contains("messaging") || 
                packageName.contains("chat") || 
                packageName.contains("comm")) {
                return "communication";
            }
            
            if (packageName.contains("social") || 
                packageName.contains("facebook") || 
                packageName.contains("twitter") || 
                packageName.contains("instagram")) {
                return "social";
            }
            
            if (packageName.contains("music") || 
                packageName.contains("audio") || 
                packageName.contains("player") || 
                packageName.contains("spotify")) {
                return "media";
            }

            return "other";
        } catch (Exception e) {
            Log.e(TAG, "Error determining app category for " + packageName + ": " + e.getMessage());
            return "unknown";
        }
    }

    private void handleForegroundEvent(AppUsageData usageData, long eventTime) {
        if (usageData.isForeground()) {
            handleBackgroundEvent(usageData, eventTime);
        }

        usageData.setForeground(true);
        usageData.setLastForegroundTime(eventTime);
        usageData.setLaunchCount(usageData.getLaunchCount() + 1);
        usageData.setLastTimeUsed(eventTime);

        com.childmonitorai.models.SessionData sessionData = new com.childmonitorai.models.SessionData(
            UUID.randomUUID().toString(),
            usageData.getPackageName(),
            usageData.getAppName(),
            eventTime
        );
        activeSessions.put(usageData.getPackageName(), sessionData);
    }

    private void handleBackgroundEvent(AppUsageData usageData, long eventTime) {
        if (!usageData.isForeground()) {
            return;
        }

        SessionData sessionData = activeSessions.remove(usageData.getPackageName());
        if (sessionData == null) {
            Log.w(TAG, "No active session found for: " + usageData.getPackageName());
            return;
        }

        long duration = eventTime - usageData.getLastForegroundTime();
        if (duration <= 0) {
            Log.w(TAG, "Invalid duration calculated: " + duration);
            return;
        }

        if (duration > MAX_SESSION_DURATION) {
            Log.w(TAG, "Session exceeded maximum duration: " + duration);
            duration = MAX_SESSION_DURATION;
        }

        usageData.setUsageDuration(usageData.getUsageDuration() + duration);
        usageData.setForeground(false);

        sessionData.setEndTime(eventTime);
        sessionData.setDuration(duration);

        uploadSessionData(sessionData);
    }

    private void uploadSessionData(SessionData sessionData) {
        int retryCount = 0;
        boolean uploaded = false;

        while (!uploaded && retryCount < MAX_RETRY_ATTEMPTS) {
            try {
                // Check if the session duration is reasonable
                if (sessionData.getDuration() > 0 && sessionData.getDuration() <= MAX_SESSION_DURATION) {
                    databaseHelper.uploadSessionData(userId, phoneModel, sessionData);
                    uploaded = true;
                    Log.i(TAG, String.format("Uploaded session for %s: duration=%d ms", 
                        sessionData.getAppName(), sessionData.getDuration()));
                } else {
                    Log.w(TAG, String.format("Skipping invalid session duration for %s: %d ms", 
                        sessionData.getAppName(), sessionData.getDuration()));
                    break;
                }
            } catch (Exception e) {
                retryCount++;
                Log.e(TAG, "Upload attempt " + retryCount + " failed", e);
                if (retryCount == MAX_RETRY_ATTEMPTS) {
                    break;
                }
                try {
                    Thread.sleep(1000 * retryCount);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void handleSessionTimeout(SessionData session) {
        long currentTime = System.currentTimeMillis();
        session.setEndTime(currentTime);
        session.setDuration(MAX_SESSION_DURATION);
        session.setTimedOut(true);

        uploadSessionData(session);
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
        isServiceRunning = false;
        super.onDestroy();
    }
}