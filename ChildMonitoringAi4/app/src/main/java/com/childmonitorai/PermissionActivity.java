package com.childmonitorai;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.Switch;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import static com.childmonitorai.helpers.PermissionHelper.isLocationPermissionGranted;
import static com.childmonitorai.helpers.PermissionHelper.areCorePermissionsGranted;
import static com.childmonitorai.helpers.AccessibilityPermissionHelper.isAccessibilityServiceEnabled;

import com.childmonitorai.helpers.PermissionHelper;
import com.childmonitorai.services.WebMonitorService;

public class PermissionActivity extends AppCompatActivity {
    private View cardCore, cardStorage, cardLocation, cardAccessibility;
    private View cardForegroundService, cardUsageAccess, cardDeviceAdmin;
    private Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission);

        initializeViews();
        setupToolbar();
        setupPermissionCards();
    }

    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        cardCore = findViewById(R.id.card_core);
        cardStorage = findViewById(R.id.card_storage);
        cardLocation = findViewById(R.id.card_location);
        cardAccessibility = findViewById(R.id.card_accessibility);
        cardForegroundService = findViewById(R.id.card_foreground_service);
        cardUsageAccess = findViewById(R.id.card_usage_access);
        cardDeviceAdmin = findViewById(R.id.card_device_admin);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Permissions");
        }
    }

    private void setupPermissionCards() {
        setupPermissionCard(cardCore, "Core Permissions", 
            "Required permissions for basic functionality including storage access, camera, and notifications. These are essential for monitoring and storing activity data.", 
            this::handleCorePermissions);
        
        setupPermissionCard(cardStorage, "Storage Access", 
            "Needed to store monitoring data and captured media files locally on the device.", 
            this::handleStoragePermission);
        
        setupPermissionCard(cardLocation, "Location Access", 
            "Enables tracking of device location for safety monitoring. This helps locate the device when needed.", 
            this::handleLocationPermission);
        
        setupPermissionCard(cardAccessibility, "Accessibility Service", 
            "Required to monitor app usage and web browsing activity. This helps track digital activity and ensure online safety.", 
            this::handleAccessibilityPermission);
        
        setupPermissionCard(cardForegroundService, "Background Service", 
            "Allows the app to run in the background for continuous monitoring and protection.", 
            this::handleForegroundServicePermission);
        
        setupPermissionCard(cardUsageAccess, "Usage Access", 
            "Enables monitoring of app usage statistics and screen time tracking.", 
            this::handleUsageAccessPermission);
        
        setupPermissionCard(cardDeviceAdmin, "Device Administrator", 
            "Provides advanced control features for device management and security settings.", 
            this::handleDeviceAdminPermission);
    }

    private void setupPermissionCard(View card, String title, String description, 
                                   View.OnClickListener switchListener) {
        TextView titleView = card.findViewById(R.id.permission_title);
        TextView descView = card.findViewById(R.id.permission_description);
        Switch permissionSwitch = card.findViewById(R.id.permission_switch);

        titleView.setText(title);
        descView.setText(description);
        
        boolean isPermissionGranted = getPermissionStatusForCard(card);
        permissionSwitch.setEnabled(!isPermissionGranted);
        permissionSwitch.setAlpha(isPermissionGranted ? 0.5f : 1.0f);
        permissionSwitch.setOnClickListener(switchListener);
    }

    private boolean getPermissionStatusForCard(View card) {
        if (card == cardCore) {
            return areCorePermissionsGranted(this);
        } else if (card == cardLocation) {
            return isLocationPermissionGranted(this);
        } else if (card == cardAccessibility) {
            return isAccessibilityServiceEnabled(this, WebMonitorService.class);
        } else if (card == cardDeviceAdmin) {
            return PermissionHelper.isDeviceAdminEnabled(this);
        }
        return false;
    }

    private void handleCorePermissions(View view) {
        // Handle core permissions
    }

    private void handleStoragePermission(View view) {
        // Handle storage permission
    }

    private void handleLocationPermission(View view) {
        // Handle location permission
    }

    private void handleAccessibilityPermission(View view) {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
    }

    private void handleForegroundServicePermission(View view) {
        // Handle foreground service permission
    }

    private void handleUsageAccessPermission(View view) {
        // Handle usage access permission
    }

    private void handleDeviceAdminPermission(View view) {
        if (!PermissionHelper.isDeviceAdminEnabled(this)) {
            PermissionHelper.requestDeviceAdmin(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionStatuses();
    }

    private void updatePermissionStatuses() {
        updatePermissionStatus(cardCore, areCorePermissionsGranted(this));
        updatePermissionStatus(cardLocation, isLocationPermissionGranted(this));
        updatePermissionStatus(cardAccessibility, 
            isAccessibilityServiceEnabled(this, WebMonitorService.class));
        updatePermissionStatus(cardDeviceAdmin, PermissionHelper.isDeviceAdminEnabled(this));
    }

    private void updatePermissionStatus(View card, boolean isGranted) {
        Switch permissionSwitch = card.findViewById(R.id.permission_switch);
        permissionSwitch.setChecked(isGranted);
        permissionSwitch.setEnabled(!isGranted);
        
        // Only apply alpha if permission is granted (switch is checked)
        float alpha = isGranted ? 0.5f : 1.0f;
        permissionSwitch.setAlpha(alpha);
    }
}
