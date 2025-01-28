package com.childmonitorai;
import com.childmonitorai.models.AppData;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.util.Log;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class AppMonitor {
    private static final String TAG = "AppMonitor";
    private static final int BATCH_SIZE = 10;
    private final String userId;
    private final String phoneModel;
    private final Context context;
    private final DatabaseHelper databaseHelper;
    private final ExecutorService executorService;

    public AppMonitor(Context context, String userId, String phoneModel) {
        this.context = context;
        this.userId = userId;
        this.phoneModel = phoneModel;
        this.databaseHelper = new DatabaseHelper();
        this.executorService = Executors.newSingleThreadExecutor();
    }

    public void startMonitoring() {
        // Register a receiver to listen for app installation/uninstallation
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme("package");
        context.registerReceiver(appInstallReceiver, filter);

        // Upload the currently installed apps at startup
        uploadInstalledApps();
    }

    public void stopMonitoring() {
        try {
            context.unregisterReceiver(appInstallReceiver);
            executorService.shutdown();
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "Error shutting down executor: " + e.getMessage());
        }
    }

    private void uploadInstalledApps() {
        PackageManager packageManager = context.getPackageManager();
        List<ApplicationInfo> apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);
        List<AppData> batch = new ArrayList<>();

        for (ApplicationInfo app : apps) {
            String appName = app.loadLabel(packageManager).toString();
            String packageName = app.packageName;

            try {
                PackageInfo packageInfo = packageManager.getPackageInfo(packageName, 0);
                long appSize = new java.io.File(packageInfo.applicationInfo.sourceDir).length();
                String version = packageInfo.versionName;

                AppData appData = new AppData(appName, packageName, System.currentTimeMillis(), "installed", appSize, version);
                appData.setCategory(getAppCategory(app));
                batch.add(appData);

                if (batch.size() >= BATCH_SIZE) {
                    uploadBatch(new ArrayList<>(batch));
                    batch.clear();
                }
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "Error fetching app info for package " + packageName + ": " + e.getMessage());
            }
        }

        if (!batch.isEmpty()) {
            uploadBatch(batch);
        }
    }

    private void uploadBatch(List<AppData> batch) {
        executorService.submit(() -> {
            for (AppData appData : batch) {
                String uniqueKey = databaseHelper.sanitizePath(appData.getPackageName());
                databaseHelper.uploadAppData(userId, phoneModel, uniqueKey, appData.toMap());
            }
        });
    }

    private String getAppCategory(ApplicationInfo app) {
        if ((app.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
            return "SYSTEM";
        } else if ((app.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0) {
            return "UPDATED_SYSTEM";
        } else {
            return "USER_INSTALLED";
        }
    }

    private void uploadAppData(final AppData appData) {
        // Make AsyncTask static and pass necessary context through constructor
        new AppDataUploadTask(userId, phoneModel, databaseHelper).execute(appData);
    }

    // Static AsyncTask class to prevent memory leaks
    private static class AppDataUploadTask extends AsyncTask<AppData, Void, Void> {
        private final String userId;
        private final String phoneModel;
        private final DatabaseHelper databaseHelper;

        AppDataUploadTask(String userId, String phoneModel, DatabaseHelper databaseHelper) {
            this.userId = userId;
            this.phoneModel = phoneModel;
            this.databaseHelper = databaseHelper;
        }

        @Override
        protected Void doInBackground(AppData... appDataArray) {
            if (appDataArray.length > 0) {
                AppData data = appDataArray[0];
                String uniqueKey = databaseHelper.sanitizePath(data.getPackageName());
                databaseHelper.uploadAppData(userId, phoneModel, uniqueKey, data.toMap());
            }
            return null;
        }
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
