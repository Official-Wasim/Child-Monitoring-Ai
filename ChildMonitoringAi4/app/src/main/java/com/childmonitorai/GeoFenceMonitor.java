package com.childmonitorai;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;

import java.util.ArrayList;
import java.util.List;

public class GeoFenceMonitor {
    private static final String TAG = "GeoFenceMonitor";
    private final GeofencingClient geofencingClient;
    private final Context context;
    private PendingIntent geofencePendingIntent;

    // Hardcoded geofence data (replace with data from parent's device later)
    private static final double FENCE_LATITUDE = 19.041750; // Example: Mumbai latitude
    private static final double FENCE_LONGITUDE = 72.858836; // Example: Mumbai longitude
    private static final float FENCE_RADIUS = 200; // 200 meters radius

    public GeoFenceMonitor(Context context) {
        this.context = context;
        this.geofencingClient = LocationServices.getGeofencingClient(context);
    }

    public boolean hasRequiredPermissions() {
        boolean hasFineLocation = ActivityCompat.checkSelfPermission(context, 
            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarseLocation = ActivityCompat.checkSelfPermission(context, 
            Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            boolean hasBackgroundLocation = ActivityCompat.checkSelfPermission(context, 
                Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
            return hasFineLocation && hasCoarseLocation && hasBackgroundLocation;
        }
        
        return hasFineLocation && hasCoarseLocation;
    }

    public boolean isLocationEnabled() {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        return locationManager != null && 
               (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || 
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
    }

    public void startGeofencing() {
        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Missing required permissions for geofencing");
            return;
        }

        if (!isLocationEnabled()) {
            Log.e(TAG, "Location services are disabled - cannot start geofencing");
            return;
        }

        List<Geofence> geofenceList = new ArrayList<>();
        geofenceList.add(new Geofence.Builder()
                .setRequestId("HOME_FENCE")
                .setCircularRegion(FENCE_LATITUDE, FENCE_LONGITUDE, FENCE_RADIUS)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | Geofence.GEOFENCE_TRANSITION_EXIT)
                .build());

        GeofencingRequest geofencingRequest = new GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofences(geofenceList)
                .build();

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_MUTABLE;
        }

        geofencePendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                new Intent(context, GeofenceBroadcastReceiver.class),
                flags
        );

        try {
            geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Geofence added successfully"))
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to add geofence", e);
                        if (e instanceof com.google.android.gms.common.api.ApiException) {
                            int statusCode = ((com.google.android.gms.common.api.ApiException) e).getStatusCode();
                            if (statusCode == 1000) {
                                Log.e(TAG, "Location services are not enabled - geofencing failed");
                            }
                        }
                    });
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception: " + e.getMessage());
        }
    }

    public void stopGeofencing() {
        if (geofencePendingIntent != null) {
            geofencingClient.removeGeofences(geofencePendingIntent)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Geofence removed successfully"))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to remove geofence", e));
        }
    }
}
