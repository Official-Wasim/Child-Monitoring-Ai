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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;
import com.childmonitorai.models.GeofenceData;

import java.util.ArrayList;
import java.util.List;

public class GeoFenceMonitor implements FlaggedContents.GeofenceDataListener {
    private static final String TAG = "GeoFenceMonitor";
    private final GeofencingClient geofencingClient;
    private final Context context;
    private PendingIntent geofencePendingIntent;
    private final FirebaseDatabase database;
    private final String userId;
    private final String phoneModel;

    public GeoFenceMonitor(Context context) {
        this.context = context;
        this.geofencingClient = LocationServices.getGeofencingClient(context);
        this.database = FirebaseDatabase.getInstance();
        this.userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        this.phoneModel = android.os.Build.MODEL;
        
        // Register for geofence updates
        FlaggedContents.addGeofenceListener(this);
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

    @Override
    public void onGeofenceDataUpdated(List<GeofenceData> geofences) {
        // Remove existing geofences first
        if (geofencePendingIntent != null) {
            geofencingClient.removeGeofences(geofencePendingIntent)
                .addOnCompleteListener(task -> {
                    // Add new geofences after removal
                    if (!geofences.isEmpty()) {
                        startGeofencing(convertToGeofenceList(geofences));
                    }
                });
        } else {
            // If no existing geofences, just add new ones
            if (!geofences.isEmpty()) {
                startGeofencing(convertToGeofenceList(geofences));
            }
        }
    }

    private List<Geofence> convertToGeofenceList(List<GeofenceData> geofences) {
        List<Geofence> geofenceList = new ArrayList<>();
        for (GeofenceData fence : geofences) {
            // Skip if ID is null
            if (fence.getId() == null || fence.getId().isEmpty()) {
                Log.w(TAG, "Skipping geofence with null/empty ID");
                continue;
            }
            
            try {
                Geofence geofence = new Geofence.Builder()
                    .setRequestId(fence.getId())
                    .setCircularRegion(
                        fence.getLatitude(), 
                        fence.getLongitude(), 
                        fence.getRadius()
                    )
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER | 
                                      Geofence.GEOFENCE_TRANSITION_EXIT)
                    .build();
                geofenceList.add(geofence);
                Log.d(TAG, "Added geofence with ID: " + fence.getId());
            } catch (Exception e) {
                Log.e(TAG, "Error creating geofence: " + e.getMessage());
            }
        }
        return geofenceList;
    }

    public void startGeofencing() {
        if (!hasRequiredPermissions() || !isLocationEnabled()) {
            Log.e(TAG, "Missing permissions or location disabled");
            return;
        }
        // The actual geofence creation will happen through the listener callback
        FlaggedContents.initialize();
    }

    private void startGeofencing(List<Geofence> geofenceList) {
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
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Geofences added successfully"))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to add geofences", e));
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
