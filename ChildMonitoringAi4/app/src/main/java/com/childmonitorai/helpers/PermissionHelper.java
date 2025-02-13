package com.childmonitorai.helpers;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

public class PermissionHelper {

    // Permission Request Codes
    public static final int LOCATION_PERMISSION_REQUEST_CODE = 100;
    public static final int CORE_PERMISSION_REQUEST_CODE = 101;
    public static final int FOREGROUND_SERVICE_PERMISSION_REQUEST_CODE = 102;
    public static final int MEDIA_PERMISSION_REQUEST_CODE = 103;
    public static final int USAGE_STATS_PERMISSION_REQUEST_CODE = 104;
    public static final int SCREENSHOT_PERMISSION_REQUEST_CODE = 1005;
    public static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 105;
    public static final int WIFI_PERMISSION_REQUEST_CODE = 1005;

    private static final String ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners";
    private static AlertDialog currentDialog;

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
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // For Android versions below 13, check for READ_EXTERNAL_STORAGE permission
            return ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
        return true; // No media permission required for Android versions below Marshmallow (Android 6.0)
    }

    // Check if usage stats permission is granted
    public static boolean isUsageStatsPermissionGranted(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            android.app.usage.UsageStatsManager usageStatsManager = (android.app.usage.UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usageStatsManager != null) {
                long time = System.currentTimeMillis();
                List<android.app.usage.UsageStats> stats = usageStatsManager.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, time - 1000 * 60 * 60 * 24, time);
                return stats != null && !stats.isEmpty();
            }
        }
        return false;
    }

    // Check if screenshot permission is granted
    public static boolean isScreenshotPermissionGranted(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("ScreenshotPrefs", Context.MODE_PRIVATE);
        return prefs.contains("resultCode") && prefs.contains("intentData");
    }

    // Check if notification listener permission is granted
    public static boolean isNotificationListenerEnabled(Context context) {
        String pkgName = context.getPackageName();
        final String flat = Settings.Secure.getString(context.getContentResolver(),
                ENABLED_NOTIFICATION_LISTENERS);
        if (!TextUtils.isEmpty(flat)) {
            final String[] names = flat.split(":");
            for (String name : names) {
                final ComponentName cn = ComponentName.unflattenFromString(name);
                if (cn != null && TextUtils.equals(pkgName, cn.getPackageName())) {
                    return true;
                }
            }
        }
        return false;
    }

    // Check if WiFi permission is granted
    public static boolean isWifiPermissionGranted(Context context) {
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED;
    }

    // Request usage stats permission (redirect to Settings)
    public static void requestUsageStatsPermission(Activity activity) {
        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        activity.startActivityForResult(intent, USAGE_STATS_PERMISSION_REQUEST_CODE);
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
                new String[] {
                        android.Manifest.permission.READ_SMS,
                        android.Manifest.permission.READ_CALL_LOG,
                        android.Manifest.permission.READ_CONTACTS,
                        android.Manifest.permission.READ_PHONE_STATE,
                        android.Manifest.permission.SEND_SMS
                },
                CORE_PERMISSION_REQUEST_CODE);
    }

    // Request media-related permissions (Android 13+ and below)
    public static void requestMediaPermissions(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // For Android 13+
            ActivityCompat.requestPermissions(activity,
                    new String[] {
                            android.Manifest.permission.READ_MEDIA_IMAGES,
                            android.Manifest.permission.READ_MEDIA_VIDEO
                    },
                    MEDIA_PERMISSION_REQUEST_CODE);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // For Android 6.0 to 12
            ActivityCompat.requestPermissions(activity,
                    new String[] { android.Manifest.permission.READ_EXTERNAL_STORAGE },
                    MEDIA_PERMISSION_REQUEST_CODE);
        }
    }

    // Request screenshot permission
    public static void requestScreenshotPermission(Activity activity) {
        ActivityCompat.requestPermissions(activity,
                new String[] { android.Manifest.permission.READ_EXTERNAL_STORAGE },
                SCREENSHOT_PERMISSION_REQUEST_CODE);
    }

    // Request notification listener permission
    public static void requestNotificationListenerPermission(Activity activity) {
        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        activity.startActivityForResult(intent, NOTIFICATION_PERMISSION_REQUEST_CODE);
    }

    // Request WiFi permission
    public static void requestWifiPermission(Activity activity) {
        ActivityCompat.requestPermissions(activity,
                new String[] { android.Manifest.permission.ACCESS_WIFI_STATE },
                WIFI_PERMISSION_REQUEST_CODE);
    }

    // Show notification access dialog
    public static void showNotificationAccessDialog(final Context context) {
        try {
            // Dismiss any existing dialog
            if (currentDialog != null && currentDialog.isShowing()) {
                currentDialog.dismiss();
            }
            
            // Create and show new dialog
            AlertDialog.Builder builder = new AlertDialog.Builder(context)
                    .setTitle("Notification Access Required")
                    .setMessage("This app needs notification access to monitor usage")
                    .setPositiveButton("Settings", (dialog, which) -> {
                        Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                    })
                    .setNegativeButton("Cancel", null)
                    .setCancelable(false);

            currentDialog = builder.create();
            currentDialog.show();
            
        } catch (Exception e) {
            Log.e("PermissionHelper", "Error showing notification dialog: " + e.getMessage());
        }
    }

    public static void cleanup() {
        if (currentDialog != null && currentDialog.isShowing()) {
            try {
                currentDialog.dismiss();
            } catch (Exception e) {
                Log.e("PermissionHelper", "Error dismissing dialog: " + e.getMessage());
            }
            currentDialog = null;
        }
    }

    // Request foreground service permission (Android 10+)
    public static void requestForegroundServicePermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(activity,
                    new String[] { android.Manifest.permission.FOREGROUND_SERVICE },
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

        // Media permissions (Android 13+ and below)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.READ_MEDIA_IMAGES);
            permissions.add(android.Manifest.permission.READ_MEDIA_VIDEO);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            permissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        // Screenshot permission
        permissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE);

        // WiFi permission
        permissions.add(android.Manifest.permission.ACCESS_WIFI_STATE);

        // Request permissions
        ActivityCompat.requestPermissions(activity, permissions.toArray(new String[0]), CORE_PERMISSION_REQUEST_CODE);
    }

    // Check if we should show an explanation for a permission
    public static boolean shouldShowPermissionRationale(Activity activity, String permission) {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission);
    }
}
