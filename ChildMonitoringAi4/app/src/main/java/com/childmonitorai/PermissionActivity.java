package com.childmonitorai;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.Switch;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import static com.childmonitorai.PermissionHelper.isLocationPermissionGranted;
import static com.childmonitorai.PermissionHelper.isForegroundServicePermissionGranted;
import static com.childmonitorai.PermissionHelper.areCorePermissionsGranted;
import static com.childmonitorai.AccessibilityPermissionHelper.isAccessibilityServiceEnabled;

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
            "Basic permissions required for app functionality", this::handleCorePermissions);
        
        setupPermissionCard(cardLocation, "Location Access", 
            "Required for location tracking features", this::handleLocationPermission);
        
        setupPermissionCard(cardAccessibility, "Accessibility Service", 
            "Required for monitoring app usage", this::handleAccessibilityPermission);
        
        // Setup other cards similarly
    }

    private void setupPermissionCard(View card, String title, String description, 
                                   View.OnClickListener switchListener) {
        TextView titleView = card.findViewById(R.id.permission_title);
        TextView descView = card.findViewById(R.id.permission_description);
        Switch permissionSwitch = card.findViewById(R.id.permission_switch);

        titleView.setText(title);
        descView.setText(description);
        permissionSwitch.setOnClickListener(switchListener);
    }

    private void handleCorePermissions(View view) {
        // Handle core permissions
    }

    private void handleLocationPermission(View view) {
        // Handle location permission
    }

    private void handleAccessibilityPermission(View view) {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
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
            isAccessibilityServiceEnabled(this, WebMonitor.class));
        // Update other permission statuses
    }

    private void updatePermissionStatus(View card, boolean isGranted) {
        Switch permissionSwitch = card.findViewById(R.id.permission_switch);
        permissionSwitch.setChecked(isGranted);
    }
}
