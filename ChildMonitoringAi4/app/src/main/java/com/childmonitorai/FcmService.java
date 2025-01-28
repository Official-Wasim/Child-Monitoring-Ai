package com.childmonitorai;

import android.util.Log;
import androidx.annotation.NonNull;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessaging;

public class FcmService extends FirebaseMessagingService {
    private static final String TAG = "FcmService";

    @Override
    public void onCreate() {
        super.onCreate();
        // Subscribe to topic for reliable delivery
        FirebaseMessaging.getInstance().subscribeToTopic("parentAlerts")
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Log.d(TAG, "Subscribed to parentAlerts topic");
                } else {
                    Log.e(TAG, "Failed to subscribe to topic", task.getException());
                }
            });
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "New FCM token: " + token);
        
        String userId = getUserId();
        // Store token in both locations for redundancy
        FirebaseDatabase.getInstance()
            .getReference("devices")
            .child(userId)
            .child("fcmToken")
            .setValue(token)
            .addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Log.d(TAG, "Token stored successfully");
                } else {
                    Log.e(TAG, "Failed to store token", task.getException());
                }
            });

        // Store in shared preferences for local access
        getSharedPreferences("AppPrefs", MODE_PRIVATE)
            .edit()
            .putString("fcmToken", token)
            .apply();
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "Message received from: " + remoteMessage.getFrom());
        
        // Log complete message for debugging
        Log.d(TAG, "Complete message: " + remoteMessage.toString());
        
        if (remoteMessage.getData().size() > 0) {
            Log.d(TAG, "Message data payload: " + remoteMessage.getData());
        }

        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "Message Notification Body: " + remoteMessage.getNotification().getBody());
            Log.d(TAG, "Message Notification Title: " + remoteMessage.getNotification().getTitle());
        }
    }

    private String getUserId() {
        return getSharedPreferences("AppPrefs", MODE_PRIVATE)
                .getString("userId", "defaultUserId");
    }
}
