package com.childmonitorai.helpers;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.childmonitorai.models.GeofenceData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FlaggedContents {
    private static final String TAG = "FlaggedContents";
    private static List<String> flaggedKeywords = new ArrayList<>();
    private static List<String> flaggedUrls = new ArrayList<>();
    private static boolean isInitialized = false;
    private static Map<String, GeofenceData> geofenceData = new HashMap<>();
    private static List<GeofenceDataListener> geofenceListeners = new ArrayList<>();
    private static Map<String, AppLimit> appLimits = new HashMap<>();
    private static List<AppLimitListener> appLimitListeners = new ArrayList<>();

    public interface GeofenceDataListener {
        void onGeofenceDataUpdated(List<GeofenceData> geofences);
    }

    public static class AppLimit {
        private String packageName;
        private String appName;
        private int hours;
        private int minutes;
        private long timestamp;

        public AppLimit(String packageName, String appName, int hours, int minutes, long timestamp) {
            this.packageName = packageName;
            this.appName = appName;
            this.hours = hours;
            this.minutes = minutes;
            this.timestamp = timestamp;
        }

        public long getTotalMinutes() {
            return (hours * 60L) + minutes;
        }

        public long getTimeInMillis() {
            return getTotalMinutes() * 60 * 1000;
        }

        // Getters
        public String getPackageName() { return packageName; }
        public String getAppName() { return appName; }
        public int getHours() { return hours; }
        public int getMinutes() { return minutes; }
        public long getTimestamp() { return timestamp; }
    }

    public interface AppLimitListener {
        void onAppLimitsUpdated(Map<String, AppLimit> limits);
        void onAppLimitChanged(String packageName, AppLimit limit);
        void onAppLimitRemoved(String packageName);
    }

    public static void addGeofenceListener(GeofenceDataListener listener) {
        if (!geofenceListeners.contains(listener)) {
            geofenceListeners.add(listener);
            // If we already have data, notify immediately
            if (!geofenceData.isEmpty()) {
                listener.onGeofenceDataUpdated(new ArrayList<>(geofenceData.values()));
            }
        }
    }

    public static void removeGeofenceListener(GeofenceDataListener listener) {
        geofenceListeners.remove(listener);
    }

    public static void addAppLimitListener(AppLimitListener listener) {
        if (!appLimitListeners.contains(listener)) {
            appLimitListeners.add(listener);
            if (!appLimits.isEmpty()) {
                listener.onAppLimitsUpdated(new HashMap<>(appLimits));
            }
        }
    }

    public static void removeAppLimitListener(AppLimitListener listener) {
        appLimitListeners.remove(listener);
    }

    public static void initialize() {
        if (isInitialized) return;
        
        String userId = getUserId();
        if (userId == null) return; // Don't initialize if user isn't signed in
        
        String phoneModel = getPhoneModel();
        
        // Initialize web flagged content
        initializeWebFlagged(userId, phoneModel);
        // Initialize geofences
        initializeGeofences(userId, phoneModel);
        // Initialize app limits
        initializeAppLimits(userId, phoneModel);
    }

    private static String getUserId() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            return auth.getCurrentUser().getUid();
        }
        return null;
    }

    private static String getPhoneModel() {
        return android.os.Build.MODEL;
    }

    // Add new interface for content updates
    public interface FlaggedContentListener {
        void onFlaggedContentUpdated(List<String> keywords, List<String> urls);
        void onFlaggedContentRemoved(String removedItem, String type);
    }

    private static List<FlaggedContentListener> contentListeners = new ArrayList<>();

    public static void addContentListener(FlaggedContentListener listener) {
        if (!contentListeners.contains(listener)) {
            contentListeners.add(listener);
            // Notify immediately if data exists
            if (!flaggedKeywords.isEmpty() || !flaggedUrls.isEmpty()) {
                listener.onFlaggedContentUpdated(
                    new ArrayList<>(flaggedKeywords), 
                    new ArrayList<>(flaggedUrls)
                );
            }
        }
    }

    public static void removeContentListener(FlaggedContentListener listener) {
        contentListeners.remove(listener);
    }

    private static void initializeWebFlagged(String userId, String phoneModel) {
        String webPath = String.format("users/%s/phones/%s/preferences/web_flagged", userId, phoneModel);
        DatabaseReference webRef = FirebaseDatabase.getInstance().getReference(webPath);

        webRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                List<String> oldKeywords = new ArrayList<>(flaggedKeywords);
                List<String> oldUrls = new ArrayList<>(flaggedUrls);
                
                flaggedKeywords.clear();
                flaggedUrls.clear();

                // Fetch keywords
                DataSnapshot keywordsSnapshot = dataSnapshot.child("keywords");
                for (DataSnapshot keywordSnap : keywordsSnapshot.getChildren()) {
                    String keyword = keywordSnap.getValue(String.class);
                    if (keyword != null) {
                        flaggedKeywords.add(keyword.toLowerCase());
                    }
                }

                // Fetch URLs
                DataSnapshot urlsSnapshot = dataSnapshot.child("urls");
                for (DataSnapshot urlSnap : urlsSnapshot.getChildren()) {
                    String url = urlSnap.getValue(String.class);
                    if (url != null) {
                        flaggedUrls.add(url.toLowerCase());
                    }
                }

                // Check for removed items
                for (String oldKeyword : oldKeywords) {
                    if (!flaggedKeywords.contains(oldKeyword)) {
                        notifyContentRemoved(oldKeyword, "keyword");
                    }
                }
                
                for (String oldUrl : oldUrls) {
                    if (!flaggedUrls.contains(oldUrl)) {
                        notifyContentRemoved(oldUrl, "url");
                    }
                }

                notifyContentUpdated();
                isInitialized = true;
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Error handling without logging
            }
        });

        // Listen for individual changes
        webRef.child("keywords").addChildEventListener(new ChildEventListenerAdapter() {
            @Override
            public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                String keyword = snapshot.getValue(String.class);
                if (keyword != null && !flaggedKeywords.contains(keyword.toLowerCase())) {
                    flaggedKeywords.add(keyword.toLowerCase());
                    notifyContentUpdated();
                }
            }

            @Override
            public void onChildRemoved(DataSnapshot snapshot) {
                String keyword = snapshot.getValue(String.class);
                if (keyword != null) {
                    flaggedKeywords.remove(keyword.toLowerCase());
                    notifyContentRemoved(keyword, "keyword");
                }
            }
        });

        webRef.child("urls").addChildEventListener(new ChildEventListenerAdapter() {
            @Override
            public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                String url = snapshot.getValue(String.class);
                if (url != null && !flaggedUrls.contains(url.toLowerCase())) {
                    flaggedUrls.add(url.toLowerCase());
                    notifyContentUpdated();
                }
            }

            @Override
            public void onChildRemoved(DataSnapshot snapshot) {
                String url = snapshot.getValue(String.class);
                if (url != null) {
                    flaggedUrls.remove(url.toLowerCase());
                    notifyContentRemoved(url, "url");
                }
            }
        });
    }

    private static void notifyContentUpdated() {
        for (FlaggedContentListener listener : contentListeners) {
            listener.onFlaggedContentUpdated(
                new ArrayList<>(flaggedKeywords), 
                new ArrayList<>(flaggedUrls)
            );
        }
    }

    private static void notifyContentRemoved(String item, String type) {
        for (FlaggedContentListener listener : contentListeners) {
            listener.onFlaggedContentRemoved(item, type);
        }
    }

    // Helper class to avoid implementing all methods of ChildEventListener
    private static class ChildEventListenerAdapter implements com.google.firebase.database.ChildEventListener {
        @Override
        public void onChildAdded(DataSnapshot snapshot, String previousChildName) {}
        @Override
        public void onChildChanged(DataSnapshot snapshot, String previousChildName) {}
        @Override
        public void onChildRemoved(DataSnapshot snapshot) {}
        @Override
        public void onChildMoved(DataSnapshot snapshot, String previousChildName) {}
        @Override
        public void onCancelled(DatabaseError error) {}
    }

    private static void initializeGeofences(String userId, String phoneModel) {
        String geofencePath = String.format("users/%s/phones/%s/preferences/geofences", userId, phoneModel);
        
        FirebaseDatabase.getInstance().getReference(geofencePath)
            .addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    geofenceData.clear();
                    List<GeofenceData> updatedGeofences = new ArrayList<>();

                    for (DataSnapshot fenceSnapshot : dataSnapshot.getChildren()) {
                        try {
                            GeofenceData fenceData = fenceSnapshot.getValue(GeofenceData.class);
                            if (fenceData != null) {
                                if (fenceData.getId() == null || fenceData.getId().isEmpty()) {
                                    fenceData.setId(fenceSnapshot.getKey());
                                }
                                if (fenceData.getName() == null || fenceData.getName().isEmpty()) {
                                    fenceData.setName("Unnamed Area " + (geofenceData.size() + 1));
                                }
                                
                                if (isValidGeofence(fenceData)) {
                                    geofenceData.put(fenceData.getId(), fenceData);
                                    updatedGeofences.add(fenceData);
                                }
                            }
                        } catch (Exception e) {
                            // Exception handling without logging
                        }
                    }
                    
                    if (!updatedGeofences.isEmpty()) {
                        for (GeofenceDataListener listener : geofenceListeners) {
                            listener.onGeofenceDataUpdated(updatedGeofences);
                        }
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    // Error handling without logging
                }
            });
    }

    private static void initializeAppLimits(String userId, String phoneModel) {
        String limitsPath = String.format("users/%s/phones/%s/preferences/app_limits", userId, phoneModel);
        DatabaseReference limitsRef = FirebaseDatabase.getInstance().getReference(limitsPath);
        limitsRef.keepSynced(true);

        limitsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Map<String, AppLimit> oldLimits = new HashMap<>(appLimits);
                appLimits.clear();

                for (DataSnapshot limitSnap : dataSnapshot.getChildren()) {
                    try {
                        String packageName = limitSnap.child("packageName").getValue(String.class);
                        if (packageName == null) continue;

                        String appName = limitSnap.child("appName").getValue(String.class);
                        Long hours = limitSnap.child("hours").getValue(Long.class);
                        Long minutes = limitSnap.child("minutes").getValue(Long.class);
                        Long timestamp = limitSnap.child("timestamp").getValue(Long.class);

                        if (hours == null) hours = 0L;
                        if (minutes == null) minutes = 0L;
                        if (timestamp == null) timestamp = System.currentTimeMillis();

                        AppLimit limit = new AppLimit(packageName, appName != null ? appName : packageName,
                            hours.intValue(), minutes.intValue(), timestamp);
                        appLimits.put(packageName, limit);
                    } catch (Exception e) {
                        // Exception handling without logging
                    }
                }

                notifyAppLimitsUpdated();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Error handling without logging
            }
        });
    }

    // Helper method to get current limits
    public static Map<String, AppLimit> getCurrentLimits() {
        return new HashMap<>(appLimits);
    }

    private static void updateAppLimitFromSnapshot(DataSnapshot snapshot) {
        try {
            String packageName = snapshot.child("packageName").getValue(String.class);
            String appName = snapshot.child("appName").getValue(String.class);
            Long hours = snapshot.child("hours").getValue(Long.class);
            Long minutes = snapshot.child("minutes").getValue(Long.class);
            Long timestamp = snapshot.child("timestamp").getValue(Long.class);

            if (packageName != null && hours != null && minutes != null) {
                AppLimit limit = new AppLimit(
                    packageName,
                    appName != null ? appName : packageName,
                    hours.intValue(),
                    minutes.intValue(),
                    timestamp != null ? timestamp : System.currentTimeMillis()
                );
                appLimits.put(packageName, limit);
                notifyAppLimitChanged(packageName, limit);
            }
        } catch (Exception e) {
            // Exception handling without logging
        }
    }

    private static void notifyAppLimitsUpdated() {
        for (AppLimitListener listener : appLimitListeners) {
            listener.onAppLimitsUpdated(new HashMap<>(appLimits));
        }
    }

    private static void notifyAppLimitChanged(String packageName, AppLimit limit) {
        for (AppLimitListener listener : appLimitListeners) {
            listener.onAppLimitChanged(packageName, limit);
        }
    }

    private static void notifyAppLimitRemoved(String packageName) {
        for (AppLimitListener listener : appLimitListeners) {
            listener.onAppLimitRemoved(packageName);
        }
    }

    public static AppLimit getAppLimit(String packageName) {
        return appLimits.get(packageName);
    }

    public static long getAppLimitInMillis(String packageName) {
        AppLimit limit = appLimits.get(packageName);
        return limit != null ? limit.getTimeInMillis() : 0;
    }

    private static boolean isValidGeofence(GeofenceData fence) {
        return fence != null 
            && fence.getId() != null 
            && !fence.getId().isEmpty()
            && fence.getRadius() > 0;
    }

    public static boolean isFlaggedContent(String url) {
        if (url == null) return false;
        
        String lowerUrl = url.toLowerCase();
        
        // Check against keywords
        for (String keyword : flaggedKeywords) {
            if (lowerUrl.contains(keyword)) {
                return true;
            }
        }
        
        // Check against specific URLs
        for (String flaggedUrl : flaggedUrls) {
            if (lowerUrl.contains(flaggedUrl)) {
                return true;
            }
        }
        
        return false;
    }

    public static GeofenceData getGeofenceById(String id) {
        return geofenceData.get(id);
    }
}
