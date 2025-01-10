package com.childmonitorai;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;

public class PhotosContentObserver extends ContentObserver {

    private static final String TAG = "PhotosContentObserver";
    private Context context;
    private String userId;
    private String phoneModel;

    public PhotosContentObserver(Handler handler, Context context, String userId, String phoneModel) {
        super(handler);
        this.context = context;
        this.userId = userId;
        this.phoneModel = phoneModel;
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        super.onChange(selfChange, uri);
        if (uri != null) {
            Log.d(TAG, "New photo added: " + uri.toString());
            handleNewPhoto(uri);
        }
    }

    private void handleNewPhoto(Uri photoUri) {
        Cursor cursor = null;
        try {
            // Query the photo details (path, timestamp)
            cursor = context.getContentResolver().query(photoUri,
                    new String[]{MediaStore.Images.Media.DATA, MediaStore.Images.Media.DATE_ADDED},
                    null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                // Get the index of the columns
                int photoPathIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
                int dateAddedIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED);

                // Check if the columns exist
                if (photoPathIndex >= 0 && dateAddedIndex >= 0) {
                    String photoPath = cursor.getString(photoPathIndex);
                    long dateAdded = cursor.getLong(dateAddedIndex);
                    Log.d(TAG, "New photo added: " + photoPath + ", Date added: " + dateAdded);

                    // Create a photo file
                    File photoFile = new File(photoPath);

                    // Upload the photo and data using DatabaseStorageHelper
                    DatabaseStorageHelper databaseStorageHelper = new DatabaseStorageHelper();
                    databaseStorageHelper.uploadPhotoByDate(userId, phoneModel, photoFile, dateAdded);
                } else {
                    Log.e(TAG, "Required columns are missing in the cursor.");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling new photo: ", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
}
