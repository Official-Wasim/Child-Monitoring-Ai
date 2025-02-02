package com.childmonitorai;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PhotosMonitor extends BaseContentObserver {
    private static final String TAG = "PhotosMonitor";
    private final FirebaseStorageHelper firebaseStorageHelper;

    public PhotosMonitor(Context context) {
        super(context);
        firebaseStorageHelper = new FirebaseStorageHelper();
        
        if (hasStoragePermission()) {
            registerPhotoObserver();
            Log.d(TAG, "PhotosMonitor initialized and watching for new photos");
            // Initial check for existing photos
            checkForNewPhotos();
        } else {
            Log.e(TAG, "Storage permission not granted!");
        }
    }

    private boolean hasStoragePermission() {
        Context context = getContext();
        return context != null && ContextCompat.checkSelfPermission(context,
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void registerPhotoObserver() {
        registerObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
    }

    @Override
    protected void onContentChanged(Uri uri) {
        Log.d(TAG, "Content change detected for URI: " + uri);
        if (uri.equals(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)) {
            Log.d(TAG, "New photo detected - checking details");
            checkForNewPhotos();
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

        try (Cursor cursor = context.getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder)) {

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

                    } while (cursor.moveToNext());
                } else {
                    Log.d(TAG, "No photos found in query");
                }
            } else {
                Log.e(TAG, "Cursor is null");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking for photos: " + e.getMessage(), e);
        }
    }
}
