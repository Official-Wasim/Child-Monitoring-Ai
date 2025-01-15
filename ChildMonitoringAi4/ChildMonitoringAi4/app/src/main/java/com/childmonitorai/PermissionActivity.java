package com.childmonitorai;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import static com.childmonitorai.PermissionHelper.isLocationPermissionGranted;
import static com.childmonitorai.PermissionHelper.isForegroundServicePermissionGranted;
import static com.childmonitorai.PermissionHelper.areCorePermissionsGranted;
import static com.childmonitorai.AccessibilityPermissionHelper.isAccessibilityServiceEnabled;

public class PermissionActivity extends AppCompatActivity {
    private static final String TAG = "PermissionActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission);

        // Show the permission dialog
        new AlertDialog.Builder(this)
                .setTitle("Permissions Required")
                .setMessage("This app requires location, SMS, and other permissions to monitor activity. Please grant permissions in the settings to continue.")
                .setCancelable(false)
                .setPositiveButton("Open Settings", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        navigateToSettings(); // Navigate to settings if user agrees
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish(); // Close the activity if user cancels
                    }
                })
                .show();
    }

    private void navigateToSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setData(android.net.Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Check if required permissions are granted after returning from settings
        if (isLocationPermissionGranted(this) && isForegroundServicePermissionGranted(this) && areCorePermissionsGranted(this)) {
            if (!isAccessibilityServiceEnabled(this, WebMonitor.class)) {
                // Prompt user to enable the Accessibility Service
                promptEnableAccessibilityService();
            } else {
                // Proceed with starting the service
                startMonitoringService();
            }
        } else {
            // Show permission dialog if permissions are missing
            showPermissionDialog();
        }
    }

    private void startMonitoringService() {
        // Start the monitoring service when all permissions are granted
        Intent serviceIntent = new Intent(this, MonitoringService.class);
        startService(serviceIntent);
        finish(); // Close the activity after starting the service
    }

    private void showPermissionDialog() {
        // If permissions are still not granted, show the permission request dialog again
        new AlertDialog.Builder(this)
                .setTitle("Permissions Required")
                .setMessage("This app requires location, SMS, and other permissions to monitor activity. Please grant permissions in the settings to continue.")
                .setCancelable(false)
                .setPositiveButton("Open Settings", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        navigateToSettings(); // Navigate to settings if user agrees
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish(); // Close the activity if user cancels
                    }
                })
                .show();
    }



    // Prompt user to enable Accessibility Service
    private void promptEnableAccessibilityService() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);

        Toast.makeText(this, "Please enable Web Monitor under Accessibility Services to start monitoring.", Toast.LENGTH_LONG).show();
    }
}
