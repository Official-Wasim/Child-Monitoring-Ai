package com.childmonitorai;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import android.os.Handler;
import android.os.Looper;
import com.google.firebase.storage.StorageException;

public class FirebaseStorageHelper {
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 5000; // 5 seconds
    private final FirebaseStorage storage;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public interface UploadCallback {
        void onSuccess(String downloadUrl);
        void onFailure(String error);
        default void onProgress(int progressPercent) {} // Optional progress updates
    }

    public FirebaseStorageHelper() {
        storage = FirebaseStorage.getInstance();
    }

    private String constructStoragePath(String userId, String phoneModel, String date, String filename) {
        return String.format("%s/%s/camera/%s/%s", userId, phoneModel, date, filename);
    }

    private String constructScreenshotPath(String userId, String phoneModel, String date, String filename) {
        return String.format("%s/%s/screenshot/%s/%s", userId, phoneModel, date, filename);
    }

    private String constructAudioPath(String userId, String phoneModel, String date, String filename) {
        return String.format("%s/%s/audio/%s/%s", userId, phoneModel, date, filename);
    }

    private void uploadWithRetry(String path, byte[] data, UploadCallback callback, int retryCount) {
        if (data == null || data.length == 0) {
            callback.onFailure("No data to upload");
            return;
        }

        StorageReference storageRef = storage.getReference().child(path);
        UploadTask uploadTask = storageRef.putBytes(data);

        uploadTask.addOnSuccessListener(taskSnapshot -> {
            storageRef.getDownloadUrl()
                .addOnSuccessListener(uri -> callback.onSuccess(uri.toString()))
                .addOnFailureListener(e -> handleUploadError(path, data, callback, retryCount, e));
        }).addOnFailureListener(e -> handleUploadError(path, data, callback, retryCount, e))
        .addOnProgressListener(taskSnapshot -> {
            double progress = (100.0 * taskSnapshot.getBytesTransferred()) / taskSnapshot.getTotalByteCount();
            callback.onProgress((int) progress);
        });
    }

    private void handleUploadError(String path, byte[] data, UploadCallback callback, int retryCount, Exception e) {
        if (retryCount < MAX_RETRIES && isRetryableError(e)) {
            handler.postDelayed(() -> 
                uploadWithRetry(path, data, callback, retryCount + 1), 
                RETRY_DELAY_MS * (retryCount + 1)
            );
        } else {
            callback.onFailure("Upload failed after " + retryCount + " retries: " + e.getMessage());
        }
    }

    private boolean isRetryableError(Exception e) {
        if (!(e instanceof StorageException)) {
            return false;
        }
        StorageException storageException = (StorageException) e;
        int errorCode = storageException.getErrorCode();
        return errorCode == StorageException.ERROR_RETRY_LIMIT_EXCEEDED ||
               errorCode == StorageException.ERROR_UNKNOWN;
    }

    public void uploadImage(String userId, String phoneModel, String date, String filename, 
                          byte[] imageData, UploadCallback callback) {
        if (!isValidInput(userId, phoneModel, date, filename)) {
            callback.onFailure("Invalid input parameters");
            return;
        }
        String path = constructStoragePath(userId, phoneModel, date, filename);
        uploadWithRetry(path, imageData, callback, 0);
    }

    public void uploadScreenshot(String userId, String phoneModel, String date, String filename, 
                               byte[] screenshotData, UploadCallback callback) {
        if (!isValidInput(userId, phoneModel, date, filename)) {
            callback.onFailure("Invalid input parameters");
            return;
        }
        String path = constructScreenshotPath(userId, phoneModel, date, filename);
        uploadWithRetry(path, screenshotData, callback, 0);
    }

    public void uploadAudio(String userId, String phoneModel, String date, String filename, 
                          byte[] audioData, UploadCallback callback) {
        if (!isValidInput(userId, phoneModel, date, filename)) {
            callback.onFailure("Invalid input parameters");
            return;
        }
        String path = constructAudioPath(userId, phoneModel, date, filename);
        uploadWithRetry(path, audioData, callback, 0);
    }

    private boolean isValidInput(String userId, String phoneModel, String date, String filename) {
        return userId != null && !userId.isEmpty() &&
               phoneModel != null && !phoneModel.isEmpty() &&
               date != null && !date.isEmpty() &&
               filename != null && !filename.isEmpty();
    }
}
