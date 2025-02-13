package com.childmonitorai.monitors;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;

import com.childmonitorai.helpers.PermissionHelper;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class OnRefreshStatsMonitor {
    private Context context;
    private DatabaseReference databaseReference;
    private DatabaseReference connectedRef;
    private boolean isFirebaseConnected = false;

    public OnRefreshStatsMonitor(Context context) {
        this.context = context;
        this.databaseReference = FirebaseDatabase.getInstance().getReference();
        this.connectedRef = FirebaseDatabase.getInstance().getReference(".info/connected");
        setupConnectivityListener();
    }

    private void setupConnectivityListener() {
        connectedRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                isFirebaseConnected = snapshot.getValue(Boolean.class) != null && snapshot.getValue(Boolean.class);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                isFirebaseConnected = false;
            }
        });
    }

    public void startMonitoring(String userId, String phoneModel) {
        DatabaseReference refreshRef = databaseReference
            .child("users")
            .child(userId)
            .child("phones")
            .child(phoneModel)
            .child("on_refresh")
            .child("refresh_requested");

        refreshRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Long timestamp = dataSnapshot.getValue(Long.class);
                if (timestamp != null) {
                    updateRefreshResult(userId, phoneModel);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle error
            }
        });
    }

    private void updateRefreshResult(String userId, String phoneModel) {
        Location location = getLastKnownLocation();
        
        // Only include location data if a valid location is found
        if (location != null) {
            RefreshResult result = new RefreshResult(
                getBatteryPercentage(),
                getBatteryChargingStatus(),
                isFirebaseConnected,
                getDetailedConnectionInfo(),
                System.currentTimeMillis(),
                location.getLatitude(),
                location.getLongitude(),
                location.getAccuracy(),
                location.getTime()
            );

            databaseReference
                .child("users")
                .child(userId)
                .child("phones")
                .child(phoneModel)
                .child("on_refresh")
                .child("refresh_result")
                .setValue(result);
        } else {
            // Create result without location data
            Map<String, Object> partialResult = new HashMap<>();
            partialResult.put("batteryLevel", getBatteryPercentage());
            partialResult.put("chargingStatus", getBatteryChargingStatus());
            partialResult.put("isConnected", isFirebaseConnected);
            partialResult.put("connectionInfo", getDetailedConnectionInfo());
            partialResult.put("timestamp", System.currentTimeMillis());

            // Update only non-location fields
            databaseReference
                .child("users")
                .child(userId)
                .child("phones")
                .child(phoneModel)
                .child("on_refresh")
                .child("refresh_result")
                .updateChildren(partialResult);
        }
    }

    private Location getLastKnownLocation() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }

        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        Location location = null;

        try {
            // Try GPS first
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (location != null && isAccurateEnough(location)) {
                    return location;
                }
            }
            
            // Fall back to network provider if GPS is disabled or didn't provide accurate location
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
        } catch (SecurityException e) {
            // Handle permission error
        }
        
        return location;
    }

    private boolean isAccurateEnough(Location location) {
        if (location == null) return false;
        long locationAge = System.currentTimeMillis() - location.getTime();
        return locationAge < 5 * 60 * 1000 && location.getAccuracy() < 100;
    }

    private int getBatteryPercentage() {
        IntentFilter iFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, iFilter);
        
        int level = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) : -1;
        int scale = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1) : -1;
        
        return level != -1 && scale != -1 ? (int)((level / (float)scale) * 100) : -1;
    }

    private String getBatteryChargingStatus() {
        IntentFilter iFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, iFilter);
        
        int status = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1) : -1;
        
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING: return "Charging";
            case BatteryManager.BATTERY_STATUS_FULL: return "Full";
            case BatteryManager.BATTERY_STATUS_DISCHARGING: return "Discharging";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "Not Charging";
            default: return "Unknown";
        }
    }

    private String getDetailedConnectionInfo() {
        if (!isFirebaseConnected) {
            return "Offline";
        }

        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        
        if (activeNetwork != null) {
            if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
                if (PermissionHelper.isWifiPermissionGranted(context)) {
                    WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                    return "WiFi: " + (wifiInfo.getSSID() != null ? wifiInfo.getSSID().replace("\"", "") : "unknown");
                } else {
                    return "WiFi: Permission not granted";
                }
            } else if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) {
                return "Mobile Data: " + activeNetwork.getSubtypeName();
            }
        }
        return "Unknown";
    }

    private static class RefreshResult {
        public int batteryLevel;
        public String chargingStatus;
        public boolean isConnected;
        public String connectionInfo;
        public long timestamp;
        public double location_latitude;
        public double location_longitude;
        public float location_accuracy;
        public long location_timestamp;

        public RefreshResult(int batteryLevel, String chargingStatus, boolean isConnected, 
                           String connectionInfo, long timestamp, double latitude,
                           double longitude, float accuracy, long locationTimestamp) {
            this.batteryLevel = batteryLevel;
            this.chargingStatus = chargingStatus;
            this.isConnected = isConnected;
            this.connectionInfo = connectionInfo;
            this.timestamp = timestamp;
            this.location_latitude = latitude;
            this.location_longitude = longitude;
            this.location_accuracy = accuracy;
            this.location_timestamp = locationTimestamp;
        }
    }
}
