package com.childmonitorai;

import android.net.Uri;
import android.util.Log;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class DatabaseStorageHelper {

    private static final String TAG = "DatabaseStorageHelper";
    private final FirebaseDatabase firebaseDatabase;
    private final FirebaseStorage firebaseStorage;

    public DatabaseStorageHelper() {
        this.firebaseDatabase = FirebaseDatabase.getInstance();
        this.firebaseStorage = FirebaseStorage.getInstance();
    }

    public void uploadPhotoByDate(String userId, String phoneModel, File photoFile, long timestamp) {
        try {
            // Upload photo to Firebase Storage
            StorageReference storageReference = firebaseStorage.getReference()
                    .child("photos")
                    .child(userId)
                    .child(phoneModel)
                    .child(photoFile.getName());

            // Upload photo to Firebase Storage
            storageReference.putFile(Uri.fromFile(photoFile))
                    .addOnSuccessListener(taskSnapshot -> {
                        storageReference.getDownloadUrl().addOnSuccessListener(uri -> {
                            // Get the download URL of the uploaded photo
                            String photoUrl = uri.toString();
                            Log.d(TAG, "Photo uploaded successfully: " + photoUrl);

                            // Create the photo data object
                            PhotosData photoData = new PhotosData(photoFile.getName(), photoUrl, photoFile.length(), timestamp);

                            // Upload photo data to Firebase Realtime Database
                            DatabaseReference databaseReference = firebaseDatabase.getReference()
                                    .child("users")
                                    .child(userId)
                                    .child("phones")
                                    .child(phoneModel)
                                    .child("photos");

                            String photoId = databaseReference.push().getKey();
                            if (photoId != null) {
                                Map<String, Object> photoMap = new HashMap<>();
                                photoMap.put(photoId, photoData);
                                databaseReference.updateChildren(photoMap)
                                        .addOnSuccessListener(aVoid -> Log.d(TAG, "Photo data uploaded successfully"))
                                        .addOnFailureListener(e -> Log.e(TAG, "Failed to upload photo data", e));
                            }
                        });
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to upload photo: ", e));
        } catch (Exception e) {
            Log.e(TAG, "Error uploading photo: ", e);
        }
    }
}
