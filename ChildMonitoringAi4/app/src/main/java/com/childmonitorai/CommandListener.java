package com.childmonitorai;

import android.content.Context;
import android.util.Log;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class CommandListener {
    private static final String TAG = "CommandListener";
    private DatabaseReference mDatabase;
    private String userId;
    private String deviceId;
    private CommandExecutor commandExecutor;
    private ChildEventListener commandListener;

    public CommandListener(String userId, String deviceId, Context context) {
        this.userId = userId;
        this.deviceId = deviceId;
        this.mDatabase = FirebaseDatabase.getInstance().getReference();
        this.commandExecutor = new CommandExecutor(userId, deviceId, context);
    }

    public void startListeningForCommands() {
        // Initialize the listener for Firebase command data
        commandListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String previousChildName) {
                String date = dataSnapshot.getKey();
                Log.d(TAG, "Commands found for date: " + date);

                // Loop through all commands for that date
                for (DataSnapshot commandSnapshot : dataSnapshot.getChildren()) {
                    Command command = commandSnapshot.getValue(Command.class);

                    if (command != null) {
                        String commandId = commandSnapshot.getKey();
                        Log.d(TAG, "Command fetched: " + commandId + " with details: " + command.toString());

                        // Handle missing parameters by setting defaults
                        String phoneNumber = command.getParams().get("phone_number");
                        if (phoneNumber == null || phoneNumber.isEmpty()) {
                            Log.d(TAG, "No phone_number provided for commandId: " + commandId);
                            phoneNumber = "unknown"; // Default value
                        }

                        String message = command.getParams().get("message");
                        if (message == null || message.isEmpty()) {
                            message = "No message provided"; // Default message if not present
                        }

                        // Log the phone number and message
                        Log.d(TAG, "Phone number: " + phoneNumber + ", Message: " + message);

                        // Execute the command using CommandExecutor
                        try {
                            commandExecutor.executeCommand(command, commandId);
                        } catch (Exception e) {
                            Log.e(TAG, "Error executing command: " + commandId, e);
                            updateCommandStatus(commandId, "failed");
                        }

                    } else {
                        Log.d(TAG, "Invalid command data for commandId: " + commandSnapshot.getKey());
                    }
                }
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String previousChildName) {
                // Handle updates if needed
            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {
                // Handle command removal if needed
            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String previousChildName) {
                // Handle command movement if needed
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.d(TAG, "Error while listening to commands: " + databaseError.getMessage());
            }
        };

        // Attach the listener to the Firebase node for commands grouped by date
        mDatabase.child("users").child(userId).child("phones").child(deviceId).child("commands")
                .addChildEventListener(commandListener);

        Log.d(TAG, "Started listening for commands for userId: " + userId + " and deviceId: " + deviceId);
    }

    public void stopListeningForCommands() {
        if (commandListener != null) {
            mDatabase.child("users").child(userId).child("phones").child(deviceId).child("commands")
                    .removeEventListener(commandListener); // Properly remove the listener

            Log.d(TAG, "Stopped listening for commands for userId: " + userId + " and deviceId: " + deviceId);
        }
    }

    // Update the command status in Firebase to "completed" or "failed"
    private void updateCommandStatus(String commandId, String status) {
        mDatabase.child("users").child(userId).child("phones").child(deviceId).child("commands")
                .child(commandId).child("status").setValue(status)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Command " + commandId + " status updated to: " + status);
                    } else {
                        Log.e(TAG, "Failed to update status for command " + commandId);
                    }
                });
    }
}
