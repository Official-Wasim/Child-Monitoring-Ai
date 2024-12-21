package com.childmonitorai;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.Log;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;


public class ContactMonitor {
    private static final String TAG = "ContactMonitor";
    private String userId;
    private String phoneModel;
    private Context context;
    private BaseContentObserver contactObserver;
    private long lastSyncTime = 0;

    public ContactMonitor(Context context, String userId, String phoneModel) {
        this.context = context;
        this.userId = userId;
        this.phoneModel = phoneModel;
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
            cursor = context.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[] {
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER,
                            ContactsContract.Contacts._ID // Adding _ID to check if the contact exists
                    },
                    null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC");

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    int contactIdIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID);
                    int contactNameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                    int contactNumberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                    int contactUniqueIdIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID); // Contact's unique identifier

                    if (contactIdIndex != -1 && contactNameIndex != -1 && contactNumberIndex != -1) {
                        String contactId = cursor.getString(contactIdIndex);
                        String contactName = cursor.getString(contactNameIndex);
                        String contactNumber = cursor.getString(contactNumberIndex);

                        // Check if this contact exists in the database (using contactId as unique identifier)
                        String uniqueContactId = generateUniqueId(contactId);

                        // Determine if the contact is new or modified
                        boolean isNewContact = isNewOrModifiedContact(uniqueContactId);
                        long currentTimestamp = System.currentTimeMillis();
                        String dataType = isNewContact ? "created" : "modified";

                        // If modified, get the previous name before modification
                        String nameBeforeModification = isNewContact ? null : getNameBeforeModification(uniqueContactId);

                        // Prepare contact data
                        ContactData contactData = new ContactData(contactName, contactNumber, currentTimestamp, currentTimestamp, nameBeforeModification);

                        // Upload the contact data under the unique contactId node
                        DatabaseHelper dbHelper = new DatabaseHelper();
                        dbHelper.uploadContactData(userId, phoneModel, contactData, uniqueContactId);
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

        // Update last sync time
        lastSyncTime = System.currentTimeMillis();
    }

    private String generateUniqueId(String contactId) {
        return contactId; // Use the contactId as the unique node key in the database
    }

    private boolean isNewOrModifiedContact(String contactId) {
        // Check if the contact exists in the database using the contactId.
        long lastModifiedTime = getLastModifiedTime(contactId);

        // If the contact doesn't exist, or the timestamp is different, it's either new or modified.
        return lastModifiedTime == 0 || lastModifiedTime < lastSyncTime;
    }

    private long getLastModifiedTime(String contactId) {
        DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference();
        final long[] lastModifiedTime = {0};

        dbRef.child("users")
                .child(userId)
                .child("phones")
                .child(phoneModel)
                .child("contacts")
                .child(contactId)
                .child("lastModifiedTime")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult().exists()) {
                        lastModifiedTime[0] = task.getResult().getValue(Long.class);
                    } else {
                        Log.w("ContactMonitor", "Contact not found or no lastModifiedTime.");
                    }
                });

        return lastModifiedTime[0];
    }

    private String getNameBeforeModification(String contactId) {
        DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference();
        final String[] previousName = {null};

        dbRef.child("users")
                .child(userId)
                .child("phones")
                .child(phoneModel)
                .child("contacts")
                .child(contactId)
                .child("nameBeforeModification")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult().exists()) {
                        previousName[0] = task.getResult().getValue(String.class);
                    } else {
                        Log.w("ContactMonitor", "Contact not found or no nameBeforeModification.");
                    }
                });

        return previousName[0];
    }
}
