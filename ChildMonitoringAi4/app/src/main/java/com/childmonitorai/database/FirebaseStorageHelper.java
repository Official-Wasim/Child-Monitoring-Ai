package com.childmonitorai.database;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FirebaseStorageHelper {
    private final FirebaseStorage storage;
    
    public interface ScreenshotCallback {
        void onSuccess(String downloadUrl);
        void onFailure(String error);
    }

    public interface CaptureCallback {
        void onSuccess(String downloadUrl);
        void onFailure(String error);
    }

    public interface AudioCallback {
        void onSuccess(String downloadUrl);
        void onFailure(String error);
    }

    public interface PhotoCallback {
        void onSuccess(String downloadUrl);
        void onFailure(String error);
    }

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

    public void uploadScreenshot(String userId, String phoneModel, byte[] screenshotData) {
        if (userId == null || phoneModel == null || screenshotData == null) {
            return;
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "screenshot_" + timestamp + ".jpg";
        String path = String.format("%s/%s/periodic_screenshots/%s", userId, phoneModel, fileName);

        StorageReference storageRef = storage.getReference().child(path);
        UploadTask uploadTask = storageRef.putBytes(screenshotData);
        
        uploadTask.addOnSuccessListener(taskSnapshot -> {
            // Screenshot uploaded successfully
        }).addOnFailureListener(exception -> {
            // Handle failed upload
        });
    }

    public void uploadCommandScreenshot(String userId, String phoneModel, byte[] screenshotData, String date, String timestamp, ScreenshotCallback callback) {
        if (userId == null || phoneModel == null || screenshotData == null) {
            if (callback != null) {
                callback.onFailure("Invalid parameters");
            }
            return;
        }

        String fileName = "screenshot_" + date + "_" + timestamp + ".jpg";
        String path = String.format("%s/%s/screenshot_commands/%s", userId, phoneModel, fileName);

        StorageReference storageRef = storage.getReference().child(path);
        UploadTask uploadTask = storageRef.putBytes(screenshotData);
        
        uploadTask.addOnSuccessListener(taskSnapshot -> {
            storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                if (callback != null) {
                    callback.onSuccess(uri.toString());
                }
            });
        }).addOnFailureListener(exception -> {
            if (callback != null) {
                callback.onFailure(exception.getMessage());
            }
        });
    }

    public void uploadCapture(String userId, String phoneModel, byte[] captureData, String date, String timestamp, CaptureCallback callback) {
        if (userId == null || phoneModel == null || captureData == null) {
            if (callback != null) {
                callback.onFailure("Invalid parameters");
            }
            return;
        }

        String fileName = "capture_" + date + "_" + timestamp + ".jpg";
        // Include date in the path structure
        String path = String.format("%s/%s/camera_capture/%s/%s",
            userId,
            phoneModel,
            date,  // Add date folder
            fileName
        );

        StorageReference storageRef = storage.getReference().child(path);
        UploadTask uploadTask = storageRef.putBytes(captureData);
        
        uploadTask.addOnSuccessListener(taskSnapshot -> {
            storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                if (callback != null) {
                    callback.onSuccess(uri.toString());
                }
            });
        }).addOnFailureListener(exception -> {
            if (callback != null) {
                callback.onFailure(exception.getMessage());
            }
        });
    }

    public void uploadAudio(String userId, String phoneModel, byte[] audioData, String date, String timestamp, AudioCallback callback) {
        if (userId == null || phoneModel == null || audioData == null) {
            if (callback != null) {
                callback.onFailure("Invalid parameters");
            }
            return;
        }

        String fileName = "audio_" + date + "_" + timestamp + ".mp3";
        // Include date in the path structure
        String path = String.format("%s/%s/audio_record/%s/%s",
            userId,
            phoneModel,
            date,  // Add date folder
            fileName
        );

        StorageReference storageRef = storage.getReference().child(path);
        UploadTask uploadTask = storageRef.putBytes(audioData);
        
        uploadTask.addOnSuccessListener(taskSnapshot -> {
            storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                if (callback != null) {
                    callback.onSuccess(uri.toString());
                }
            });
        }).addOnFailureListener(exception -> {
            if (callback != null) {
                callback.onFailure(exception.getMessage());
            }
        });
    }

    public void uploadPhoto(String userId, String phoneModel, String localPath, PhotoCallback callback) {
        if (userId == null || phoneModel == null || localPath == null) {
            if (callback != null) {
                callback.onFailure("Invalid parameters");
            }
            return;
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "photo_" + timestamp + "_" + new File(localPath).getName();
        String path = String.format("%s/%s/photos/%s", userId, phoneModel, fileName);

        StorageReference storageRef = storage.getReference().child(path);
        
        try {
            byte[] imageData = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                imageData = Files.readAllBytes(Paths.get(localPath));
            }
            UploadTask uploadTask = storageRef.putBytes(imageData);
            
            uploadTask.addOnSuccessListener(taskSnapshot -> {
                storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    if (callback != null) {
                        callback.onSuccess(uri.toString());
                    }
                });
            }).addOnFailureListener(exception -> {
                if (callback != null) {
                    callback.onFailure(exception.getMessage());
                }
            });
        } catch (IOException e) {
            if (callback != null) {
                callback.onFailure("Failed to read image file: " + e.getMessage());
            }
        }
    }

    public void uploadNSFWPhoto(String originalPath, byte[] compressedData, String userId, String phoneModel, PhotoCallback callback) {
        if (userId == null || phoneModel == null) {
            if (callback != null) {
                callback.onFailure("Missing userId or phoneModel");
            }
            return;
        }

        String[] pathParts = originalPath.split("/");
        String fileName = pathParts[pathParts.length - 1];
        
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String date = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
        String newFileName = "nsfw_" + timestamp + "_" + fileName;
        
        // Include date in the path structure
        String path = String.format("%s/%s/nsfw_detected/%s/%s", 
            userId, 
            phoneModel,
            date,  // Add date folder
            newFileName
        );
        
        StorageReference storageRef = storage.getReference().child(path);
        
        UploadTask uploadTask = storageRef.putBytes(compressedData);
        uploadTask.addOnSuccessListener(taskSnapshot -> {
            storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                if (callback != null) {
                    callback.onSuccess(uri.toString());
                }
            });
        }).addOnFailureListener(exception -> {
            if (callback != null) {
                callback.onFailure(exception.getMessage());
            }
        });
    }
}
