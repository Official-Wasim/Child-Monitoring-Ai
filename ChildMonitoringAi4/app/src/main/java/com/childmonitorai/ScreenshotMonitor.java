package com.childmonitorai;

import android.app.Activity;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.util.DisplayMetrics;
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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

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
        mediaProjectionManager = (MediaProjectionManager) context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        screenDensity = metrics.densityDpi;
        Display display = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        displayWidth = display.getWidth();
        displayHeight = display.getHeight();
        surfaceView = new SurfaceView(context);
        surface = surfaceView.getHolder().getSurface();
        if (!checkPermissions()) {
            requestPermissions();
        }
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

    private void takeScreenshot() {
        if (!checkPermissions()) {
            requestPermissions();
            return;
        }
        // Get current timestamp
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "screenshot_" + timestamp + ".jpg";
        
        if (mediaProjection == null) {
            requestMediaProjectionPermission();
            return;
        }
        startVirtualDisplay();
        
        // Capture screenshot using MediaProjection
        Bitmap screenshot = captureScreen();
        
        if (screenshot != null) {
            // Convert bitmap to byte array
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            screenshot.compress(Bitmap.CompressFormat.JPEG, 100, baos);
            byte[] imageData = baos.toByteArray();

            // Upload to Firebase Storage
            String deviceModel = android.os.Build.MODEL;
            String path = "screenshots/" + deviceModel + "/" + fileName;
            storageHelper.uploadImage(path, imageData);
        }
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
        if (requestCode == REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
                // Store the intent data for later use when taking screenshots
                context.getSharedPreferences("ScreenshotPrefs", Context.MODE_PRIVATE)
                    .edit()
                    .putInt("resultCode", resultCode)
                    .putString("intentData", data.toUri(Intent.URI_INTENT_SCHEME))
                    .apply();
                takeScreenshot();
            } else {
                Toast.makeText(context, "Permission denied", Toast.LENGTH_SHORT).show();
            }
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
}
