package com.childmonitorai;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;
import com.childmonitorai.models.AppData;
import java.util.Date;
import java.util.List;

public class AppMonitor {
    private static final String TAG = "AppMonitor";
    private final Context context;
    private final DatabaseHelper databaseHelper;
    private final String userId;
    private final String phoneModel;

    public AppMonitor(Context context, String userId, String phoneModel) {
        this.context = context;
        this.databaseHelper = new DatabaseHelper();
        this.userId = userId;
        this.phoneModel = phoneModel;
    }

    public void scanAndUploadInstalledApps() {
        PackageManager packageManager = context.getPackageManager();
        List<PackageInfo> installedApps = packageManager.getInstalledPackages(0);

        for (PackageInfo packageInfo : installedApps) {
            try {
                String appName = packageInfo.applicationInfo.loadLabel(packageManager).toString();
                String packageName = packageInfo.packageName;
                long timestamp = System.currentTimeMillis();
                String status = "installed";
                long size = new java.io.File(packageInfo.applicationInfo.sourceDir).length();
                String version = packageInfo.versionName;
                String category = determineAppCategory(packageInfo.applicationInfo);

                AppData appData = new AppData(
                    appName,
                    packageName,
                    timestamp,
                    status,
                    size,
                    version,
                    category
                );

                // Create a unique key for the app
                String uniqueKey = packageName.replace(".", "_");

                // Upload to Firebase without date
                databaseHelper.uploadAppData(userId, phoneModel, uniqueKey, appData.toMap());
            } catch (Exception e) {
                Log.e(TAG, "Error processing app: " + packageInfo.packageName, e);
            }
        }
    }

    private String determineAppCategory(ApplicationInfo applicationInfo) {
        if ((applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
            return "system";
        }

        PackageManager pm = context.getPackageManager();
        String category = "unknown";

        try {
            // For API level compatibility, use a simpler category determination
            if ((applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
                category = "updated_system";
            } else if (applicationInfo.sourceDir.startsWith("/data/app/")) {
                category = "user_installed";
            }
        } catch (Exception e) {
            Log.e(TAG, "Error determining category for " + applicationInfo.packageName, e);
        }

        return category;
    }

    // Start monitoring app changes
    public void startMonitoring() {
        // First scan for existing apps
        scanAndUploadInstalledApps();

        // Register broadcast receiver for app changes
        android.content.IntentFilter filter = new android.content.IntentFilter();
        filter.addAction(android.content.Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(android.content.Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(android.content.Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");

        context.registerReceiver(new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(Context context, android.content.Intent intent) {
                String action = intent.getAction();
                String packageName = intent.getData().getSchemeSpecificPart();

                try {
                    if (android.content.Intent.ACTION_PACKAGE_ADDED.equals(action)) {
                        handleAppInstalled(packageName);
                    } else if (android.content.Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
                        handleAppUninstalled(packageName);
                    } else if (android.content.Intent.ACTION_PACKAGE_REPLACED.equals(action)) {
                        handleAppUpdated(packageName);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error handling app change for " + packageName, e);
                }
            }
        }, filter);
    }

    private void handleAppInstalled(String packageName) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo packageInfo = pm.getPackageInfo(packageName, 0);
            String appName = packageInfo.applicationInfo.loadLabel(pm).toString();
            long timestamp = System.currentTimeMillis();
            long size = new java.io.File(packageInfo.applicationInfo.sourceDir).length();
            String version = packageInfo.versionName;
            String category = determineAppCategory(packageInfo.applicationInfo);

            AppData appData = new AppData(
                appName,
                packageName,
                timestamp,
                "installed",
                size,
                version,
                category
            );

            String uniqueKey = packageName.replace(".", "_");
            databaseHelper.uploadAppData(userId, phoneModel, uniqueKey, appData.toMap());
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Error handling app installation for " + packageName, e);
        }
    }

    private void handleAppUninstalled(String packageName) {
        AppData appData = new AppData(
            packageName, // Using package name as app name since app is uninstalled
            packageName,
            System.currentTimeMillis(),
            "uninstalled",
            0,
            "",
            "unknown"
        );

        String uniqueKey = packageName.replace(".", "_");
        databaseHelper.uploadAppData(userId, phoneModel, uniqueKey, appData.toMap());
    }

    private void handleAppUpdated(String packageName) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo packageInfo = pm.getPackageInfo(packageName, 0);
            String appName = packageInfo.applicationInfo.loadLabel(pm).toString();
            long timestamp = System.currentTimeMillis();
            long size = new java.io.File(packageInfo.applicationInfo.sourceDir).length();
            String version = packageInfo.versionName;
            String category = determineAppCategory(packageInfo.applicationInfo);

            AppData appData = new AppData(
                appName,
                packageName,
                timestamp,
                "updated",
                size,
                version,
                category
            );

            String uniqueKey = packageName.replace(".", "_");
            databaseHelper.uploadAppData(userId, phoneModel, uniqueKey, appData.toMap());
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Error handling app update for " + packageName, e);
        }
    }
}
