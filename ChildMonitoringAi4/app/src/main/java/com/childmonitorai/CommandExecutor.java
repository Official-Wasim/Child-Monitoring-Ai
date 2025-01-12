// CommandExecutor.java
package com.childmonitorai;

import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import android.provider.CallLog;
import android.content.ContentResolver;
import android.database.Cursor;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CommandExecutor {
    private static final String TAG = "CommandExecutor";
    private DatabaseReference mDatabase;
    private String userId;
    private String deviceId;
    private Context context;

    public CommandExecutor(String userId, String deviceId, Context context) {
        this.userId = userId;
        this.deviceId = deviceId;
        this.context = context;
        this.mDatabase = FirebaseDatabase.getInstance().getReference();
    }

    public void executeCommand(Command command, String date, String timestamp) {
        if (command == null) return;

        String commandName = command.getCommand();
        Log.d(TAG, "Executing command: " + commandName);

        try {
            switch (commandName) {
                case "get_location":
                    fetchLocation(date, timestamp);
                    break;
                case "recover_calls":
                    String phoneNumber = command.getParam("phone_number", "unknown");
                    int dataCount = Integer.parseInt(command.getParam("data_count", "15"));
                    recoverCalls(date, timestamp, phoneNumber, dataCount);
                    break;
                default:
                    updateCommandStatus(date, timestamp, "failed", "Unknown command: " + commandName);
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error executing command: " + commandName, e);
            updateCommandStatus(date, timestamp, "failed", "Error: " + e.getMessage());
        }
    }

    private void fetchLocation(String date, String timestamp) {
        if (ActivityCompat.checkSelfPermission(context,
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            updateCommandStatus(date, timestamp, "failed", "Location permission not granted");
            return;
        }

        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            updateCommandStatus(date, timestamp, "failed", "Location service unavailable");
            return;
        }

        try {
            Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (location == null) {
                location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }

            if (location != null) {
                String result = String.format(Locale.US, "Latitude: %.6f, Longitude: %.6f",
                        location.getLatitude(), location.getLongitude());
                updateCommandStatus(date, timestamp, "completed", result);
            } else {
                updateCommandStatus(date, timestamp, "failed", "Unable to get location");
            }
        } catch (SecurityException e) {
            updateCommandStatus(date, timestamp, "failed", "Location permission denied");
        }
    }

    private void recoverCalls(String date, String timestamp, String phoneNumber, int dataCount) {
        if (ActivityCompat.checkSelfPermission(context,
                android.Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            updateCommandStatus(date, timestamp, "failed", "Call log permission not granted");
            return;
        }

        ContentResolver contentResolver = context.getContentResolver();
        String selection = "unknown".equals(phoneNumber) ? null : CallLog.Calls.NUMBER + "=?";
        String[] selectionArgs = "unknown".equals(phoneNumber) ? null : new String[]{phoneNumber};

        try (Cursor cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                new String[]{
                        CallLog.Calls.NUMBER,
                        CallLog.Calls.DATE,
                        CallLog.Calls.TYPE,
                        CallLog.Calls.DURATION
                },
                selection,
                selectionArgs,
                CallLog.Calls.DATE + " DESC")) {

            if (cursor == null) {
                updateCommandStatus(date, timestamp, "failed", "Unable to access call logs");
                return;
            }

            int numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER);
            int dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE);
            int typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE);
            int durationIndex = cursor.getColumnIndex(CallLog.Calls.DURATION);

            if (numberIndex == -1 || dateIndex == -1 || typeIndex == -1 || durationIndex == -1) {
                updateCommandStatus(date, timestamp, "failed", "Missing required call log fields");
                return;
            }

            StringBuilder result = new StringBuilder();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            int count = 0;

            while (cursor.moveToNext() && count < dataCount) {
                String number = cursor.getString(numberIndex);
                long callDate = cursor.getLong(dateIndex);
                int callType = cursor.getInt(typeIndex);
                int duration = cursor.getInt(durationIndex);

                result.append("Call ").append(count + 1).append(":\n")
                        .append("Number: ").append(number).append("\n")
                        .append("Date: ").append(dateFormat.format(new Date(callDate))).append("\n")
                        .append("Type: ").append(getCallTypeString(callType)).append("\n")
                        .append("Duration: ").append(duration).append(" seconds\n\n");

                count++;
            }

            String finalResult = count > 0 ? result.toString() :
                    "No call logs found" + ("unknown".equals(phoneNumber) ? "" : " for " + phoneNumber);
            updateCommandStatus(date, timestamp, "completed", finalResult);

        } catch (Exception e) {
            updateCommandStatus(date, timestamp, "failed", "Error accessing call logs: " + e.getMessage());
        }
    }


    private String getCallTypeString(int callType) {
        switch (callType) {
            case CallLog.Calls.INCOMING_TYPE: return "Incoming";
            case CallLog.Calls.OUTGOING_TYPE: return "Outgoing";
            case CallLog.Calls.MISSED_TYPE: return "Missed";
            case CallLog.Calls.VOICEMAIL_TYPE: return "Voicemail";
            case CallLog.Calls.REJECTED_TYPE: return "Rejected";
            case CallLog.Calls.BLOCKED_TYPE: return "Blocked";
            default: return "Unknown";
        }
    }

    private void updateCommandStatus(String date, String timestamp, String status, String result) {
        DatabaseReference commandRef = mDatabase.child("users").child(userId)
                .child("phones").child(deviceId).child("commands")
                .child(date).child(timestamp);

        commandRef.child("status").setValue(status);
        if (result != null) {
            commandRef.child("result").setValue(result);
        }
    }
}