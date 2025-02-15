package com.childmonitorai.monitors;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import androidx.core.content.ContextCompat;

import com.childmonitorai.helpers.BaseContentObserver;
import com.childmonitorai.database.FirebaseStorageHelper;
import com.childmonitorai.detectors.NSFWDetector;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;
import android.content.Intent;
import com.childmonitorai.services.FcmService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import androidx.documentfile.provider.DocumentFile;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileDescriptor;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class PhotosMonitor extends BaseContentObserver {
    private static final String TAG = "PhotosMonitor";
    private final FirebaseStorageHelper firebaseStorageHelper;
    private boolean isMonitoring = false;
    private NSFWDetector nsfwDetector;
    private long lastCheckedTimestamp = 0;
    private static final String PREFS_NAME = "PhotoMonitorPrefs";
    private static final String INSTALL_TIME_KEY = "install_time";
    private long installationTime;
    private long lastProcessedId = -1;
    private static final float NSFW_THRESHOLD = 0.7f;
    private static final int COMPRESSION_QUALITY = 60;
    private String userId;
    private String phoneModel;

    public PhotosMonitor(Context context) {
        super(context);
        
        // Get userId and phoneModel from SharedPreferences
        SharedPreferences prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        userId = prefs.getString("userId", null);
        phoneModel = prefs.getString("phoneModel", null);

        // If userId not found in SharedPreferences, get from Firebase Auth
        if (userId == null) {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser != null) {
                userId = currentUser.getUid();
                // Save for future use
                prefs.edit().putString("userId", userId).apply();
            } else {
                Log.e(TAG, "No user is currently logged in");
            }
        }

        // If phoneModel not found in SharedPreferences, get from Build.MODEL
        if (phoneModel == null) {
            phoneModel = android.os.Build.MODEL;
            // Save for future use
            prefs.edit().putString("phoneModel", phoneModel).apply();
        }

        firebaseStorageHelper = new FirebaseStorageHelper();
        nsfwDetector = new NSFWDetector(context);
        
        // Get or set installation time
        initializeInstallationTime(context);
        
        if (hasStoragePermission()) {
            registerPhotoObserver();
            isMonitoring = true;
            Log.d(TAG, "PhotosMonitor initialized and watching for new photos");
            // Initial check for photos since installation
            checkNewPhotos();
        } else {
            Log.e(TAG, "Storage permission not granted!");
        }
    }

    private boolean hasStoragePermission() {
        Context context = getContext();
        if (context == null) return false;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) 
                == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) 
                == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void registerPhotoObserver() {
        Context context = getContext();
        if (context != null) {
            // Register for both internal and external storage changes
            context.getContentResolver().registerContentObserver(
                MediaStore.Images.Media.INTERNAL_CONTENT_URI,
                true, // true to watch descendants
                this
            );
            context.getContentResolver().registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true, // true to watch descendants
                this
            );
            isMonitoring = true;
            Log.d(TAG, "Photo observers registered successfully");
        }
    }

    private void initializeInstallationTime(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        installationTime = prefs.getLong(INSTALL_TIME_KEY, 0);

        if (installationTime == 0) {
            try {
                PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
                installationTime = packageInfo.firstInstallTime / 1000; // Convert to seconds
                prefs.edit().putLong(INSTALL_TIME_KEY, installationTime).apply();
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "Error getting package info", e);
                installationTime = System.currentTimeMillis() / 1000;
            }
        }
        Log.d(TAG, "Installation time set to: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", 
            Locale.getDefault()).format(new Date(installationTime * 1000)));
    }

    @Override
    protected void onContentChanged(Uri uri) {
        try {
            Log.d(TAG, "Content change detected for URI: " + uri);
            if (isImageContentUri(uri)) {
                Log.d(TAG, "New photo detected - analyzing single photo");
                analyzeNewPhoto(uri);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in content observer", e);
        }
    }

    private boolean isImageContentUri(Uri uri) {
        return uri != null && (
            uri.toString().startsWith(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString()) ||
            uri.toString().startsWith(MediaStore.Images.Media.INTERNAL_CONTENT_URI.toString())
        );
    }

    private void analyzeNewPhoto(Uri changedUri) {
        Context context = getContext();
        if (context == null || !hasStoragePermission()) {
            Log.e(TAG, "Context null or no permission");
            return;
        }

        String[] projection = {
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA
        };

        // Only check the most recently added photo that we haven't processed yet
        String selection = MediaStore.Images.Media.DATE_ADDED + " > ? AND " +
                         MediaStore.Images.Media._ID + " > ?";
        String[] selectionArgs = new String[]{
            String.valueOf(installationTime),
            String.valueOf(lastProcessedId)
        };
        String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC LIMIT 1";

        try (Cursor cursor = context.getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder)) {

            if (cursor != null && cursor.moveToFirst()) {
                int idIndex = cursor.getColumnIndex(MediaStore.Images.Media._ID);
                if (idIndex >= 0) {
                    lastProcessedId = cursor.getLong(idIndex);
                }
                processPhotoFromCursor(cursor);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error analyzing new photo: " + e.getMessage(), e);
        }
    }

    private void processPhotoFromCursor(Cursor cursor) {
        try {
            int pathIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
            int dateIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED);
            int nameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME);
            
            if (pathIndex < 0 || dateIndex < 0 || nameIndex < 0) {
                Log.e(TAG, "Column index not found");
                return;
            }

            String path = cursor.getString(pathIndex);
            long dateAdded = cursor.getLong(dateIndex);
            String fileName = cursor.getString(nameIndex);

            Log.d(TAG, String.format("New photo detected - Name: %s, Path: %s, Date: %s", 
                fileName,
                path,
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(new Date(dateAdded * 1000))
            ));

            // Analyze the image for NSFW content
            Map<String, Float> analysisResult = nsfwDetector.analyzeImage(path);
            
            if (analysisResult.containsKey("nsfw_score")) {
                float nsfwScore = analysisResult.get("nsfw_score");
                if (nsfwScore > NSFW_THRESHOLD) {
                    Log.w(TAG, String.format("Potentially inappropriate content detected in %s", fileName));
                    handleNSFWContent(path, fileName, nsfwScore, analysisResult);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing photo: " + e.getMessage(), e);
        }
    }

    private void handleNSFWContent(String imagePath, String fileName, float nsfwScore, Map<String, Float> scores) {
        Context context = getContext();
        if (context == null) return;

        ContentResolver resolver = context.getContentResolver();
        Uri imageUri = getImageContentUri(imagePath);
        
        if (imageUri == null) {
            Log.e(TAG, "Could not get content URI for NSFW image: " + imagePath);
            return;
        }

        try {
            ParcelFileDescriptor parcelFd = resolver.openFileDescriptor(imageUri, "r");
            if (parcelFd == null) {
                Log.e(TAG, "Could not open file descriptor for: " + imagePath);
                return;
            }

            try {
                FileDescriptor fileDescriptor = parcelFd.getFileDescriptor();
                Bitmap originalBitmap = BitmapFactory.decodeFileDescriptor(fileDescriptor);
                
                if (originalBitmap == null) {
                    Log.e(TAG, "Could not decode bitmap from: " + imagePath);
                    return;
                }

                try {
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    boolean compressed = originalBitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, outputStream);
                    
                    if (!compressed) {
                        Log.e(TAG, "Failed to compress bitmap for: " + imagePath);
                        return;
                    }

                    byte[] compressedImageData = outputStream.toByteArray();
                    
                    // Upload to Firebase Storage with userId and phoneModel
                    firebaseStorageHelper.uploadNSFWPhoto(imagePath, compressedImageData, userId, phoneModel, 
                        new FirebaseStorageHelper.PhotoCallback() {
                            @Override
                            public void onSuccess(String downloadUrl) {
                                sendNSFWNotification(fileName, nsfwScore, downloadUrl, scores);
                                Log.i(TAG, "Successfully uploaded NSFW image: " + fileName);
                            }

                            @Override
                            public void onFailure(String error) {
                                Log.e(TAG, "Failed to upload NSFW photo: " + error);
                            }
                        });

                    outputStream.close();
                } finally {
                    originalBitmap.recycle();
                }
            } finally {
                parcelFd.close();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception accessing file: " + imagePath, e);
        } catch (IOException e) {
            Log.e(TAG, "IO exception processing file: " + imagePath, e);
        } catch (Exception e) {
            Log.e(TAG, "Error handling NSFW content: " + e.getMessage(), e);
        }
    }

    private Uri getImageContentUri(String imagePath) {
        try {
            ContentResolver resolver = getContext().getContentResolver();
            
            // First try to get URI from MediaStore
            Uri contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            String selection = MediaStore.Images.Media.DATA + "=?";
            String[] selectionArgs = new String[]{imagePath};
            
            try (Cursor cursor = resolver.query(
                    contentUri,
                    new String[]{MediaStore.Images.Media._ID},
                    selection,
                    selectionArgs,
                    null)) {

                if (cursor != null && cursor.moveToFirst()) {
                    long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID));
                    return ContentUris.withAppendedId(contentUri, id);
                }
            }

            // If MediaStore fails, try to create content URI from file
            File file = new File(imagePath);
            if (file.exists()) {
                return DocumentFile.fromFile(file).getUri();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting content URI: " + e.getMessage(), e);
        }
        
        return null;
    }

    private void sendNSFWNotification(String fileName, float nsfwScore, String imageUrl, Map<String, Float> scores) {
        Context context = getContext();
        if (context == null) return;

        String title = "Inappropriate Content Detected";
        String message = String.format(
            "Found potentially inappropriate content in %s (Score: %.2f)\n" +
            "Categories: Porn: %.2f, Sexy: %.2f, Hentai: %.2f",
            fileName, nsfwScore,
            scores.get("porn"), scores.get("sexy"), scores.get("hentai")
        );

        // Save notification to Firebase Database
        String currentDate = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
        DatabaseReference notificationsRef = FirebaseDatabase.getInstance()
            .getReference("users")
            .child(userId)
            .child("phones")
            .child(phoneModel)
            .child("notifications")
            .child(currentDate);

        String notificationId = notificationsRef.push().getKey();
        if (notificationId != null) {
            Map<String, Object> notification = new HashMap<>();
            notification.put("title", title);
            notification.put("body", message);
            notification.put("timestamp", System.currentTimeMillis());
            notification.put("type", "nsfw_detection");
            notification.put("imageUrl", imageUrl);
            notification.put("nsfwScore", nsfwScore);
            notification.put("fileName", fileName);
            notification.put("scores", scores);

            notificationsRef.child(notificationId).setValue(notification);
        }

        // Send FCM notification
        Intent intent = new Intent(context, FcmService.class);
        intent.putExtra("title", title);
        intent.putExtra("message", message);
        intent.putExtra("url", imageUrl);
        context.startService(intent);
    }

    private void checkNewPhotos() {
        Context context = getContext();
        if (context == null || !hasStoragePermission()) {
            Log.e(TAG, "Context null or no permission");
            return;
        }

        String[] projection = {
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA
        };

        // Get the latest photo ID for future reference
        String selection = MediaStore.Images.Media.DATE_ADDED + " > ?";
        String[] selectionArgs = new String[]{String.valueOf(installationTime)};
        String sortOrder = MediaStore.Images.Media._ID + " DESC LIMIT 1";

        try (Cursor cursor = context.getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder)) {

            if (cursor != null && cursor.moveToFirst()) {
                int idIndex = cursor.getColumnIndex(MediaStore.Images.Media._ID);
                if (idIndex >= 0) {
                    lastProcessedId = cursor.getLong(idIndex);
                }
                Log.d(TAG, "Initialized last processed photo ID: " + lastProcessedId);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing photo monitor: " + e.getMessage(), e);
        }
    }

    public void stopMonitor() {
        if (isMonitoring) {
            try {
                if (nsfwDetector != null) {
                    nsfwDetector.close();
                    nsfwDetector = null;
                }
                Context context = getContext();
                if (context != null) {
                    context.getContentResolver().unregisterContentObserver(this);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error stopping PhotosMonitor: " + e.getMessage(), e);
            } finally {
                isMonitoring = false;
                Log.d(TAG, "PhotosMonitor stopped successfully");
            }
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            stopMonitor();
        } finally {
            super.finalize();
        }
    }
}
