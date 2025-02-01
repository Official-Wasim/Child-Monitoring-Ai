package com.childmonitorai;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class DeviceAdminReceiver extends android.app.admin.DeviceAdminReceiver {
    private static final String TAG = "DeviceAdminReceiver";

    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        Log.i(TAG, "Device Admin Enabled");
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
        Log.i(TAG, "Device Admin Disabled");
    }

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        return "This app cannot be uninstalled.";
    }
}
