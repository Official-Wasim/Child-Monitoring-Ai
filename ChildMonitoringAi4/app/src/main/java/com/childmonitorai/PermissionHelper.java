package com.childmonitorai;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class PermissionHelper {

    // Permission Request Codes
    public static final int LOCATION_PERMISSION_REQUEST_CODE = 100;
    public static final int CORE_PERMISSION_REQUEST_CODE = 101;
    public static final int FOREGROUND_SERVICE_PERMISSION_REQUEST_CODE = 102;
    public static final int MMS_PERMISSION_REQUEST_CODE = 103;

    // Check if location permissions are granted
    public static boolean isLocationPermissionGranted(Context context) {
        boolean fineLocation = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarseLocation = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean backgroundLocation = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;

        return fineLocation && coarseLocation && (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || backgroundLocation);
    }

    // Check if foreground service permission is granted (Android 10 and above)
    public static boolean isForegroundServicePermissionGranted(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ContextCompat.checkSelfPermission(context, android.Manifest.permission.FOREGROUND_SERVICE) == PackageManager.PERMISSION_GRANTED;
        }
        return true; // Implicit permission for Android versions below Q
    }

    // Check if core permissions (SMS, Call Log, Contacts, etc.) are granted
    public static boolean areCorePermissionsGranted(Context context) {
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    // Check if media-related permissions (Android 13+) are granted
    public static boolean isMediaPermissionGranted(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
        }
        return true; // No media permission required for Android versions below 13
    }



    // Request location permissions
    public static void requestLocationPermissions(Activity activity) {
        List<String> permissions = new ArrayList<>();
        permissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(android.Manifest.permission.ACCESS_COARSE_LOCATION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        }

        ActivityCompat.requestPermissions(activity, permissions.toArray(new String[0]), LOCATION_PERMISSION_REQUEST_CODE);
    }

    // Request core permissions (SMS, Call Log, Contacts, etc.)
    public static void requestCorePermissions(Activity activity) {
        ActivityCompat.requestPermissions(activity,
                new String[]{
                        android.Manifest.permission.READ_SMS,
                        android.Manifest.permission.READ_CALL_LOG,
                        android.Manifest.permission.READ_CONTACTS,
                        android.Manifest.permission.READ_PHONE_STATE,
                        android.Manifest.permission.SEND_SMS
                },
                CORE_PERMISSION_REQUEST_CODE);
    }

    // Request media-related permissions (Android 13+)
    public static void requestMmsPermissions(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(activity,
                    new String[] {
                            android.Manifest.permission.READ_MEDIA_IMAGES,
                            android.Manifest.permission.READ_MEDIA_VIDEO
                    },
                    MMS_PERMISSION_REQUEST_CODE);
        }
    }

    // Request foreground service permission (Android 10+)
    public static void requestForegroundServicePermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{android.Manifest.permission.FOREGROUND_SERVICE},
                    FOREGROUND_SERVICE_PERMISSION_REQUEST_CODE);
        }
    }

    // Request all necessary permissions
    public static void requestAllPermissions(Activity activity) {
        List<String> permissions = new ArrayList<>();

        // Core permissions (SMS, Call Log, Contacts, etc.)
        permissions.add(android.Manifest.permission.READ_SMS);
        permissions.add(android.Manifest.permission.READ_CALL_LOG);
        permissions.add(android.Manifest.permission.READ_CONTACTS);
        permissions.add(android.Manifest.permission.READ_PHONE_STATE);
        permissions.add(android.Manifest.permission.SEND_SMS);

        // Location permissions
        permissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(android.Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION);
            permissions.add(android.Manifest.permission.FOREGROUND_SERVICE);
        }

        // Media permissions (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.READ_MEDIA_IMAGES);
            permissions.add(android.Manifest.permission.READ_MEDIA_VIDEO);
        }

        // Request permissions
        ActivityCompat.requestPermissions(activity, permissions.toArray(new String[0]), CORE_PERMISSION_REQUEST_CODE);
    }

    // Check if we should show an explanation for a permission
    public static boolean shouldShowPermissionRationale(Activity activity, String permission) {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission);
    }
}
