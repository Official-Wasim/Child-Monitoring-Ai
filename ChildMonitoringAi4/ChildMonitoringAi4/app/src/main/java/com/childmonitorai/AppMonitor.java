package com.childmonitorai;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.util.Log;

import com.google.firebase.database.DatabaseReference;

import java.util.List;

public class AppMonitor {
    private static final String TAG = "AppMonitor";
    private final String userId;
    private final String phoneModel;
    private final Context context;
    private final DatabaseHelper databaseHelper;

    public AppMonitor(Context context, String userId, String phoneModel) {
        this.context = context;
        this.userId = userId;
        this.phoneModel = phoneModel;
        this.databaseHelper = new DatabaseHelper();
    }

    public void startMonitoring() {
        // Register a receiver to listen for app installation/uninstallation
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme("package");
        context.registerReceiver(appInstallReceiver, filter);

        // Upload the currently installed apps at the time of startup
        uploadInstalledApps();
    }

    public void stopMonitoring() {
        context.unregisterReceiver(appInstallReceiver);
    }

    private void uploadInstalledApps() {
        PackageManager packageManager = context.getPackageManager();
        List<ApplicationInfo> apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);

        for (ApplicationInfo app : apps) {
            String appName = app.loadLabel(packageManager).toString();
            String packageName = app.packageName;

            try {
                PackageInfo packageInfo = packageManager.getPackageInfo(packageName, 0);
                long appSize = new java.io.File(packageInfo.applicationInfo.sourceDir).length(); // App size in bytes
                String version = packageInfo.versionName; // App version

                // Create an AppData object with installation status
                AppData appData = new AppData(appName, packageName, System.currentTimeMillis(), "installed", appSize, version);

                // Upload the app data using the sanitized package name as the unique key
                uploadAppData(appData);
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "Error fetching app info for package " + packageName + ": " + e.getMessage());
            }
        }
    }

    private void uploadAppData(AppData appData) {
        // Use AsyncTask to handle Firebase upload in the background
        new AsyncTask<AppData, Void, Void>() {
            @Override
            protected Void doInBackground(AppData... appDataArray) {
                if (appDataArray.length > 0) {
                    AppData data = appDataArray[0];
                    String uniqueKey = databaseHelper.sanitizePath(data.getPackageName()); // Use the sanitized package name as the unique key
                    databaseHelper.uploadAppData(userId, phoneModel, uniqueKey, data.toMap()); // Pass the map representation of appData
                }
                return null;
            }
        }.execute(appData);
    }

    private final BroadcastReceiver appInstallReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action != null) {
                String packageName = intent.getData().getSchemeSpecificPart();
                String appName = getAppNameFromPackage(packageName);

                try {
                    PackageInfo packageInfo = context.getPackageManager().getPackageInfo(packageName, 0);
                    long appSize = new java.io.File(packageInfo.applicationInfo.sourceDir).length();
                    String version = packageInfo.versionName;

                    if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {
                        AppData appData = new AppData(appName, packageName, System.currentTimeMillis(), "installed", appSize, version);
                        uploadAppData(appData);
                    } else if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
                        AppData appData = new AppData(appName, packageName, System.currentTimeMillis(), "uninstalled", 0, "N/A");
                        uploadAppData(appData);
                    }
                } catch (PackageManager.NameNotFoundException e) {
                    Log.e(TAG, "Error fetching app info for package " + packageName + ": " + e.getMessage());
                }
            }
        }
    };

    private String getAppNameFromPackage(String packageName) {
        PackageManager packageManager = context.getPackageManager();
        try {
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo(packageName, 0);
            return (String) packageManager.getApplicationLabel(applicationInfo);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "App name not found for package: " + packageName);
            return "Unknown";
        }
    }
}
