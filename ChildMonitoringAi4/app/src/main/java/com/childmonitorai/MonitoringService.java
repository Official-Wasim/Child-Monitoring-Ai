package com.childmonitorai;

import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import java.net.URISyntaxException;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.firebase.auth.FirebaseAuth;

import java.util.concurrent.TimeUnit;

public class MonitoringService extends Service {
    private static final String TAG = "MonitoringService";
    private static final String CHANNEL_ID = "MonitoringServiceChannel";
    private CommandListener commandListener; 
    private CommandExecutor commandExecutor;
    private String userId;
    private String phoneModel; 
    private GeoFenceMonitor geoFenceMonitor;
    private AppMonitor appMonitor;  
    private PhotosMonitor photosMonitor;  

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Get user ID from Firebase Auth
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            userId = auth.getCurrentUser().getUid();
            phoneModel = android.os.Build.MODEL;
        }
        
        // Start foreground service first
        createNotificationChannel();
        startForeground();
        
        initCommandExecutor();
        startMonitoringService();
        return START_STICKY;
    }

    private void startForeground() {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Child Monitoring Service")
                .setContentText("Monitoring active")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        startForeground(1, notification);
    }

    private void startMonitoringService() {
        Log.d(TAG, "Starting monitoring service");

        try {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            if (auth.getCurrentUser() == null) {
                Log.e(TAG, "No user logged in.");
                stopSelf();
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
            startSMSMonitor(userId, phoneModel);
            startCallMonitor(userId, phoneModel);
            startLocationMonitor(userId, phoneModel);
            startMMSMonitor(userId, phoneModel);
            startContactMonitor(userId, phoneModel);
            startAppMonitor(userId, phoneModel);
            startWebMonitor(userId, phoneModel);
            startClipboardMonitor(userId, phoneModel);
            startAppUsageMonitor(userId, phoneModel);
            startGeofenceMonitor();
            startPhotosMonitor(); 
            initializeCommandListener(userId, phoneModel);
        } catch (Exception e) {
            Log.e(TAG, "Error starting monitors: " + e.getMessage());
        }
    }

    private void startClipboardMonitor(String userId, String phoneModel) {
        Log.d(TAG, "Initializing Clipboard Monitor");
        ClipboardMonitor clipboardMonitor = new ClipboardMonitor(this, userId, phoneModel);
        clipboardMonitor.startMonitoring(); 
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
        try {
            appMonitor = new AppMonitor(this, userId, phoneModel);
            appMonitor.startMonitoring();
            Log.i(TAG, "App Monitor initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize App Monitor: " + e.getMessage());
        }
    }

    private void startAppUsageMonitor(String userId, String phoneModel) {
        Log.d(TAG, "Initializing App Usage Monitor");

        // Check for usage stats permission
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getPackageName());

        if (mode != AppOpsManager.MODE_ALLOWED) {
            Log.w(TAG, "Usage stats permission not granted");
            // You might want to handle this case, perhaps by showing a permission request
            return;
        }

        // Create Data object with required parameters
        Data inputData = new Data.Builder()
                .putString("userId", userId)
                .putString("phoneModel", phoneModel)
                .build();

        // Schedule periodic work using WorkManager with input data
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest usageWorkRequest = 
            new PeriodicWorkRequest.Builder(UsageTrackingWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setInputData(inputData)  
                .addTag("usage_tracking")
                .build();

        WorkManager.getInstance(this)
                .enqueueUniquePeriodicWork(
                    "usage_tracking_" + userId,  // Make work request unique per user
                    ExistingPeriodicWorkPolicy.UPDATE,  // Update existing work if any
                    usageWorkRequest
                );

        // Start the AppUsageService with the same parameters
        Intent serviceIntent = new Intent(this, AppUsageService.class);
        serviceIntent.putExtra("userId", userId);
        serviceIntent.putExtra("phoneModel", phoneModel);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        Log.d(TAG, "App Usage Monitor initialized successfully");
    }

    private void startGeofenceMonitor() {
        Log.d(TAG, "Initializing Geofence Monitor");
        geoFenceMonitor = new GeoFenceMonitor(this);
        if (geoFenceMonitor.hasRequiredPermissions()) {
            geoFenceMonitor.startGeofencing();
        } else {
            Log.w(TAG, "Missing permissions for geofencing");
            showPermissionActivity();
        }
    }

    private void startPhotosMonitor() {
        Log.d(TAG, "Initializing Photos Monitor");
        try {
            photosMonitor = new PhotosMonitor(this);
            Log.i(TAG, "Photos Monitor initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Photos Monitor: " + e.getMessage());
        }
    }

    private void initializeCommandListener(String userId, String phoneModel) {
        Log.d(TAG, "Initializing Command Listener");
        commandExecutor = new CommandExecutor(userId, phoneModel, this);
        commandListener = new CommandListener(userId, phoneModel, this);
        commandListener.setCommandExecutor(commandExecutor);
        commandListener.startListeningForCommands();
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

    private void initCommandExecutor() {
        if (userId == null || phoneModel == null) {
            Log.e(TAG, "Cannot initialize CommandExecutor - missing userId or deviceId");
            return;
        }
        
        // Get application context to ensure we have a valid context
        Context appContext = getApplicationContext();
        commandExecutor = new CommandExecutor(userId, phoneModel, appContext);
    }
}