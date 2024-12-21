package com.childmonitorai;

import static android.content.ContentValues.TAG;

import android.util.Log;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.Date;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DatabaseHelper {

    private static DatabaseReference database;

    public DatabaseHelper() {
        // Firebase initialization
        database = FirebaseDatabase.getInstance().getReference("users");
    }

    // Helper method to avoid repetition of paths
    private static DatabaseReference getPhoneDataReference(String userId, String phoneModel, String dataType, String uniqueId, String date) {
        return database.child(userId)
                .child("phones")
                .child(phoneModel)
                .child(dataType)
                .child(date)
                .child(uniqueId);
    }

    // Upload all call data grouped by date and check for duplication of recent data only
    public void uploadCallDataByDate(String userId, String phoneModel, CallData callData, String uniqueCallId, String callDate) {
        DatabaseReference callRef = getPhoneDataReference(userId, phoneModel, "calls", uniqueCallId, callDate);

        callRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && !task.getResult().exists()) {
                Map<String, Object> callMap = new HashMap<>();
                callMap.put("date", callData.getDate());
                callMap.put("duration", callData.getCallDuration());
                callMap.put("number", callData.getPhoneNumber());
                callMap.put("type", callData.getCallType());
                callMap.put("timestamp", callData.getTimestamp());

                callRef.setValue(callMap).addOnSuccessListener(aVoid ->
                                Log.d("DatabaseHelper", "Call data uploaded successfully."))
                        .addOnFailureListener(e ->
                                Log.e("DatabaseHelper", "Failed to upload call data: " + e.getMessage()));
            } else {
                Log.d("DatabaseHelper", "Duplicate call data found for recent call, skipping upload.");
            }
        }).addOnFailureListener(e -> Log.e("DatabaseHelper", "Error checking for duplication: " + e.getMessage()));
    }

    // Upload all SMS data grouped by date and check for duplication of recent SMS only
    public void uploadSMSDataByDate(String userId, String phoneModel, SMSData smsData, String uniqueSMSId, String smsDate) {
        DatabaseReference smsRef = getPhoneDataReference(userId, phoneModel, "sms", uniqueSMSId, smsDate);

        smsRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && !task.getResult().exists()) {
                Map<String, Object> smsMap = new HashMap<>();
                smsMap.put("type", smsData.getType());
                smsMap.put("address", smsData.getAddress());
                smsMap.put("body", smsData.getBody());
                smsMap.put("timestamp", smsData.getTimestamp());
                smsMap.put("date", smsData.getDate());

                smsRef.setValue(smsMap).addOnSuccessListener(aVoid ->
                                Log.d("DatabaseHelper", "SMS data uploaded successfully."))
                        .addOnFailureListener(e ->
                                Log.e("DatabaseHelper", "Failed to upload SMS data: " + e.getMessage()));
            } else {
                Log.d("DatabaseHelper", "Duplicate SMS data found for recent SMS, skipping upload.");
            }
        }).addOnFailureListener(e -> Log.e("DatabaseHelper", "Error accessing Firebase: " + e.getMessage()));
    }

    // Upload MMS data grouped by date
    public void uploadMMSDataByDate(String userId, String phoneModel, MMSData mmsData, String uniqueMMSId, String mmsDate) {
        DatabaseReference mmsRef = getPhoneDataReference(userId, phoneModel, "mms", uniqueMMSId, mmsDate);

        mmsRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && !task.getResult().exists()) {
                Map<String, Object> mmsMap = new HashMap<>();
                mmsMap.put("subject", mmsData.getSubject());
                mmsMap.put("date", mmsData.getDate());
                mmsMap.put("senderAddress", mmsData.getSenderAddress());
                mmsMap.put("content", mmsData.getContent());

                mmsRef.setValue(mmsMap).addOnSuccessListener(aVoid ->
                                Log.d("DatabaseHelper", "MMS data uploaded successfully."))
                        .addOnFailureListener(e ->
                                Log.e("DatabaseHelper", "Failed to upload MMS data: " + e.getMessage()));
            } else {
                Log.d("DatabaseHelper", "Duplicate MMS data found for recent MMS, skipping upload.");
            }
        }).addOnFailureListener(e -> Log.e("DatabaseHelper", "Error checking for duplication: " + e.getMessage()));
    }

    // Upload location data
    public void uploadLocationDataByDate(String userId, String phoneModel, Map<String, Object> locationData, String uniqueLocationId, String locationDate) {
        String sanitizedLocationId = sanitizePath(uniqueLocationId);
        DatabaseReference locationRef = getPhoneDataReference(userId, phoneModel, "location", sanitizedLocationId, locationDate);

        // Fetch existing data to avoid duplicates
        locationRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && !task.getResult().exists()) {
                // If no existing data, create the data map and upload
                locationRef.setValue(locationData)
                        .addOnSuccessListener(aVoid -> Log.d("DatabaseHelper", "Location data uploaded successfully."))
                        .addOnFailureListener(e -> Log.e("DatabaseHelper", "Failed to upload location data: " + e.getMessage()));
            } else {
                Log.d("DatabaseHelper", "Duplicate location data found for the same date, skipping upload.");
            }
        }).addOnFailureListener(e -> Log.e("DatabaseHelper", "Error accessing Firebase: " + e.getMessage()));
    }

    // Upload contact data
    public void uploadContactData(String userId, String phoneModel, ContactData contactData, String uniqueContactId) {
        DatabaseReference contactRef = getPhoneDataReference(userId, phoneModel, "contacts", uniqueContactId, "");

        contactRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult().exists()) {
                // Contact exists, meaning it's an update
                ContactData existingContact = task.getResult().getValue(ContactData.class);
                if (existingContact != null) {
                    // Set the original name before modification
                    contactData.setNameBeforeModification(existingContact.getName());
                }
                // Update the last modified time
                contactData.setLastModifiedTime(System.currentTimeMillis());
            } else {
                // It's a new contact, so set the creation time
                contactData.setCreationTime(System.currentTimeMillis());
            }

            // Upload the contact data with the appropriate timestamps
            contactRef.setValue(contactData)
                    .addOnCompleteListener(uploadTask -> {
                        if (uploadTask.isSuccessful()) {
                            Log.d("DatabaseHelper", "Contact data uploaded successfully.");
                        } else {
                            Log.e("DatabaseHelper", "Error uploading contact data: " + uploadTask.getException().getMessage());
                        }
                    });
        });
    }

    // Upload app data
    public void uploadAppData(String userId, String phoneModel, String uniqueKey, Map<String, Object> appMap) {
        DatabaseReference appRef = getPhoneDataReference(userId, phoneModel, "apps", uniqueKey, "");

        appRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && !task.getResult().exists()) {
                appRef.setValue(appMap).addOnSuccessListener(aVoid ->
                                Log.d("DatabaseHelper", "App data uploaded successfully."))
                        .addOnFailureListener(e ->
                                Log.e("DatabaseHelper", "Failed to upload app data: " + e.getMessage()));
            } else {
                Log.d("DatabaseHelper", "Duplicate app data found, skipping upload.");
            }
        }).addOnFailureListener(e -> Log.e("DatabaseHelper", "Error checking for duplication: " + e.getMessage()));
    }

    public static void uploadWebVisitDataByDate(String userId, String phoneModel, WebVisitData visitData) {
        // Generate unique ID using timestamp and a random string
        String timestamp = String.valueOf(visitData.getTimestamp());
        String randomId = UUID.randomUUID().toString();  // Directly generate the random string
        String uniqueKey = timestamp + "_" + randomId;  // Unique ID based on timestamp and random ID

        // Convert the Date object to a String
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String dateString = visitData.getDate() != null ? dateFormat.format(visitData.getDate()) : "";

        // Reference to the web visit data in Firebase
        DatabaseReference webRef = getPhoneDataReference(userId, phoneModel, "web_visits", uniqueKey, dateString);

        // Upload the web visit data directly
        Map<String, Object> webMap = new HashMap<>();
        webMap.put("url", visitData.getUrl());
        webMap.put("title", visitData.getTitle());
        webMap.put("timestamp", visitData.getTimestamp());
        webMap.put("date", dateString);

        // Upload the data
        webRef.setValue(webMap).addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Web visit data uploaded successfully.");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to upload web visit data: " + e.getMessage());
                });
    }


    public void uploadAppUsageDataByDate(String userId, String phoneModel, AppUsageData appUsageData) {
        // Generate unique ID using timestamp and a random string
        String timestamp = String.valueOf(appUsageData.getTimestamp());
        String randomId = UUID.randomUUID().toString();  // Generate a random string for uniqueness
        String uniqueKey = timestamp + "_" + randomId;  // Unique key formed by combining timestamp and random ID

        // Convert the current timestamp into a Date object and format it to a string (yyyy-MM-dd)
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String dateString = dateFormat.format(new Date(appUsageData.getTimestamp()));  // Format to "yyyy-MM-dd"

        // Reference to the Firebase node where the app usage data will be uploaded
        DatabaseReference appUsageRef = getPhoneDataReference(userId, phoneModel, "app_usage", uniqueKey, dateString);

        // Prepare the data to upload
        Map<String, Object> appUsageMap = new HashMap<>();
        appUsageMap.put("app_name", appUsageData.getAppName());
        appUsageMap.put("package_name", appUsageData.getPackageName());
        appUsageMap.put("usage_duration", appUsageData.getUsageDuration());
        appUsageMap.put("timestamp", appUsageData.getTimestamp());
        appUsageMap.put("date", dateString);

        // Upload the app usage data to Firebase
        appUsageRef.setValue(appUsageMap).addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "App usage data uploaded successfully.");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to upload app usage data: " + e.getMessage());
                });
    }



    // Helper function to sanitize paths and remove invalid characters
    String sanitizePath(String originalPath) {
        return originalPath.replaceAll("[.#$\\[\\]]", "_");
    }
}
