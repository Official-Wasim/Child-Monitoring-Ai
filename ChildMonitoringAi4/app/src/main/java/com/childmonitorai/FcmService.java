package com.childmonitorai;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.IBinder;
import android.util.Log;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.io.InputStreamReader;
import java.io.BufferedReader;

public class FcmService extends Service {
    private static final String TAG = "FCMService";
    private DatabaseReference databaseRef;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String url = intent.getStringExtra("url");
            String title = intent.getStringExtra("title");
            String message = intent.getStringExtra("message");
            
            // Get userId from SharedPreferences first
            SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
            String userId = prefs.getString("userId", null);
            
            // If not in SharedPreferences, try to get from Firebase Auth
            if (userId == null) {
                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                if (currentUser != null) {
                    userId = currentUser.getUid();
                    // Save for future use
                    prefs.edit().putString("userId", userId).apply();
                } else {
                    Log.e(TAG, "No user is currently logged in");
                    return START_NOT_STICKY;
                }
            }
            
            fetchParentTokenAndSendNotification(userId, title, message, url);
        }
        return START_NOT_STICKY;
    }

    private void fetchParentTokenAndSendNotification(String userId, String title, String message, String url) {
        databaseRef = FirebaseDatabase.getInstance().getReference()
            .child("users").child(userId).child("parent_fcm_token");

        databaseRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
            String parentToken = dataSnapshot.getValue(String.class);
            if (parentToken != null && !parentToken.isEmpty()) {
                new SendFcmNotificationTask().execute(new String[]{title, message, url, parentToken});
            } else {
                Log.e(TAG, "Parent FCM token not found for user: " + userId);
            }
            }

            @Override
            public void onCancelled(DatabaseError error) {
            Log.e(TAG, "Failed to fetch parent FCM token: " + error.getMessage());
            }
        });
        
        }

        private class SendFcmNotificationTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... params) {
            String title = params[0];
            String message = params[1];
            String url = params[2];
            String parentToken = params[3];
            try {
                String accessToken = getAccessToken();
                if (accessToken == null) {
                    Log.e(TAG, "Failed to get access token");
                    return null;
                }

                URL fcmUrl = new URL("https://fcm.googleapis.com/v1/projects/child-monitor-ai/messages:send");
                HttpURLConnection conn = (HttpURLConnection) fcmUrl.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + accessToken);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                String jsonPayload = String.format("""
                    {
                        "message": {
                            "token": "%s",
                            "notification": {
                                "title": "%s",
                                "body": "%s"
                            },
                            "data": {
                                "url": "%s"
                            }
                        }
                    }""", parentToken, title, message, url);

                conn.getOutputStream().write(jsonPayload.getBytes());
                
                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    Log.d(TAG, "FCM notification sent successfully");
                } else {
                    Log.e(TAG, "FCM notification failed: " + conn.getResponseCode());
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Error sending FCM notification", e);
            }
            return null;
        }
    }

    /**
     * Generates an access token for FCM HTTP v1 API from service account.
     * @return The access token string.
     */
    private String getAccessToken() {
        try {
            InputStream inputStream = getResources().openRawResource(R.raw.service_account);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            inputStream.close();

            inputStream = new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8));
            GoogleCredentials credentials = GoogleCredentials.fromStream(inputStream)
                .createScoped(Collections.singleton("https://www.googleapis.com/auth/firebase.messaging"));
            credentials.refreshIfExpired();
            return credentials.getAccessToken().getTokenValue();
        } catch (Exception e) {
            Log.e(TAG, "Error getting access token: " + e.getMessage());
            return null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
