package com.childmonitorai;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
public class MonitoringService extends Service {
    private static final String TAG = "MonitoringService";
    private static final String CHANNEL_ID = "MonitoringServiceChannel";

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");

        // Check if required permissions and accessibility service are granted
        if (!PermissionHelper.isForegroundServicePermissionGranted(this) ||
                !PermissionHelper.isLocationPermissionGranted(this) ||
                !PermissionHelper.areCorePermissionsGranted(this)) {
            Log.e(TAG, "Required permissions or accessibility service not granted. Stopping service.");
            showPermissionActivity(); // Start PermissionActivity to request permissions
            return START_NOT_STICKY;
        }

        createNotificationChannel();

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Child Monitoring Service")
                .setContentText("Monitoring location in the background.")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        startForeground(1, notification);

        startMonitoringService(); // Start monitoring tasks
        return START_STICKY;
    }

    private void startMonitoringService() {
        // Start foreground service with notification
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Child Monitoring Service")
                .setContentText("Monitoring calls, SMS, and location in the background.")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        startForeground(1, notification);

        try {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            if (auth.getCurrentUser() == null) {
                Log.e(TAG, "No user logged in.");
                stopSelf(); // Stop service if no user is logged in
                return;
            }

            String userId = auth.getCurrentUser().getUid();
            String phoneModel = android.os.Build.MODEL;

            // Add null checks before starting monitors
            if (userId == null || userId.isEmpty()) {
                Log.e(TAG, "User ID is null or empty.");
                stopSelf();
                return;
            }

            if (phoneModel == null || phoneModel.isEmpty()) {
                Log.e(TAG, "Phone model is null or empty.");
                stopSelf();
                return;
            }

            // Start monitoring components
            startSMSMonitor(userId, phoneModel);
            startCallMonitor(userId, phoneModel);
            startLocationMonitor(userId, phoneModel);
            startMMSMonitor(userId, phoneModel);
            startContactMonitor(userId, phoneModel);
            startAppMonitor(userId, phoneModel);
            startWebMonitor(userId, phoneModel);

        } catch (Exception e) {
            Log.e(TAG, "Error starting monitors: " + e.getMessage());
        }
    }

    private void startWebMonitor(String userId, String phoneModel) {
        Log.d(TAG, "Initializing Web Monitor");

        // Initialize the WebMonitor service
        Intent serviceIntent = new Intent(this, WebMonitor.class); // Use 'this' if inside Activity or Service
        serviceIntent.putExtra("userId", userId);
        serviceIntent.putExtra("phoneModel", phoneModel);

        // Start the service
        startService(serviceIntent);
    }

    private void startSMSMonitor(String userId, String phoneModel) {
        Log.d(TAG, "Initializing SMS Monitor");
        SMSMonitor smsMonitor = new SMSMonitor(this, userId, phoneModel);
        smsMonitor.startMonitoring();
    }

    private void startCallMonitor(String userId, String phoneModel) {
        Log.d(TAG, "Initializing Call Monitor");
        CallMonitor callMonitor = new CallMonitor(this, userId, phoneModel);
        callMonitor.startMonitoring();
    }

    private void startLocationMonitor(String userId, String phoneModel) {
        Log.d(TAG, "Initializing Location Monitor");
        LocationMonitor locationMonitor = new LocationMonitor(this, userId, phoneModel);
        locationMonitor.startMonitoring();
    }

    private void startMMSMonitor(String userId, String phoneModel) {
        Log.d(TAG, "Initializing MMS Monitor");
        MMSMonitor mmsMonitor = new MMSMonitor(this, userId, phoneModel);
        mmsMonitor.startMonitoring();
    }

    private void startContactMonitor(String userId, String phoneModel) {
        Log.d(TAG, "Initializing Contact Monitor");
        ContactMonitor contactMonitor = new ContactMonitor(this, userId, phoneModel);
        contactMonitor.startMonitoring();
    }

    private void startAppMonitor(String userId, String phoneModel) {
        Log.d(TAG, "Initializing App Monitor");
        AppMonitor appMonitor = new AppMonitor(this, userId, phoneModel);
        appMonitor.startMonitoring();
    }


    private void startAppUsageMonitor(String userId, String phoneModel) {
        Log.d(TAG, "Initializing App Usage Monitor");

        // Initialize the AppUsageMonitor service
        Intent serviceIntent = new Intent(this, AppUsageService.class); // Use 'this' if inside Activity or Service
        serviceIntent.putExtra("userId", userId);
        serviceIntent.putExtra("phoneModel", phoneModel);

        // Start the service
        startService(serviceIntent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Monitoring Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Service for monitoring calls, SMS, and location");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void showPermissionActivity() {
        // Start PermissionActivity to request necessary permissions
        Intent intent = new Intent(this, PermissionActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    // Method to check if Accessibility Service is enabled
    private boolean isAccessibilityServiceEnabled(Class<? extends AccessibilityService> serviceClass) {
        String service = getPackageName() + "/" + serviceClass.getName();
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES));

        while (splitter.hasNext()) {
            String enabledService = splitter.next();
            if (enabledService.equalsIgnoreCase(service)) {
                return true;
            }
        }
        return false;
    }
}
