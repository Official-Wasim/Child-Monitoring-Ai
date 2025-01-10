package com.childmonitorai;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class MainActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private DatabaseReference database;
    private String userId;
    private String phoneModel;
    private Button logoutButton;
    private TextView currentUserEmail;

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

        userId = auth.getCurrentUser().getUid();  // Use UID instead of sanitized email
        phoneModel = android.os.Build.MODEL;

        // Initialize TextView
        currentUserEmail = findViewById(R.id.currentUserEmail);

        // Display the current user email
        currentUserEmail.setText(auth.getCurrentUser().getEmail());

        // Store device details in Firebase
        storeDeviceDetails();

        // Find the logout button and set the click listener
        logoutButton = findViewById(R.id.logoutButton);
        logoutButton.setOnClickListener(v -> logout());

        // Check and request permissions
        if (PermissionHelper.isLocationPermissionGranted(this) &&
                PermissionHelper.areCorePermissionsGranted(this) &&
                PermissionHelper.isForegroundServicePermissionGranted(this) &&
                PermissionHelper.isMediaPermissionGranted(this) &&
                PermissionHelper.isUsageStatsPermissionGranted(this)) {
            startForegroundService();
        } else {
            requestPermissions();
        }

        // Check if Accessibility Service is enabled
        if (!AccessibilityPermissionHelper.isAccessibilityServiceEnabled(this, WebMonitor.class)) {
            showAccessibilityPermissionDialog();
        }

        // Check if usage stats permission is granted, if not, show a pop-up
        if (!PermissionHelper.isUsageStatsPermissionGranted(this)) {
            showUsageStatsPermissionDialog();
        }
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
        // Request all necessary permissions
        PermissionHelper.requestAllPermissions(this);

        // Show a dialog informing the user to grant permissions
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

    // Show the usage stats permission dialog
    private void showUsageStatsPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("App Usage Permission Required")
                .setMessage("Please enable the app usage access in the settings to allow monitoring of app usage.")
                .setPositiveButton("Go to Settings", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Redirect the user to the usage access settings page
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

        // Delay for toast message to show before navigating
        new Handler().postDelayed(() -> navigateToLogin(), 1000);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Check if the request code matches the usage stats permission request
        if (requestCode == PermissionHelper.USAGE_STATS_PERMISSION_REQUEST_CODE) {
            // After user returns from settings, check if permission is granted
            if (PermissionHelper.isUsageStatsPermissionGranted(this)) {
                Toast.makeText(this, "Usage Stats Permission Granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permission not granted", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
