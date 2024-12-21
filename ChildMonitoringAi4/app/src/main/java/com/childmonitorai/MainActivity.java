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
                PermissionHelper.isForegroundServicePermissionGranted(this)) {
            startForegroundService();
        } else {
            requestPermissions();
        }

        // Check if Accessibility Service is enabled
        if (!AccessibilityPermissionHelper.isAccessibilityServiceEnabled(this, WebMonitor.class)) {
            showAccessibilityPermissionDialog();
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
        // Request location permissions if not granted
        if (!PermissionHelper.isLocationPermissionGranted(this)) {
            PermissionHelper.requestLocationPermissions(this);
        }

        // Request core permissions (SMS, Call Log, Contacts, etc.) if not granted
        if (!PermissionHelper.areCorePermissionsGranted(this)) {
            PermissionHelper.requestCorePermissions(this);
        }

        // Request foreground service permission if not granted
        if (!PermissionHelper.isForegroundServicePermissionGranted(this)) {
            PermissionHelper.requestForegroundServicePermission(this);
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
}
