package com.childmonitorai;
import com.childmonitorai.models.ContactData;


import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.Log;

public class ContactMonitor {
    private static final String TAG = "ContactMonitor";
    private String userId;
    private String phoneModel;
    private Context context;
    private BaseContentObserver contactObserver;
    private DatabaseHelper databaseHelper;

    public ContactMonitor(Context context, String userId, String phoneModel) {
        this.context = context;
        this.userId = userId;
        this.phoneModel = phoneModel;
        this.databaseHelper = new DatabaseHelper(); // Initialize the helper class
    }

    public void startMonitoring() {
        contactObserver = new BaseContentObserver(context) {
            @Override
            protected void onContentChanged(Uri uri) {
                fetchContacts();
            }
        };
        contactObserver.registerObserver(ContactsContract.Contacts.CONTENT_URI);
    }

    public void stopMonitoring() {
        if (contactObserver != null) {
            contactObserver.unregisterObserver();
        }
    }

    private void fetchContacts() {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER
                    },
                    null, null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC");

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    int contactIdIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID);
                    int contactNameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                    int contactNumberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);

                    if (contactIdIndex != -1 && contactNameIndex != -1 && contactNumberIndex != -1) {
                        String contactId = cursor.getString(contactIdIndex);
                        String contactName = cursor.getString(contactNameIndex);
                        String contactNumber = cursor.getString(contactNumberIndex);

                        String uniqueContactId = generateUniqueId(contactId);

                        // Create a ContactData object
                        ContactData contactData = new ContactData(
                                contactName, contactNumber,
                                System.currentTimeMillis(), 0, null
                        );

                        // Upload using DatabaseHelper
                        databaseHelper.uploadContactData(userId, phoneModel, contactData, uniqueContactId);
                    } else {
                        Log.w(TAG, "One or more columns were not found in the cursor.");
                    }
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching contacts: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private String generateUniqueId(String contactId) {
        return contactId; 
    }
}
