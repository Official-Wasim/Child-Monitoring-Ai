package com.childmonitorai;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

public class FirebaseStorageHelper {
    private final FirebaseStorage storage;

    public FirebaseStorageHelper() {
        storage = FirebaseStorage.getInstance();
    }

    public void uploadImage(String path, byte[] imageData) {
        StorageReference storageRef = storage.getReference().child(path);
        UploadTask uploadTask = storageRef.putBytes(imageData);
        uploadTask.addOnSuccessListener(taskSnapshot -> {
            // Handle successful upload
        }).addOnFailureListener(exception -> {
            // Handle failed upload
        });
    }
}
