package com.childmonitorai.services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.childmonitorai.database.DatabaseHelper;
import com.childmonitorai.monitors.InstagramMonitor;
import com.childmonitorai.monitors.SnapchatMonitor;
import com.childmonitorai.monitors.TelegramMonitor;
import com.childmonitorai.monitors.WhatsappMonitor;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.childmonitorai.helpers.Preferences;


import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

public class SocialMediaMonitorService extends AccessibilityService {
    private static final String TAG = "SocialMediaMonitorService";
    private static final String WHATSAPP_PACKAGE = "com.whatsapp";
    private static final String INSTAGRAM_PACKAGE = "com.instagram.android";
    private static final String SNAPCHAT_PACKAGE = "com.snapchat.android";
    private static final String TELEGRAM_PACKAGE = "org.telegram.messenger";

    private DatabaseReference mDatabase;
    private Set<String> processedMessages = new HashSet<>();
    private static final int MAX_PROCESSED_MESSAGES = 100;
    private static final int MAX_MESSAGE_LENGTH = 20;

    private WhatsappMonitor whatsappMonitor;
    private InstagramMonitor instagramMonitor;
    private SnapchatMonitor snapchatMonitor;
    private TelegramMonitor telegramMonitor;
    private DatabaseHelper databaseHelper;

    private static final String HUGGING_FACE_API_KEY = ""; // API key
    private static final String API_URL = "https://api-inference.huggingface.co/models/unitary/unbiased-toxic-roberta";
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 1000; // 1 second delay between retries

    private static final int MAX_CACHE_SIZE = 1000; // Maximum number of cached results
    private Map<String, ToxicityResult> analyzedMessages = new LinkedHashMap<String, ToxicityResult>(MAX_CACHE_SIZE + 1, .75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ToxicityResult> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };

    private static class ToxicityResult {
        boolean isToxic;
        String highestLabel;
        double highestScore;
        long timestamp;

        ToxicityResult(boolean isToxic, String highestLabel, double highestScore) {
            this.isToxic = isToxic;
            this.highestLabel = highestLabel;
            this.highestScore = highestScore;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private Preferences preferences;
    private boolean monitorWhatsapp = true;
    private boolean monitorInstagram = true;
    private boolean monitorSnapchat = true;
    private boolean monitorTelegram = true;

    @Override
    public void onCreate() {
        super.onCreate();
        mDatabase = FirebaseDatabase.getInstance().getReference("social_media_messages");
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        whatsappMonitor = new WhatsappMonitor(displayMetrics);
        instagramMonitor = new InstagramMonitor(displayMetrics);
        snapchatMonitor = new SnapchatMonitor(displayMetrics);
        telegramMonitor = new TelegramMonitor(displayMetrics);
        databaseHelper = new DatabaseHelper();
        Log.d(TAG, "Service created");

        // Initialize preferences and monitoring states
        preferences = new Preferences();
        updateMonitoringStates();
        
        // Set up preference change listener with specific preference filtering
        preferences.setPreferenceChangeListener((preferenceName, enabled) -> {
            // Only handle social media related preferences
            switch (preferenceName) {
                case "whatsapp":
                case "instagram":
                case "snapchat":
                case "telegram":
                    updateSpecificMonitoringState(preferenceName, enabled);
                    break;
                default:
                    // Ignore other preference changes
                    break;
            }
        });
    }

    private void updateSpecificMonitoringState(String preferenceName, boolean enabled) {
        boolean stateChanged = false;
        
        switch (preferenceName) {
            case "whatsapp":
                if (monitorWhatsapp != enabled) {
                    monitorWhatsapp = enabled;
                    stateChanged = true;
                    Log.i(TAG, "WhatsApp monitoring " + (enabled ? "started" : "stopped") + " successfully");
                }
                break;
            case "instagram":
                if (monitorInstagram != enabled) {
                    monitorInstagram = enabled;
                    stateChanged = true;
                    Log.i(TAG, "Instagram monitoring " + (enabled ? "started" : "stopped") + " successfully");
                }
                break;
            case "snapchat":
                if (monitorSnapchat != enabled) {
                    monitorSnapchat = enabled;
                    stateChanged = true;
                    Log.i(TAG, "Snapchat monitoring " + (enabled ? "started" : "stopped") + " successfully");
                }
                break;
            case "telegram":
                if (monitorTelegram != enabled) {
                    monitorTelegram = enabled;
                    stateChanged = true;
                    Log.i(TAG, "Telegram monitoring " + (enabled ? "started" : "stopped") + " successfully");
                }
                break;
        }

        // Only log overall status if there was an actual change
        if (stateChanged) {
            Log.i(TAG, String.format("Updated monitoring states:\n" +
                    "WhatsApp: %s\n" +
                    "Instagram: %s\n" +
                    "Snapchat: %s\n" +
                    "Telegram: %s",
                    monitorWhatsapp ? "Active" : "Inactive",
                    monitorInstagram ? "Active" : "Inactive",
                    monitorSnapchat ? "Active" : "Inactive",
                    monitorTelegram ? "Active" : "Inactive"));
        }
    }

    private void updateMonitoringStates() {
        monitorWhatsapp = preferences.isWhatsapp();
        monitorInstagram = preferences.isInstagram();
        monitorSnapchat = preferences.isSnapchat();
        monitorTelegram = preferences.isTelegram();

        Log.i(TAG, String.format("Initial monitoring states:\n" +
                "WhatsApp: %s\n" +
                "Instagram: %s\n" +
                "Snapchat: %s\n" +
                "Telegram: %s",
                monitorWhatsapp ? "Active" : "Inactive",
                monitorInstagram ? "Active" : "Inactive",
                monitorSnapchat ? "Active" : "Inactive",
                monitorTelegram ? "Active" : "Inactive"));
    }

    @Override
    public void onServiceConnected() {
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED |
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED |
                AccessibilityEvent.TYPE_VIEW_CLICKED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS |
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY |
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        info.notificationTimeout = 100;

        String[] packageNames = {WHATSAPP_PACKAGE, INSTAGRAM_PACKAGE, SNAPCHAT_PACKAGE, TELEGRAM_PACKAGE};
        info.packageNames = packageNames;

        setServiceInfo(info);
        Log.d(TAG, "Service connected and configured");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";

        // Check if we should monitor this package based on preferences
        if (!isPackageMonitored(packageName)) {
            return;
        }

        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) {
            Log.e(TAG, "Root node is null for package: " + packageName);
            return;
        }

        try {
            if (packageName.equals(WHATSAPP_PACKAGE)) {
                whatsappMonitor.processMessages(rootNode, processedMessages, getUserId(), getDeviceModel(), MAX_MESSAGE_LENGTH);
            } else if (packageName.equals(INSTAGRAM_PACKAGE)) {
                instagramMonitor.processMessages(rootNode, processedMessages, getUserId(), getDeviceModel(), databaseHelper, MAX_MESSAGE_LENGTH);
            } else if (packageName.equals(SNAPCHAT_PACKAGE)) {
                snapchatMonitor.processMessages(rootNode, processedMessages, getUserId(), getDeviceModel(), MAX_MESSAGE_LENGTH);
            } else if (packageName.equals(TELEGRAM_PACKAGE)) {
                telegramMonitor.processMessages(rootNode, processedMessages, getUserId(), getDeviceModel(), MAX_MESSAGE_LENGTH);
            }

            // Only perform toxicity analysis if suspicious content monitoring is enabled
            if (preferences.isSuspiciousContent()) {
                // Process any messages in the processedMessages set
                for (String message : processedMessages) {
                    if (message != null && !message.isEmpty()) {
                        // Check if message was already analyzed
                        ToxicityResult cachedResult = analyzedMessages.get(message);
                        if (cachedResult != null) {
                            Log.d(TAG, String.format("Cached Result - Message: %s | Highest Label: %s | Score: %.4f | Toxic: %b", 
                                message, cachedResult.highestLabel, cachedResult.highestScore, cachedResult.isToxic));
                            continue;
                        }

                        // Use a background thread for new toxicity analysis
                        new Thread(() -> {
                            ToxicityResult result = performToxicityAnalysis(message);
                            if (result != null) {
                                analyzedMessages.put(message, result);
                                Log.d(TAG, String.format("New Analysis - Message: %s | Highest Label: %s | Score: %.4f | Toxic: %b", 
                                    message, result.highestLabel, result.highestScore, result.isToxic));
                            }
                        }).start();
                    }
                }
            } else {
                Log.d(TAG, "Skipping toxicity analysis - suspicious content monitoring is disabled");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing messages: " + e.getMessage(), e);
        } finally {
            rootNode.recycle();
        }

        if (processedMessages.size() > MAX_PROCESSED_MESSAGES) {
            processedMessages.clear();
        }
    }

    private boolean isPackageMonitored(String packageName) {
        boolean isMonitored = (packageName.equals(WHATSAPP_PACKAGE) && monitorWhatsapp) ||
               (packageName.equals(INSTAGRAM_PACKAGE) && monitorInstagram) ||
               (packageName.equals(SNAPCHAT_PACKAGE) && monitorSnapchat) ||
               (packageName.equals(TELEGRAM_PACKAGE) && monitorTelegram);
               
        if (!isMonitored) {
            Log.d(TAG, "Package " + packageName + " is not being monitored due to preferences");
        }
        return isMonitored;
    }

    private ToxicityResult performToxicityAnalysis(String message) {
        int retryCount = 0;
        while (retryCount < MAX_RETRIES) {
            HttpURLConnection conn = null;
            try {
                // Create URL and connection
                URL url = new URL(API_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + HUGGING_FACE_API_KEY);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                // Sanitize and prepare the message
                String sanitizedMessage = message.replace("\"", "\\\"")
                                              .replace("\n", "\\n")
                                              .replace("\r", "\\r")
                                              .replace("\t", "\\t");
                
                // Prepare JSON payload
                JSONObject jsonInput = new JSONObject();
                jsonInput.put("inputs", sanitizedMessage);
                String payload = jsonInput.toString();

                // Send request
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = payload.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                // Check response code
                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Read response
                    StringBuilder response = new StringBuilder();
                    try (Scanner scanner = new Scanner(conn.getInputStream(), "UTF-8")) {
                        while (scanner.hasNextLine()) {
                            response.append(scanner.nextLine());
                        }
                    }

                    // Parse response array
                    JSONArray resultsArray = new JSONArray(response.toString());
                    JSONArray predictions = resultsArray.getJSONArray(0);
                    
                    String highestLabel = "";
                    double highestScore = 0;
                    boolean isToxic = false;

                    // Process each prediction in the first array
                    for (int i = 0; i < predictions.length(); i++) {
                        JSONObject prediction = predictions.getJSONObject(i);
                        String label = prediction.getString("label");
                        double score = prediction.getDouble("score");

                        if (score > highestScore) {
                            highestScore = score;
                            highestLabel = label;
                        }

                        if ((label.equals("toxicity") || 
                             label.equals("severe_toxicity") || 
                             label.equals("threat") || 
                             label.equals("insult")) && 
                            score > 0.5) {
                            isToxic = true;
                        }
                    }

                    ToxicityResult result = new ToxicityResult(isToxic, highestLabel, highestScore);
                    
                    // Send notification if content is toxic
                    if (isToxic) {
                        Intent fcmIntent = new Intent(getApplicationContext(), FcmService.class);
                        fcmIntent.putExtra("title", "Toxic Content Detected");
                        fcmIntent.putExtra("message", String.format("Label: %s (Score: %.2f)\nMessage: %s", 
                            highestLabel, highestScore, message));
                        fcmIntent.putExtra("url", ""); // Optional: Add a URL if needed
                        startService(fcmIntent);
                        
                        Log.w(TAG, "Toxic content detected and notification sent");
                    }
                    
                    return result;
                } else if (responseCode == HttpURLConnection.HTTP_UNAVAILABLE) {
                    // Service unavailable - retry after delay
                    retryCount++;
                    if (retryCount < MAX_RETRIES) {
                        Log.w(TAG, "Service unavailable, retrying in " + RETRY_DELAY_MS + "ms (Attempt " + retryCount + " of " + MAX_RETRIES + ")");
                        Thread.sleep(RETRY_DELAY_MS);
                        continue;
                    }
                    Log.e(TAG, "Service still unavailable after " + MAX_RETRIES + " retries");
                } else {
                    // Other error codes - log and return
                    Log.e(TAG, "Server returned HTTP " + responseCode + ": " + conn.getResponseMessage());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error analyzing toxicity: " + e.getMessage() + 
                      "\nMessage content: " + message.substring(0, Math.min(message.length(), 100)) +
                      "\nFull stack trace:", e);
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
            retryCount++;
        }

        // Fallback to keyword check if API fails
        boolean isToxic = performFallbackToxicityCheck(message);
        if (isToxic) {
            Intent fcmIntent = new Intent(getApplicationContext(), FcmService.class);
            fcmIntent.putExtra("title", "Toxic Content Detected (Fallback)");
            fcmIntent.putExtra("message", "Toxic keyword found in message: " + message);
            fcmIntent.putExtra("url", "");
            startService(fcmIntent);
            
            Log.w(TAG, "Toxic content detected by fallback check and notification sent");
        }
        return new ToxicityResult(isToxic, "fallback", isToxic ? 1.0 : 0.0);
    }

    private boolean performFallbackToxicityCheck(String message) {
        String[] toxicKeywords = {"bad", "hate", "kill", "die", "stupid"};
        String lowercaseMessage = message.toLowerCase();
        for (String keyword : toxicKeywords) {
            if (lowercaseMessage.contains(keyword)) {
                Log.d(TAG, "Fallback toxicity check - Found toxic keyword: " + keyword);
                return true;
            }
        }
        return false;
    }

    public String getUserId() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return (user != null) ? user.getUid() : "No User Logged In";
    }

    public String getDeviceModel() {
        return Build.MODEL;
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Service interrupted");
    }
}
