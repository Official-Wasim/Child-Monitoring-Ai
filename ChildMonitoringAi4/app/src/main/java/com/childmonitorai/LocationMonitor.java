package com.childmonitorai;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
public class LocationMonitor {
    private static final String TAG = "LocationMonitor";
    private static final long LOCATION_UPDATE_INTERVAL = 15 * 60 * 1000; // 15 minutes in milliseconds
    private Context context;
    private String userId;
    private String phoneModel;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private Handler handler;
    private long lastLocationUploadTime = 0; // To track the last time location data was uploaded

    public LocationMonitor(Context context, String userId, String phoneModel) {
        this.context = context;
        this.userId = userId;
        this.phoneModel = phoneModel;
        this.handler = new Handler(Looper.getMainLooper());
    }

    public void startMonitoring() {
        // Check for location permissions
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permissions not granted. Location monitoring cannot start.");
            return;
        }

        // Initialize LocationManager and LocationListener
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                // Do not upload location every time it changes, just use it for periodic upload
                // If location is received, update last known location
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
                // Not used in modern Android versions
            }

            @Override
            public void onProviderEnabled(String provider) {
                Log.d(TAG, "Provider enabled: " + provider);
            }

            @Override
            public void onProviderDisabled(String provider) {
                Log.e(TAG, "Provider disabled: " + provider);
            }
        };

        // Request location updates (only for the first time to initialize)
        try {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    0,
                    0,
                    locationListener,
                    Looper.getMainLooper()
            );

            locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    0,
                    0,
                    locationListener,
                    Looper.getMainLooper()
            );
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied: " + e.getMessage());
        }

        // Start periodic location uploads
        handler.post(locationFetchRunnable);
    }

    public void stopMonitoring() {
        // Stop location updates and periodic tasks
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
        }
        handler.removeCallbacks(locationFetchRunnable);
    }

    private final Runnable locationFetchRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                // Fetch the last known location
                Location lastKnownLocation = null;

                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }

                if (lastKnownLocation != null) {
                    long currentTime = System.currentTimeMillis();

                    // Check if 15 minutes have passed since last upload
                    if (currentTime - lastLocationUploadTime >= LOCATION_UPDATE_INTERVAL) {
                        uploadLocationData(lastKnownLocation);
                        lastLocationUploadTime = currentTime; // Update the last upload time
                    }
                } else {
                    Log.w(TAG, "No last known location available.");
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Permission denied: " + e.getMessage());
            }

            // Schedule the next location fetch
            handler.postDelayed(this, LOCATION_UPDATE_INTERVAL);
        }
    };

    private void uploadLocationData(Location location) {
        String timestamp = String.valueOf(location.getTime()); // Use timestamp as unique location ID
        String locationDate = getDateFromTimestamp(timestamp); // Method to convert timestamp to date format (e.g., yyyy-MM-dd)
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();
        float accuracy = location.getAccuracy();

        // Create location data map
        Map<String, Object> locationData = new HashMap<>();
        locationData.put("timestamp", timestamp);
        locationData.put("latitude", latitude);
        locationData.put("longitude", longitude);
        locationData.put("accuracy", accuracy);

        // Reference path for location data
        DatabaseHelper dbHelper = new DatabaseHelper();
        dbHelper.uploadLocationDataByDate(userId, phoneModel, locationData, timestamp, locationDate);
    }

    // Method to convert timestamp to date format (e.g., yyyy-MM-dd)
    private String getDateFromTimestamp(String timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Date date = new Date(Long.parseLong(timestamp));
        return sdf.format(date);
    }
}


