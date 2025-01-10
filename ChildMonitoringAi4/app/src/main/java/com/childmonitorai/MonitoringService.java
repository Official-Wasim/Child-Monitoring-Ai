package com.childmonitorai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;

public class MonitoringService extends Service {
    private static final String TAG = "MonitoringService";
    private static final String CHANNEL_ID = "MonitoringServiceChannel";
    private CommandListener commandListener; // CommandListener initialization


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");

        // Check if required permissions and accessibility service are granted
//        if (!PermissionHelper.isForegroundServicePermissionGranted(this) ||
//                !PermissionHelper.isLocationPermissionGranted(this) ||
//                !PermissionHelper.areCorePermissionsGranted(this)) {
//            Log.e(TAG, "Required permissions or accessibility service not granted. Stopping service.");
//            showPermissionActivity(); // Start PermissionActivity to request permissions
//            return START_NOT_STICKY;
//        }

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
        Log.d(TAG, "Starting monitoring service");

        try {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            if (auth.getCurrentUser() == null) {
                Log.e(TAG, "No user logged in.");
                stopSelf(); // Stop service if no user is logged in
                return;
            }

            String userId = auth.getCurrentUser().getUid();
            String phoneModel = android.os.Build.MODEL;

            // Null checks
            if (TextUtils.isEmpty(userId)) {
                Log.e(TAG, "User ID is null or empty.");
                stopSelf();
                return;
            }

            if (TextUtils.isEmpty(phoneModel)) {
                Log.e(TAG, "Phone model is null or empty.");
                stopSelf();
                return;
            }

            // Start monitoring components
            //startSMSMonitor(userId, phoneModel);
            //startCallMonitor(userId, phoneModel);
            startLocationMonitor(userId, phoneModel);
            //startMMSMonitor(userId, phoneModel);
            //startContactMonitor(userId, phoneModel);
            //startAppMonitor(userId, phoneModel);
            startWebMonitor(userId, phoneModel);
            //startAppUsageMonitor(userId, phoneModel);
            //startClipboardMonitor(userId, phoneModel); // Added Clipboard Monitor
            initializeCommandListener(userId, phoneModel); // Initialize CommandListener


        } catch (Exception e) {
            Log.e(TAG, "Error starting monitors: " + e.getMessage());
        }
    }

    private void startClipboardMonitor(String userId, String phoneModel) {
        Log.d(TAG, "Initializing Clipboard Monitor");
        ClipboardMonitor clipboardMonitor = new ClipboardMonitor(this, userId, phoneModel);
        clipboardMonitor.startMonitoring(); // Start monitoring clipboard content
    }

    private void startWebMonitor(String userId, String phoneModel) {
        Log.d(TAG, "Initializing Web Monitor");

        Intent serviceIntent = new Intent(this, WebMonitor.class);
        serviceIntent.putExtra("userId", userId);
        serviceIntent.putExtra("phoneModel", phoneModel);

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

        Intent serviceIntent = new Intent(this, AppUsageService.class);
        serviceIntent.putExtra("userId", userId);
        serviceIntent.putExtra("phoneModel", phoneModel);

        startService(serviceIntent);
    }

    private void initializeCommandListener(String userId, String phoneModel) {
        Log.d(TAG, "Initializing Command Listener");
        commandListener = new CommandListener(userId, phoneModel, this); // Pass the context
        commandListener.startListeningForCommands(); // Start listening for commands
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
        Intent intent = new Intent(this, PermissionActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
}