package com.childmonitorai;

import android.app.Activity;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Toast;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.view.WindowManager;
import android.os.Looper;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class ScreenshotMonitor {
    private static final long SCREENSHOT_INTERVAL = 15 * 60 * 1000; // 15 minutes
    private static final int REQUEST_CODE = 1000;
    private static final int PERMISSION_REQUEST_CODE = 2000;
    private final Context context;
    private final Handler handler;
    private final FirebaseStorageHelper storageHelper;
    private boolean isMonitoring = false;
    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private SurfaceView surfaceView;
    private Surface surface;
    private int screenDensity;
    private int displayWidth;
    private int displayHeight;
    private FirebaseAuth mAuth;
    private ImageReader imageReader;
    private ScreenshotHelper screenshotHelper;

    private final Runnable screenshotRunnable = new Runnable() {
        @Override
        public void run() {
            takeScreenshot();
            if (isMonitoring) {
                handler.postDelayed(this, SCREENSHOT_INTERVAL);
            }
        }
    };

    public ScreenshotMonitor(Context context) {
        this.context = context;
        this.handler = new Handler(Looper.getMainLooper());
        this.storageHelper = new FirebaseStorageHelper();
        this.screenshotHelper = new ScreenshotHelper(context);
        
        mediaProjectionManager = (MediaProjectionManager) context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        screenDensity = metrics.densityDpi;
        
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        displayWidth = display.getWidth();
        displayHeight = display.getHeight();
        
        imageReader = ImageReader.newInstance(displayWidth, displayHeight, PixelFormat.RGBA_8888, 2);
        mAuth = FirebaseAuth.getInstance();
    }

    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
        } else {
            // If context is not an instance of Activity, use a different approach to request permissions
            Intent intent = new Intent(context, PermissionRequestActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
        // Request screenshot permission
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            activity.startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_CODE);
        }
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(context, "Permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, "Permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void setupVirtualDisplay() {
        if (mediaProjection != null) {
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenshotMonitor",
                displayWidth, displayHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null);
        }
    }

    private void takeScreenshot() {
        if (!checkPermissions()) {
            requestPermissions();
            return;
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(context, "No user signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        if (mediaProjection == null || virtualDisplay == null) {
            requestMediaProjectionPermission();
            return;
        }

        try {
            String userId = currentUser.getUid();
            String deviceModel = android.os.Build.MODEL;
            SimpleDateFormat folderDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat fileFormat = new SimpleDateFormat("HHmmss", Locale.getDefault());
            Date now = new Date();
            
            String folderDate = folderDateFormat.format(now);
            String fileName = "screenshot_" + fileFormat.format(now) + ".jpg";

            // Ensure virtual display is set up
            if (virtualDisplay == null) {
                setupVirtualDisplay();
            }

            // Take screenshot using ImageReader
            Image image = imageReader.acquireLatestImage();
            if (image != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try {
                    Bitmap bitmap = imageToBitmap(image);
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
                    byte[] screenshotData = baos.toByteArray();

                    storageHelper.uploadScreenshot(userId, deviceModel, folderDate, fileName, 
                        screenshotData, new FirebaseStorageHelper.UploadCallback() {
                            @Override
                            public void onSuccess(String downloadUrl) {
                                handler.post(() -> Log.d("ScreenshotMonitor", "Screenshot uploaded: " + downloadUrl));
                            }

                            @Override
                            public void onFailure(String error) {
                                handler.post(() -> Log.e("ScreenshotMonitor", "Screenshot upload failed: " + error));
                            }
                        });
                } finally {
                    image.close();
                    baos.close();
                }
            }
        } catch (Exception e) {
            Log.e("ScreenshotMonitor", "Error taking screenshot: " + e.getMessage());
        }
    }

    private Bitmap imageToBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * displayWidth;

        Bitmap bitmap = Bitmap.createBitmap(displayWidth + rowPadding / pixelStride,
                displayHeight, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        return bitmap;
    }

    private void requestMediaProjectionPermission() {
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            activity.startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), REQUEST_CODE);
        } else {
            Toast.makeText(context, "Context is not an instance of Activity", Toast.LENGTH_SHORT).show();
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
            if (mediaProjection != null) {
                // Initialize screenshot components
                setupVirtualDisplay();
                screenshotHelper.setMediaProjection(mediaProjection);
                startMonitoring();
            }
        } else {
            Toast.makeText(context, "Screenshot permission denied", Toast.LENGTH_SHORT).show();
        }
    }

    private void startVirtualDisplay() {
        virtualDisplay = mediaProjection.createVirtualDisplay("ScreenshotMonitor",
                displayWidth, displayHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface, null, null);
    }

    private Bitmap captureScreen() {
        // Check if surfaceView is not null
        if (surfaceView == null) {
            return null;
        }

        // Enable drawing cache
        surfaceView.setDrawingCacheEnabled(true);
        surfaceView.buildDrawingCache();

        // Get the bitmap from the drawing cache
        Bitmap bitmap = surfaceView.getDrawingCache();
        if (bitmap != null) {
            // Create a copy of the bitmap to avoid issues with the drawing cache
            bitmap = Bitmap.createBitmap(bitmap);
        }

        // Disable drawing cache
        surfaceView.setDrawingCacheEnabled(false);

        return bitmap;
    }

    public void startMonitoring() {
        if (!isMonitoring) {
            isMonitoring = true;
            handler.post(screenshotRunnable);
        }
    }

    public void stopMonitoring() {
        isMonitoring = false;
        handler.removeCallbacks(screenshotRunnable);
        if (mediaProjection != null) {
            mediaProjection.stop();
        }
    }

    @Override
    protected void finalize() throws Throwable {
        stopMonitoring();
        if (imageReader != null) {
            imageReader.close();
        }
        super.finalize();
    }
}
