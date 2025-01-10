package com.childmonitorai;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;

public class PhotosMonitor {

    private static final String TAG = "PhotosMonitor";
    private Context context;
    private PhotosContentObserver photoContentObserver;
    private String userId;
    private String phoneModel;

    public PhotosMonitor(Context context) {
        this.context = context;
    }

    public void monitorPhotos(String userId, String phoneModel) {
        this.userId = userId;
        this.phoneModel = phoneModel;

        // Initialize the content observer and pass userId and phoneModel
        photoContentObserver = new PhotosContentObserver(new Handler(), context, userId, phoneModel);

        // Register the observer for the external content URI for photos
        Uri externalUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        context.getContentResolver().registerContentObserver(externalUri, true, photoContentObserver);  // Ensure this line is correct
        Log.d(TAG, "Photos monitoring started for userId: " + userId + ", phoneModel: " + phoneModel);
    }

    public void stopMonitoring() {
        // Unregister the observer when monitoring is stopped
        if (photoContentObserver != null) {
            context.getContentResolver().unregisterContentObserver(photoContentObserver);
            Log.d(TAG, "Photos monitoring stopped");
        }
    }
}
