package com.childmonitorai.helpers;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.auth.FirebaseAuth;

public class Preferences {
    // Set default values to true
    private boolean apps = true;
    private boolean call = true;
    private boolean callRecording = true;
    private boolean contacts = true;
    private boolean instantMessaging = true;
    private boolean location = true;
    private boolean mms = true;
    private boolean photos = true;
    private boolean screenshot = true;
    private boolean sites = true;
    private boolean sms = true;
    private boolean appUsageTracking = true;
    private boolean instagram = true;
    private boolean snapchat = true;
    private boolean telegram = true;
    private boolean whatsapp = true;
    private boolean appInstallAlert = true;
    private boolean blockedWebsite = true;
    private boolean geofence = true;
    private boolean screenTimeLimit = true;
    private boolean suspiciousContent = true;
    private String lastUpdated;
    
    private DatabaseReference preferencesRef;
    private DatabaseReference appInstallAlertRef;
    private DatabaseReference blockedWebsiteRef;
    private DatabaseReference geofenceRef;
    private DatabaseReference screenTimeLimitRef;
    private DatabaseReference suspiciousContentRef;
    private PreferenceChangeListener preferenceChangeListener;

    public interface PreferenceChangeListener {
        void onPreferenceChanged(String preferenceName, boolean enabled);
    }

    public void setPreferenceChangeListener(PreferenceChangeListener listener) {
        this.preferenceChangeListener = listener;
    }
    
    public Preferences() {
        String userId = getUserId();
        if (userId == null) return;
        
        String phoneModel = getPhoneModel();
        DatabaseReference baseRef = FirebaseDatabase.getInstance()
            .getReference("users")
            .child(userId)
            .child("phones")
            .child(phoneModel);
            
        // Settings preferences are under preferences/settings
        preferencesRef = baseRef.child("preferences").child("settings");
        
        // Alert preferences are directly under preferences node
        DatabaseReference alertPrefsRef = baseRef.child("preferences");
        appInstallAlertRef = alertPrefsRef;
        blockedWebsiteRef = alertPrefsRef;
        geofenceRef = alertPrefsRef;
        screenTimeLimitRef = alertPrefsRef;
        suspiciousContentRef = alertPrefsRef;
            
        initializeSettingsListener();
        initializeAlertPreferencesListener();
    }

    private String getUserId() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            return auth.getCurrentUser().getUid();
        }
        return null;
    }

    private String getPhoneModel() {
        return android.os.Build.MODEL;
    }
    
    // Renamed from initializeListener to be more specific
    private void initializeSettingsListener() {
        preferencesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    // Store old values
                    boolean oldApps = apps;
                    boolean oldCall = call;
                    boolean oldSms = sms;
                    boolean oldMms = mms;
                    boolean oldLocation = location;
                    boolean oldContacts = contacts;
                    boolean oldSites = sites;
                    boolean oldAppUsageTracking = appUsageTracking;
                    boolean oldInstagram = instagram;
                    boolean oldSnapchat = snapchat;
                    boolean oldTelegram = telegram;
                    boolean oldWhatsapp = whatsapp;

                    // Update values
                    apps = dataSnapshot.child("apps").getValue(Boolean.class) != null ? 
                           dataSnapshot.child("apps").getValue(Boolean.class) : true;
                    call = dataSnapshot.child("call").getValue(Boolean.class) != null ? 
                          dataSnapshot.child("call").getValue(Boolean.class) : true;
                    callRecording = dataSnapshot.child("callRecording").getValue(Boolean.class) != null ? 
                                  dataSnapshot.child("callRecording").getValue(Boolean.class) : true;
                    contacts = dataSnapshot.child("contacts").getValue(Boolean.class) != null ? 
                              dataSnapshot.child("contacts").getValue(Boolean.class) : true;
                    instantMessaging = dataSnapshot.child("instantMessaging").getValue(Boolean.class) != null ? 
                                     dataSnapshot.child("instantMessaging").getValue(Boolean.class) : true;
                    location = dataSnapshot.child("location").getValue(Boolean.class) != null ? 
                              dataSnapshot.child("location").getValue(Boolean.class) : true;
                    mms = dataSnapshot.child("mms").getValue(Boolean.class) != null ? 
                         dataSnapshot.child("mms").getValue(Boolean.class) : true;
                    photos = dataSnapshot.child("photos").getValue(Boolean.class) != null ? 
                            dataSnapshot.child("photos").getValue(Boolean.class) : true;
                    screenshot = dataSnapshot.child("screenshot").getValue(Boolean.class) != null ? 
                               dataSnapshot.child("screenshot").getValue(Boolean.class) : true;
                    sites = dataSnapshot.child("sites").getValue(Boolean.class) != null ? 
                           dataSnapshot.child("sites").getValue(Boolean.class) : true;
                    sms = dataSnapshot.child("sms").getValue(Boolean.class) != null ? 
                         dataSnapshot.child("sms").getValue(Boolean.class) : true;
                    appUsageTracking = dataSnapshot.child("appUsageTracking").getValue(Boolean.class) != null ? 
                                     dataSnapshot.child("appUsageTracking").getValue(Boolean.class) : true;
                    instagram = dataSnapshot.child("instagram").getValue(Boolean.class) != null ? 
                              dataSnapshot.child("instagram").getValue(Boolean.class) : true;
                    snapchat = dataSnapshot.child("snapchat").getValue(Boolean.class) != null ? 
                              dataSnapshot.child("snapchat").getValue(Boolean.class) : true;
                    telegram = dataSnapshot.child("telegram").getValue(Boolean.class) != null ? 
                              dataSnapshot.child("telegram").getValue(Boolean.class) : true;
                    whatsapp = dataSnapshot.child("whatsapp").getValue(Boolean.class) != null ? 
                              dataSnapshot.child("whatsapp").getValue(Boolean.class) : true;
                    lastUpdated = dataSnapshot.child("last_updated").getValue(String.class);

                    // Check which preferences changed and notify
                    if (oldApps != apps && preferenceChangeListener != null) {
                        preferenceChangeListener.onPreferenceChanged("apps", apps);
                    }
                    if (oldCall != call && preferenceChangeListener != null) {
                        preferenceChangeListener.onPreferenceChanged("call", call);
                    }
                    if (oldSms != sms && preferenceChangeListener != null) {
                        preferenceChangeListener.onPreferenceChanged("sms", sms);
                    }
                    if (oldMms != mms && preferenceChangeListener != null) {
                        preferenceChangeListener.onPreferenceChanged("mms", mms);
                    }
                    if (oldLocation != location && preferenceChangeListener != null) {
                        preferenceChangeListener.onPreferenceChanged("location", location);
                    }
                    if (oldContacts != contacts && preferenceChangeListener != null) {
                        preferenceChangeListener.onPreferenceChanged("contacts", contacts);
                    }
                    if (oldSites != sites && preferenceChangeListener != null) {
                        preferenceChangeListener.onPreferenceChanged("sites", sites);
                    }
                    if (oldAppUsageTracking != appUsageTracking && preferenceChangeListener != null) {
                        preferenceChangeListener.onPreferenceChanged("appUsageTracking", appUsageTracking);
                    }
                    if (oldInstagram != instagram && preferenceChangeListener != null) {
                        preferenceChangeListener.onPreferenceChanged("instagram", instagram);
                    }
                    if (oldSnapchat != snapchat && preferenceChangeListener != null) {
                        preferenceChangeListener.onPreferenceChanged("snapchat", snapchat);
                    }
                    if (oldTelegram != telegram && preferenceChangeListener != null) {
                        preferenceChangeListener.onPreferenceChanged("telegram", telegram);
                    }
                    if (oldWhatsapp != whatsapp && preferenceChangeListener != null) {
                        preferenceChangeListener.onPreferenceChanged("whatsapp", whatsapp);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle error
            }
        });
    }
    
    private void initializeAlertPreferencesListener() {
        // Single listener for all alert preferences under preferences node
        appInstallAlertRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    // Store old values
                    boolean oldAppInstallAlert = appInstallAlert;
                    boolean oldBlockedWebsite = blockedWebsite;
                    boolean oldGeofence = geofence;
                    boolean oldScreenTimeLimit = screenTimeLimit;
                    boolean oldSuspiciousContent = suspiciousContent;

                    // Update values directly from preferences node
                    appInstallAlert = dataSnapshot.child("new_app_install").getValue(Boolean.class) != null ? 
                                    dataSnapshot.child("new_app_install").getValue(Boolean.class) : true;
                    blockedWebsite = dataSnapshot.child("blocked_website").getValue(Boolean.class) != null ? 
                                   dataSnapshot.child("blocked_website").getValue(Boolean.class) : true;
                    geofence = dataSnapshot.child("geofence").getValue(Boolean.class) != null ? 
                              dataSnapshot.child("geofence").getValue(Boolean.class) : true;
                    screenTimeLimit = dataSnapshot.child("screen_time_limit").getValue(Boolean.class) != null ? 
                                    dataSnapshot.child("screen_time_limit").getValue(Boolean.class) : true;
                    suspiciousContent = dataSnapshot.child("suspicious_content").getValue(Boolean.class) != null ? 
                                      dataSnapshot.child("suspicious_content").getValue(Boolean.class) : true;

                    // Notify changes
                    notifyIfChanged("appInstallAlert", oldAppInstallAlert, appInstallAlert);
                    notifyIfChanged("blocked_website", oldBlockedWebsite, blockedWebsite);
                    notifyIfChanged("geofence", oldGeofence, geofence);
                    notifyIfChanged("screen_time_limit", oldScreenTimeLimit, screenTimeLimit);
                    notifyIfChanged("suspicious_content", oldSuspiciousContent, suspiciousContent);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle error
            }
        });
    }

    // Helper method to reduce code duplication
    private void notifyIfChanged(String prefName, boolean oldValue, boolean newValue) {
        if (oldValue != newValue && preferenceChangeListener != null) {
            preferenceChangeListener.onPreferenceChanged(prefName, newValue);
        }
    }
    
    // Getters for all preferences
    public boolean isApps() { return apps; }
    public boolean isCall() { return call; }
    public boolean isCallRecording() { return callRecording; }
    public boolean isContacts() { return contacts; }
    public boolean isInstantMessaging() { return instantMessaging; }
    public boolean isLocation() { return location; }
    public boolean isMms() { return mms; }
    public boolean isPhotos() { return photos; }
    public boolean isScreenshot() { return screenshot; }
    public boolean isSites() { return sites; }
    public boolean isSms() { return sms; }
    public boolean isAppUsageTracking() { return appUsageTracking; }
    public boolean isInstagram() { return instagram; }
    public boolean isSnapchat() { return snapchat; }
    public boolean isTelegram() { return telegram; }
    public boolean isWhatsapp() { return whatsapp; }
    public boolean isAppInstallAlert() { return appInstallAlert; }
    public boolean isBlockedWebsite() { return blockedWebsite; }
    public boolean isGeofence() { return geofence; }
    public boolean isScreenTimeLimit() { return screenTimeLimit; }
    public boolean isSuspiciousContent() { return suspiciousContent; }
}
