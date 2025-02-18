package com.childmonitorai;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.util.HashMap;
import java.util.Map;

public class GeofenceBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "GeofenceReceiver";

    private String getCurrentDate() {
        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyyMMdd");
        return dateFormat.format(new java.util.Date());
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
        
        if (geofencingEvent == null) {
            Log.e(TAG, "Geofencing event is null");
            return;
        }

        if (geofencingEvent.hasError()) {
            Log.e(TAG, "Geofencing error: " + geofencingEvent.getErrorCode());
            return;
        }

        int transitionType = geofencingEvent.getGeofenceTransition();
        String message;
        String title;

        // Get the first geofence that triggered the event
        if (!geofencingEvent.getTriggeringGeofences().isEmpty()) {
            String geofenceId = geofencingEvent.getTriggeringGeofences().get(0).getRequestId();
            
            if (transitionType == Geofence.GEOFENCE_TRANSITION_ENTER) {
                title = "Geofence Alert";
                message = "Child has entered " + geofenceId;
            } else if (transitionType == Geofence.GEOFENCE_TRANSITION_EXIT) {
                title = "Geofence Alert";
                message = "Child has left " + geofenceId;
            } else {
                return;
            }
        } else {
            Log.e(TAG, "No triggering geofence found");
            return;
        }

        // Get userId and phoneModel
        SharedPreferences prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        String userId = prefs.getString("userId", null);
        String phoneModel = prefs.getString("phoneModel", null);

        // Verify we have required data
        if (userId == null || phoneModel == null) {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser != null) {
                userId = currentUser.getUid();
                // Save userId for future use
                prefs.edit().putString("userId", userId).apply();
            } else {
                Log.e(TAG, "No user ID available - cannot log notification");
                return;
            }

            if (phoneModel == null) {
                phoneModel = android.os.Build.MODEL;
                // Save phoneModel for future use
                prefs.edit().putString("phoneModel", phoneModel).apply();
            }
        }

        // Upload to database
        String currentDate = getCurrentDate();
        DatabaseReference notificationsRef = FirebaseDatabase.getInstance()
            .getReference("users")
            .child(userId)
            .child("phones")
            .child(phoneModel)
            .child("notifications")
            .child(currentDate);

        Map<String, Object> notification = new HashMap<>();
        notification.put("title", title);
        notification.put("body", message);
        notification.put("timestamp", System.currentTimeMillis());
        notification.put("type", "geofence_alert");
        notification.put("deviceModel", phoneModel);

        notificationsRef.push().setValue(notification)
            .addOnSuccessListener(aVoid -> Log.d(TAG, "Geofence notification logged to database"))
            .addOnFailureListener(e -> Log.e(TAG, "Failed to log geofence notification: " + e.getMessage()));

        // Send FCM notification
        Intent fcmIntent = new Intent(context, FcmService.class);
        fcmIntent.putExtra("title", title);
        fcmIntent.putExtra("message", message);
        fcmIntent.putExtra("url", "");
        context.startService(fcmIntent);
    }
}
