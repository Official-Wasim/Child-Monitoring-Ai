package com.childmonitorai.services;

import android.app.AppOpsManager;
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
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.childmonitorai.PermissionActivity;
import com.childmonitorai.R;
import com.childmonitorai.commands.CommandExecutor;
import com.childmonitorai.commands.CommandListener;
import com.childmonitorai.helpers.UsageTrackingWorker;
import com.childmonitorai.monitors.AppMonitor;
import com.childmonitorai.monitors.AppUsageService;
import com.childmonitorai.monitors.CallMonitor;
import com.childmonitorai.monitors.ClipboardMonitor;
import com.childmonitorai.monitors.ContactMonitor;
import com.childmonitorai.monitors.GeoFenceMonitor;
import com.childmonitorai.monitors.LocationMonitor;
import com.childmonitorai.monitors.MMSMonitor;
import com.childmonitorai.monitors.OnRefreshStatsMonitor;
import com.childmonitorai.monitors.PhotosMonitor;
import com.childmonitorai.monitors.SMSMonitor;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.childmonitorai.helpers.Preferences;

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
    private OnRefreshStatsMonitor refreshStatsMonitor;
    private CallMonitor callMonitor;  // Add this field
    private SMSMonitor smsMonitor;  // Add this field
    private LocationMonitor locationMonitor;
    private MMSMonitor mmsMonitor;
    private ContactMonitor contactMonitor;
    private ClipboardMonitor clipboardMonitor;
    private WebMonitorService webMonitor;
    private Preferences preferences;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true);
        } catch (Exception e) {
            Log.e(TAG, "Firebase persistence already enabled or other error: " + e.getMessage());
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Initialize preferences first
        try {
            preferences = new Preferences();  // Remove getApplicationContext()
            preferences.setPreferenceChangeListener(this::handlePreferenceChange);  // Use handlePreferenceChange instead
        } catch (Exception e) {
            Log.e(TAG, "Error initializing preferences: " + e.getMessage());
        }

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

            // Initialize all monitors based on current preference values
            if (preferences.isApps()) {
                startAppMonitor(userId, phoneModel);
                startAppUsageMonitor(userId, phoneModel);
            }
            if (preferences.isCall()) {
                startCallMonitor(userId, phoneModel);
            }
            if (preferences.isSms()) {
                startSMSMonitor(userId, phoneModel);
            }
            if (preferences.isLocation()) {
                startLocationMonitor(userId, phoneModel);
            }
            if (preferences.isMms()) {
                startMMSMonitor(userId, phoneModel);
            }
            if (preferences.isContacts()) {
                startContactMonitor(userId, phoneModel);
            }
            if (preferences.isSites()) {
                startWebMonitor(userId, phoneModel);
            }
            
            // These features might need separate preference controls
            startGeofenceMonitor();
            startPhotosMonitor(); 
            initializeCommandListener(userId, phoneModel);
            startRefreshStatsMonitor(userId, phoneModel);

        } catch (Exception e) {
            Log.e(TAG, "Error starting monitors: " + e.getMessage());
        }
    }

    private void startRefreshStatsMonitor(String userId, String phoneModel) {
        Log.d(TAG, "Initializing Refresh Stats Monitor");
        try {
            refreshStatsMonitor = new OnRefreshStatsMonitor(this);
            refreshStatsMonitor.startMonitoring(userId, phoneModel);
            Log.i(TAG, "Refresh Stats Monitor initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Refresh Stats Monitor: " + e.getMessage());
        }
    }

    private void startClipboardMonitor(String userId, String phoneModel) {
        Log.d(TAG, "Initializing Clipboard Monitor");
        clipboardMonitor = new ClipboardMonitor(this, userId, phoneModel);
        clipboardMonitor.startMonitoring(); 
    }

    private void startWebMonitor(String userId, String phoneModel) {
        Log.d(TAG, "Initializing Web Monitor");

        Intent serviceIntent = new Intent(this, WebMonitorService.class);
        serviceIntent.putExtra("userId", userId);
        serviceIntent.putExtra("phoneModel", phoneModel);

        startService(serviceIntent);
    }

    private void startSMSMonitor(String userId, String phoneModel) {
        Log.d(TAG, "Initializing SMS Monitor");
        smsMonitor = new SMSMonitor(this, userId, phoneModel);
        smsMonitor.startMonitoring();
    }

    private void stopSMSMonitor() {
        if (smsMonitor != null) {
            Log.d(TAG, "Stopping SMS Monitor");
            smsMonitor.stopMonitoring();
            smsMonitor = null;
        }
    }

    private void startCallMonitor(String userId, String phoneModel) {
        Log.d(TAG, "Initializing Call Monitor");
        callMonitor = new CallMonitor(this, userId, phoneModel);
        callMonitor.startMonitoring();
    }

    private void stopCallMonitor() {
        if (callMonitor != null) {
            Log.d(TAG, "Stopping Call Monitor");
            callMonitor.stopMonitoring();
            callMonitor = null;
        }
    }

    private void startLocationMonitor(String userId, String phoneModel) {
        Log.d(TAG, "Initializing Location Monitor");
        locationMonitor = new LocationMonitor(this, userId, phoneModel);
        locationMonitor.startMonitoring();
    }

    private void stopLocationMonitor() {
        if (locationMonitor != null) {
            Log.d(TAG, "Stopping Location Monitor");
            locationMonitor.stopMonitoring();
            locationMonitor = null;
        }
    }

    private void startMMSMonitor(String userId, String phoneModel) {
        Log.d(TAG, "Initializing MMS Monitor");
        mmsMonitor = new MMSMonitor(this, userId, phoneModel);
        mmsMonitor.startMonitoring();
    }

    private void stopMMSMonitor() {
        if (mmsMonitor != null) {
            Log.d(TAG, "Stopping MMS Monitor");
            mmsMonitor.stopMonitoring();
            mmsMonitor = null;
        }
    }

    private void startContactMonitor(String userId, String phoneModel) {
        Log.d(TAG, "Initializing Contact Monitor");
        contactMonitor = new ContactMonitor(this, userId, phoneModel);
        contactMonitor.startMonitoring();
    }

    private void stopContactMonitor() {
        if (contactMonitor != null) {
            Log.d(TAG, "Stopping Contact Monitor");
            contactMonitor.stopMonitoring();
            contactMonitor = null;
        }
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

    private void stopAppMonitor() {
        if (appMonitor != null) {
            Log.d(TAG, "Stopping App Monitor");
            appMonitor.stopMonitoring();
            appMonitor = null;
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

    private void stopAppUsageMonitor() {
        Log.d(TAG, "Stopping App Usage Monitor");
        WorkManager.getInstance(this).cancelUniqueWork("usage_tracking_" + userId);
        Intent serviceIntent = new Intent(this, AppUsageService.class);
        stopService(serviceIntent);
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

    private void stopWebMonitor() {
        Log.d(TAG, "Stopping Web Monitor");
        Intent intent = new Intent(this, WebMonitorService.class);
        stopService(intent);
    }

 
    // Add new method to handle specific preference changes
    public void handlePreferenceChange(String preferenceName, boolean enabled) {
        if (userId == null || phoneModel == null) {
            Log.e(TAG, "UserId or phoneModel not initialized");
            return;
        }

        switch (preferenceName) {
            case "sms":
                if (enabled) startSMSMonitor(userId, phoneModel);
                else stopSMSMonitor();
                break;
            case "call":
                if (enabled) startCallMonitor(userId, phoneModel);
                else stopCallMonitor();
                break;
            case "location":
                if (enabled) startLocationMonitor(userId, phoneModel);
                else stopLocationMonitor();
                break;
            case "mms":
                if (enabled) startMMSMonitor(userId, phoneModel);
                else stopMMSMonitor();
                break;
            case "contacts":
                if (enabled) startContactMonitor(userId, phoneModel);
                else stopContactMonitor();
                break;
            case "apps":
                if (enabled) {
                    startAppMonitor(userId, phoneModel);
                    startAppUsageMonitor(userId, phoneModel);
                } else {
                    stopAppMonitor();
                    stopAppUsageMonitor();
                }
                break;
            case "sites":
                if (enabled) startWebMonitor(userId, phoneModel);
                else stopWebMonitor();
                break;
        }
    }
}