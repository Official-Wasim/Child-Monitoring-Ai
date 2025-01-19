package com.childmonitorai;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CallMonitor {
    private static final String TAG = "CallMonitor";
    private String userId;
    private String phoneModel;
    private Context context;
    private BaseContentObserver callLogObserver;
    private long installationDate;

    public CallMonitor(Context context, String userId, String phoneModel) {
        this.context = context;
        this.userId = userId;  // Passed in user ID
        this.phoneModel = phoneModel;  // Passed in phone model
        this.installationDate = System.currentTimeMillis(); // Set installation date to current time
    }

    public void startMonitoring() {
        // Create the ContentObserver to monitor changes in the call log
        callLogObserver = new BaseContentObserver(context) {
            @Override
            protected void onContentChanged(Uri uri) {
                // Handle content change (new call log)
                fetchCalls();
            }
        };

        // Register the ContentObserver to observe changes in the call log
        callLogObserver.registerObserver(CallLog.Calls.CONTENT_URI);

//        // Optionally, use periodic polling (if needed)
//        final Handler handler = new Handler(Looper.getMainLooper());
//
//        Runnable runnable = new Runnable() {
//            @Override
//            public void run() {
//                fetchCalls();
//                handler.postDelayed(this, 60000);  // Every minute
//            }
//        };
//
//        handler.post(runnable);
    }

    public void stopMonitoring() {
        // Unregister the ContentObserver when monitoring is stopped
        if (callLogObserver != null) {
            callLogObserver.unregisterObserver();
        }
    }

    private void fetchCalls() {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(CallLog.Calls.CONTENT_URI,
                    null, "date >= ?", new String[]{String.valueOf(installationDate)}, CallLog.Calls.DATE + " DESC");

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    int phoneNumberColumnIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER);
                    int callTypeColumnIndex = cursor.getColumnIndex(CallLog.Calls.TYPE);
                    int callDurationColumnIndex = cursor.getColumnIndex(CallLog.Calls.DURATION);
                    int callDateColumnIndex = cursor.getColumnIndex(CallLog.Calls.DATE);

                    if (phoneNumberColumnIndex != -1 && callTypeColumnIndex != -1 &&
                            callDurationColumnIndex != -1 && callDateColumnIndex != -1) {

                        String phoneNumber = cursor.getString(phoneNumberColumnIndex);
                        String callType = cursor.getString(callTypeColumnIndex);
                        long callDuration = cursor.getLong(callDurationColumnIndex);
                        long timestamp = cursor.getLong(callDateColumnIndex);
                        String callDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(timestamp));

                        String contactName = getContactName(phoneNumber); // Fetch contact name

                        if (timestamp >= installationDate) { // Check if call log is from the date of installation
                            CallData callData = new CallData(phoneNumber, callType, callDuration, callDate);
                            callData.setTimestamp(timestamp);
                            callData.setContactName(contactName); // Set contact name

                            // Generate a unique ID based on phone number and timestamp
                            String uniqueCallId = generateUniqueId(phoneNumber, timestamp);

                            // Check for duplication of recent calls only
                            DatabaseHelper dbHelper = new DatabaseHelper();
                            dbHelper.uploadCallDataByDate(userId, phoneModel, callData, uniqueCallId, callDate);
                        }
                    } else {
                        Log.w(TAG, "Missing required columns in the call log.");
                    }
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching call logs: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private String getContactName(String phoneNumber) {
        String contactName = null;
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
        Cursor cursor = context.getContentResolver().query(uri, new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                contactName = cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME));
            }
            cursor.close();
        }
        return contactName;
    }

    private String generateUniqueId(String phoneNumber, long timestamp) {
        return phoneNumber + "_" + timestamp; // Combination of phone number and timestamp
    }
}
