package com.childmonitorai;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && intent.getAction() != null &&
                (intent.getAction().equalsIgnoreCase(Intent.ACTION_BOOT_COMPLETED) ||
                        intent.getAction().equalsIgnoreCase("android.intent.action.QUICKBOOT_POWERON") ||
                        intent.getAction().equalsIgnoreCase("com.htc.intent.action.QUICKBOOT_POWERON"))) {

            Log.d(TAG, "Device rebooted. Attempting to restart MonitoringService.");

            // Intent for the MonitoringService
            Intent serviceIntent = new Intent(context, MonitoringService.class);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d(TAG, "Starting MonitoringService as a foreground service.");
                context.startForegroundService(serviceIntent);
            } else {
                Log.d(TAG, "Starting MonitoringService as a regular service.");
                context.startService(serviceIntent);
            }
        }
    }
}
