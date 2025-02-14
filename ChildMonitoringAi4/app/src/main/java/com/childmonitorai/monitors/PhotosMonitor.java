package com.childmonitorai.monitors;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import androidx.core.content.ContextCompat;

import com.childmonitorai.helpers.BaseContentObserver;
import com.childmonitorai.database.FirebaseStorageHelper;
import com.childmonitorai.detectors.NSFWDetector;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public class PhotosMonitor extends BaseContentObserver {
    private static final String TAG = "PhotosMonitor";
    private final FirebaseStorageHelper firebaseStorageHelper;
    private boolean isMonitoring = false;
    private NSFWDetector nsfwDetector;

    public PhotosMonitor(Context context) {
        super(context);
        firebaseStorageHelper = new FirebaseStorageHelper();
        nsfwDetector = new NSFWDetector(context);
        
        if (hasStoragePermission()) {
            registerPhotoObserver();
            isMonitoring = true;
            Log.d(TAG, "PhotosMonitor initialized and watching for new photos");
            // Initial check for existing photos
            checkForNewPhotos();
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
        registerObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        isMonitoring = true;
    }

    @Override
    protected void onContentChanged(Uri uri) {
        try {
            Log.d(TAG, "Content change detected for URI: " + uri);
            if (uri.equals(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)) {
                Log.d(TAG, "New photo detected - checking details");
                checkForNewPhotos();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in content observer", e);
        }
    }

    private void checkForNewPhotos() {
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

        String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC LIMIT 10";

        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder);

            if (cursor != null) {
                Log.d(TAG, "Query executed. Cursor count: " + cursor.getCount());
                
                if (cursor.moveToFirst()) {
                    Log.d(TAG, "Processing " + cursor.getCount() + " photos");
                    
                    do {
                        int pathIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
                        int dateIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED);
                        int nameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME);
                        
                        if (pathIndex < 0 || dateIndex < 0 || nameIndex < 0) {
                            Log.e(TAG, "Column index not found");
                            continue;
                        }

                        String path = cursor.getString(pathIndex);
                        long dateAdded = cursor.getLong(dateIndex);
                        String fileName = cursor.getString(nameIndex);

                        Log.d(TAG, String.format("Photo found - Name: %s, Path: %s, Date: %s", 
                            fileName,
                            path,
                            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                                .format(new Date(dateAdded * 1000))
                        ));

                        // Analyze the image for NSFW content
                        Map<String, Float> analysisResult = nsfwDetector.analyzeImage(path);
                        
                        // Log the results
                        if (analysisResult.containsKey("nsfw_score")) {
                            float nsfwScore = analysisResult.get("nsfw_score");
                            Log.d(TAG, String.format("Photo analyzed - Name: %s, NSFW Score: %.2f", 
                                fileName, nsfwScore));
                            
                            // If NSFW score is high, log a warning
                            if (nsfwScore > 0.7) {  // You can adjust this threshold
                                Log.w(TAG, String.format("Potentially inappropriate content detected in %s", fileName));
                                // Here you could add code to notify parents or take other actions
                            }
                        }

                    } while (cursor.moveToNext());
                } else {
                    Log.d(TAG, "No photos found in query");
                }
            } else {
                Log.e(TAG, "Cursor is null");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking for photos: " + e.getMessage(), e);
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                try {
                    cursor.close();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing cursor", e);
                }
            }
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
