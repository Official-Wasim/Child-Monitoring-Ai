package com.childmonitorai.helpers;

import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;

public class AccessibilityPermissionHelper {

    // Method to check if the given Accessibility service is enabled
    public static boolean isAccessibilityServiceEnabled(Context context, Class<?> service) {
        String colonSplitter = ":";
        String enabledServices = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);

        // Check if enabledServices is null or empty
        if (TextUtils.isEmpty(enabledServices)) {
            return false;  
        }

        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(colonSplitter.charAt(0));
        splitter.setString(enabledServices);

        while (splitter.hasNext()) {
            String enabledService = splitter.next();
            if (enabledService.equalsIgnoreCase(service.getName())) {
                return true;
            }
        }
        return false;
    }
}
