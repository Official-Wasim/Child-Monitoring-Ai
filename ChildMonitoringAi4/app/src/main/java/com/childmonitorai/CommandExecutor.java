package com.childmonitorai;

import android.content.ContentResolver;
import android.database.Cursor;
import android.location.LocationManager;
import android.provider.CallLog;
import android.util.Log;
import android.content.Context;
import android.location.Location;

import androidx.core.app.ActivityCompat;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import android.content.pm.PackageManager;


import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public class CommandExecutor {
    private DatabaseReference mDatabase;
    private String userId;
    private String deviceId;
    private Context context;
    private FusedLocationProviderClient mFusedLocationClient;
    private static final String TAG = "CommandExecutor";

    public CommandExecutor(String userId, String deviceId, Context context) {
        this.userId = userId;
        this.deviceId = deviceId;
        this.context = context;
        this.mDatabase = FirebaseDatabase.getInstance().getReference();
        this.mFusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
    }

    public void executeCommand(Command command, String commandId) {
        if (command != null) {
            String commandName = command.getCommand();
            Map<String, String> params = command.getParams();
            String status = command.getStatus();

            // Log the command details for debugging purposes
            Log.d(TAG, "Executing command: " + commandName + " with params: " + params);

            // Perform actions based on the command
            if ("recover_calls".equals(commandName)) {
                recoverCalls(params, commandId);
            } else if ("get_location".equals(commandName)) {
                fetchLocation(commandId);
            } else {
                Log.d(TAG, "Unknown command: " + commandName);
                updateCommandStatus(commandId, "failed", "Unknown command");
            }
        }
    }

    private void recoverCalls(Map<String, String> params, String commandId) {
        String dataCountStr = params.get("data_count");
        String phoneNumber = params.get("phone_number");

        // Default values if the parameters are missing or invalid
        int dataCount = 5; // Default value
        try {
            if (dataCountStr != null) {
                dataCount = Integer.parseInt(dataCountStr);
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid data_count parameter: " + dataCountStr);
        }

        // Fetch call logs
        String result = fetchCallLogs(phoneNumber, dataCount);

        // Log the result of the recovery operation
        Log.d(TAG, "Recover calls result: " + result);

        // Update the command status with the result
        updateCommandStatus(commandId, "completed", result);
    }

    private String fetchCallLogs(String phoneNumber, int dataCount) {
        StringBuilder result = new StringBuilder();

        ContentResolver contentResolver = context.getContentResolver();
        Cursor cursor = null;
        try {
            // Query the call log for the last 'dataCount' records for the specific phone number
            String selection = phoneNumber != null ? CallLog.Calls.NUMBER + " = ?" : null;
            String[] selectionArgs = phoneNumber != null ? new String[]{phoneNumber} : null;
            String sortOrder = CallLog.Calls.DATE + " DESC"; // Sort by date in descending order to get the most recent calls

            String[] projection = {
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.DATE,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DURATION
            };

            cursor = contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
            );

            if (cursor != null && cursor.moveToFirst()) {
                int count = 0;

                // Get column indices with safe fallback
                int numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER);
                int dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE);
                int typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE);
                int durationIndex = cursor.getColumnIndex(CallLog.Calls.DURATION);

                do {
                    // Check if columns are available
                    String number = (numberIndex != -1) ? cursor.getString(numberIndex) : "N/A";
                    long dateMillis = (dateIndex != -1) ? cursor.getLong(dateIndex) : 0;
                    String type = (typeIndex != -1) ? cursor.getString(typeIndex) : "Unknown";
                    String duration = (durationIndex != -1) ? cursor.getString(durationIndex) : "0";

                    // Format the date to a readable format
                    String date = (dateMillis > 0) ?
                            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(dateMillis))
                            : "Unknown";

                    // Build the result message
                    result.append("Call ").append(count + 1).append(":\n");
                    result.append("Type: ").append(type).append("\n");
                    result.append("Number: ").append(number).append("\n");
                    result.append("Duration: ").append(duration).append(" seconds\n");
                    result.append("Timestamp: ").append(date).append("\n\n");

                    count++;
                    if (count >= dataCount) {
                        break;
                    }
                } while (cursor.moveToNext());
            }

            if (result.length() == 0) {
                result.append("No calls found");
                if (phoneNumber != null) {
                    result.append(" for the specified phone number: ").append(phoneNumber);
                }
            }
        } catch (Exception e) {
            result.append("Error fetching call logs: ").append(e.getMessage());
            Log.e("CommandExecutor", "Error fetching call logs: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return result.toString();
    }

    private void fetchLocation(String commandId) {
        // Check if the necessary permission to access location is granted
        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            updateCommandStatus(commandId, "failed", "Location permission not granted");
            Log.e(TAG, "Location permission not granted.");
            return;
        }

        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            updateCommandStatus(commandId, "failed", "Location service unavailable");
            return;
        }

        Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (location == null) {
            location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }

        if (location != null) {
            String locationDetails = "Latitude: " + location.getLatitude() + ", Longitude: " + location.getLongitude();
            updateCommandStatus(commandId, "completed", locationDetails);
        } else {
            updateCommandStatus(commandId, "failed", "Unable to fetch location");
        }
    }

    private void updateCommandStatus(String commandId, String status, String result) {
        DatabaseReference commandRef = mDatabase.child("users").child(userId).child("phones").child(deviceId).child("commands").child(commandId);

        commandRef.child("status").setValue(status);
        commandRef.child("result").setValue(result);

        // Log the status update
        Log.d(TAG, "Updated command status: " + status + " for commandId: " + commandId);
    }
}
