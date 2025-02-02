package com.childmonitorai;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;
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

        if (transitionType == Geofence.GEOFENCE_TRANSITION_ENTER) {
            title = "Geofence Alert";
            message = "Child has entered the monitored area";
        } else if (transitionType == Geofence.GEOFENCE_TRANSITION_EXIT) {
            title = "Geofence Alert";
            message = "Child has left the monitored area";
        } else {
            return;
        }

        // Get userId and phoneModel from SharedPreferences
        SharedPreferences prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        String userId = prefs.getString("userId", "defaultUserId");
        String phoneModel = prefs.getString("phoneModel", "defaultPhoneModel");

        // Log to database
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
