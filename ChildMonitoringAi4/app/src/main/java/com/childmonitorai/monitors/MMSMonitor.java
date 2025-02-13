package com.childmonitorai.monitors;
import com.childmonitorai.helpers.BaseContentObserver;
import com.childmonitorai.database.DatabaseHelper;
import com.childmonitorai.models.MMSData;


import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.Telephony;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public class MMSMonitor {
    private Context context;
    private String userId;
    private String phoneModel;
    private static final String TAG = "MMSMonitor";
    private long installationDate;
    private BaseContentObserver mmsObserver;

    public MMSMonitor(Context context, String userId, String phoneModel) {
        this.context = context;
        this.userId = userId;
        this.phoneModel = phoneModel;
        this.installationDate = System.currentTimeMillis(); // Set installation date to current time
    }

    public void startMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // For Android 13 (API 33) and above, check permissions for media access
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.READ_MEDIA_IMAGES}, 1001);
                return; // Permission not granted, exit the method
            }
        } else {
            // For Android versions below API 33, handle the usual permissions
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
                fetchMms();
            } else {
                ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.READ_SMS}, 1001);
            }
        }

        // Monitor the MMS content
        mmsObserver = new BaseContentObserver(context) {
            @Override
            protected void onContentChanged(Uri uri) {
                fetchMms();
            }
        };

        context.getContentResolver().registerContentObserver(Telephony.Mms.CONTENT_URI, true, mmsObserver);
    }


    private void fetchMms() {
        Uri mmsUri = Telephony.Mms.CONTENT_URI;
        String[] projection = {Telephony.Mms._ID, Telephony.Mms.DATE, Telephony.Mms.SUBJECT};

        try (Cursor cursor = context.getContentResolver().query(mmsUri, projection, "date >= ?", new String[]{String.valueOf(installationDate)}, Telephony.Mms.DATE + " DESC")) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String mmsId = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Mms._ID));
                    long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Mms.DATE));
                    String subject = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Mms.SUBJECT));
                    String senderAddress = fetchSenderAddress(mmsId);
                    String content = fetchMmsContent(mmsId);

                    if (timestamp >= installationDate) { // Check if MMS is from the date of installation
                        if (senderAddress != null) {
                            MMSData mmsData = new MMSData(subject, timestamp, senderAddress, content);
                            uploadMmsData(mmsData, mmsId);
                        } else {
                            Log.e(TAG, "Sender address not found for MMS ID: " + mmsId);
                        }
                    }
                } while (cursor.moveToNext());
            } else {
                Log.w(TAG, "No MMS data found or failed to query MMS content provider.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error querying MMS content provider.", e);
        }
    }

    private String fetchSenderAddress(String mmsId) {
        Uri addressUri = Uri.parse("content://mms/" + mmsId + "/addr");
        try (Cursor cursor = context.getContentResolver().query(addressUri, new String[]{"address", "type"}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int type = cursor.getInt(cursor.getColumnIndexOrThrow("type"));
                if (type == 137) {  // 137 is the type for MMS sender
                    return cursor.getString(cursor.getColumnIndexOrThrow("address"));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching sender address for MMS ID: " + mmsId, e);
        }
        return null;
    }

    private String fetchMmsContent(String mmsId) {
        Uri contentUri = Uri.parse("content://mms/" + mmsId + "/part");
        StringBuilder contentBuilder = new StringBuilder();

        try (Cursor cursor = context.getContentResolver().query(contentUri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String partId = cursor.getString(cursor.getColumnIndexOrThrow("_id"));
                    String type = cursor.getString(cursor.getColumnIndexOrThrow("ct"));

                    // Check for text content
                    if ("text/plain".equals(type) || "text/html".equals(type)) {
                        String data = cursor.getString(cursor.getColumnIndexOrThrow("text"));
                        if (data != null) {
                            contentBuilder.append(data);
                        } else {
                            contentBuilder.append(readTextPart(partId));
                        }
                    }
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching MMS content for MMS ID: " + mmsId, e);
        }

        return contentBuilder.toString();
    }

    private String readTextPart(String partId) {
        Uri partUri = Uri.parse("content://mms/part/" + partId);
        StringBuilder stringBuilder = new StringBuilder();

        try (InputStream inputStream = context.getContentResolver().openInputStream(partUri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading text part of MMS ID: " + partId, e);
        }

        return stringBuilder.toString();
    }

    private void uploadMmsData(MMSData mmsData, String mmsId) {
        new DatabaseHelper().uploadMMSDataByDate(userId, phoneModel, mmsData, mmsId, String.valueOf(mmsData.getDate()));
    }

    public void stopMonitoring() {
        if (mmsObserver != null) {
            context.getContentResolver().unregisterContentObserver(mmsObserver);
            mmsObserver = null;
            Log.i(TAG, "MMS monitoring stopped successfully");
        }
    }
}
