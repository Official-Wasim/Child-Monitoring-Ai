package com.childmonitorai;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;

public class MainActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private DatabaseReference database;
    private String userId;
    private String phoneModel;
    private Button logoutButton;
    private TextView currentUserEmail;

    private static final int DEVICE_ADMIN_REQUEST_CODE = 1;
    private DevicePolicyManager devicePolicyManager;
    private ComponentName deviceAdminComponent;

    private static final int PERMISSION_CHECK_INTERVAL = 5000; // 5 seconds
    private Handler permissionCheckHandler;
    private Runnable permissionCheckRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        auth = FirebaseAuth.getInstance();
        database = FirebaseDatabase.getInstance().getReference("users");

        if (auth.getCurrentUser() == null) {
            navigateToLogin();
            return;
        }

        userId = auth.getCurrentUser().getUid();
        phoneModel = android.os.Build.MODEL;

        currentUserEmail = findViewById(R.id.currentUserEmail);
        currentUserEmail.setText(auth.getCurrentUser().getEmail());

        storeDeviceDetails();

        logoutButton = findViewById(R.id.logoutButton);
        logoutButton.setOnClickListener(v -> logout());

        // Check and request permissions
        if (PermissionHelper.areCorePermissionsGranted(this)) {
            startMonitoringService();
        } else {
            requestPermissions();
        }

        if (!PermissionHelper.isUsageStatsPermissionGranted(this)) {
            showUsageStatsPermissionDialog();
        }

        setupDeviceAdmin();
        setupPermissionCheck();
    }

    private void storeDeviceDetails() {
        String userEmail = auth.getCurrentUser().getEmail();

        DatabaseReference userNode = database.child(userId).child("phones").child(phoneModel).child("user-details");
        userNode.child("model").setValue(phoneModel);
        userNode.child("last_login").setValue(System.currentTimeMillis());
        userNode.child("email").setValue(userEmail); // Add the email
    }

    private void startForegroundService() {
        Intent serviceIntent = new Intent(this, MonitoringService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void requestPermissions() {
        PermissionHelper.requestAllPermissions(this);

        new AlertDialog.Builder(this)
                .setTitle("Permissions Required")
                .setMessage("To use all features of the app, please grant the necessary permissions.")
                .setPositiveButton("Grant Permissions", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        PermissionHelper.requestAllPermissions(MainActivity.this);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startMonitoringService() {
        Intent serviceIntent = new Intent(this, MonitoringService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void showAccessibilityPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Accessibility Permission Required")
                .setMessage("Please enable the Accessibility Service for monitoring web activity.")
                .setPositiveButton("Go to Settings", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                        startActivity(intent);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showUsageStatsPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("App Usage Permission Required")
                .setMessage("Please enable the app usage access in the settings to allow monitoring of app usage.")
                .setPositiveButton("Go to Settings", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                        startActivityForResult(intent, PermissionHelper.USAGE_STATS_PERMISSION_REQUEST_CODE);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void navigateToLogin() {
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        startActivity(intent);
        finish();
    }

    private void logout() {
        auth.signOut();
        Toast.makeText(MainActivity.this, "Logged out successfully", Toast.LENGTH_SHORT).show();

        new Handler().postDelayed(() -> navigateToLogin(), 1000);
    }

    private void setupDeviceAdmin() {
        devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        deviceAdminComponent = new ComponentName(this, DeviceAdminReceiver.class);

        if (!devicePolicyManager.isAdminActive(deviceAdminComponent)) {
            // Request admin privileges with camera management
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdminComponent);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.device_admin_description));
            startActivityForResult(intent, DEVICE_ADMIN_REQUEST_CODE);
        } else {
            // Try to ensure camera is enabled
            try {
                if (devicePolicyManager.getCameraDisabled(deviceAdminComponent)) {
                    devicePolicyManager.setCameraDisabled(deviceAdminComponent, false);
                    Toast.makeText(this, "Camera enabled successfully", Toast.LENGTH_SHORT).show();
                }
            } catch (SecurityException e) {
                Log.e("MainActivity", "Cannot modify camera state: " + e.getMessage());
                Toast.makeText(this, "Cannot enable camera - check device settings", Toast.LENGTH_LONG).show();
                showCameraSettingsDialog();
            }
        }
    }

    private void showCameraSettingsDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Camera Access Required")
            .setMessage("Camera access appears to be disabled by system policy. Would you like to check device settings?")
            .setPositiveButton("Open Settings", (dialog, which) -> {
                Intent intent = new Intent(Settings.ACTION_SECURITY_SETTINGS);
                startActivity(intent);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void setupPermissionCheck() {
        permissionCheckHandler = new Handler();
        permissionCheckRunnable = new Runnable() {
            @Override
            public void run() {
                checkPermissions();
                permissionCheckHandler.postDelayed(this, PERMISSION_CHECK_INTERVAL);
            }
        };
        startPermissionCheck();
    }

    private void startPermissionCheck() {
        permissionCheckHandler.post(permissionCheckRunnable);
    }

    private void stopPermissionCheck() {
        permissionCheckHandler.removeCallbacks(permissionCheckRunnable);
    }

    private void checkPermissions() {
        if (!PermissionHelper.isNotificationListenerEnabled(this)) {
            PermissionHelper.showNotificationAccessDialog(this);
        }
        // Add other permission checks as needed
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPermissionCheck();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PermissionHelper.USAGE_STATS_PERMISSION_REQUEST_CODE) {
            if (PermissionHelper.isUsageStatsPermissionGranted(this)) {
                Toast.makeText(this, "Usage Stats Permission Granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permission not granted", Toast.LENGTH_SHORT).show();
            }
        }

        if (requestCode == DEVICE_ADMIN_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // Device admin enabled
                Log.d("MainActivity", "Device Admin enabled");
                // Try to enable camera after device admin is activated
                setupDeviceAdmin();
            } else {
                // Device admin not enabled
                Log.d("MainActivity", "Device Admin not enabled");
                Toast.makeText(this, "Device admin access required for camera functionality", Toast.LENGTH_LONG).show();
            }
        }

        if (requestCode == PermissionHelper.NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (PermissionHelper.isNotificationListenerEnabled(this)) {
                Toast.makeText(this, "Notification Access Granted", Toast.LENGTH_SHORT).show();
                // Start or restart the NotificationMonitor service
                restartNotificationService();
            } else {
                Toast.makeText(this, "Notification Access Required", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void restartNotificationService() {
        toggleNotificationListenerService();
    }

    private void toggleNotificationListenerService() {
        ComponentName thisComponent = new ComponentName(this, NotificationMonitor.class);
        PackageManager pm = getPackageManager();
        pm.setComponentEnabledSetting(thisComponent,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        pm.setComponentEnabledSetting(thisComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
    }
}
