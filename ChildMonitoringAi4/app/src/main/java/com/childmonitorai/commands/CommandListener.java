package com.childmonitorai.commands;
import com.childmonitorai.models.Command;


import android.content.Context;
import android.util.Log;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

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
    }

    public void setCommandExecutor(CommandExecutor executor) {
        this.commandExecutor = executor;
    }

    public void startListeningForCommands() {
        commandListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dateSnapshot, String previousChildName) {
                processCommands(dateSnapshot);
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String previousChildName) {
                processCommands(dataSnapshot);
            }

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {
                // removal if needed for future implementation
            }

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String previousChildName) {
                // movement if needed for future implementation
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Error while listening to commands: " + databaseError.getMessage());
            }
        };

        mDatabase.child("users").child(userId).child("phones").child(deviceId)
                .child("commands").addChildEventListener(commandListener);

        Log.d(TAG, "Started listening for commands for userId: " + userId + " and deviceId: " + deviceId);
    }

    private void processCommands(DataSnapshot dateSnapshot) {
        String date = dateSnapshot.getKey();
        Log.d(TAG, "Commands found for date: " + date);

        for (DataSnapshot timestampSnapshot : dateSnapshot.getChildren()) {
            try {
                String timestamp = timestampSnapshot.getKey();

                // Null check and type verification
                if (!timestampSnapshot.exists() || timestampSnapshot.getValue() == null) {
                    Log.e(TAG, "Invalid command data for timestamp: " + timestamp);
                    continue;
                }

                Command command;
                try {
                    // Try direct conversion first
                    command = timestampSnapshot.getValue(Command.class);
                } catch (DatabaseException e) {
                    // If direct conversion fails, try manual parsing
                    try {
                        command = parseCommand(timestampSnapshot);
                    } catch (Exception parseEx) {
                        Log.e(TAG, "Error parsing command manually: " + parseEx.getMessage());
                        continue;
                    }
                }

                if (command == null || command.getCommand() == null) {
                    Log.e(TAG, "Invalid command structure for timestamp: " + timestamp);
                    continue;
                }

                // Check if the command status is "pending"
                if (!"pending".equals(command.getStatus())) {
                    Log.d(TAG, "Skipping command as its status is not 'pending': " + timestamp);
                    continue;
                }

                Log.d(TAG, "Command fetched: " + timestamp +
                        " with details: " + command.toString() +
                        " params present: " + (command.getParams() != null && !command.getParams().isEmpty()));

                // Execute the command
                try {
                    commandExecutor.executeCommand(command, date, timestamp);
                } catch (Exception e) {
                    Log.e(TAG, "Error executing command: " + timestamp, e);
                    updateCommandStatus(date, timestamp, "failed", "Execution error: " + e.getMessage());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing command: " + e.getMessage(), e);
            }
        }
    }

    private Command parseCommand(DataSnapshot snapshot) {
        Command command = new Command();

        if (snapshot.hasChild("command")) {
            command.setCommand(snapshot.child("command").getValue(String.class));
        }

        if (snapshot.hasChild("status")) {
            command.setStatus(snapshot.child("status").getValue(String.class));
        }

        if (snapshot.hasChild("result")) {
            command.setResult(snapshot.child("result").getValue(String.class));
        }

        if (snapshot.hasChild("params")) {
            DataSnapshot paramsSnapshot = snapshot.child("params");
            Map<String, String> params = new HashMap<>();
            for (DataSnapshot paramSnapshot : paramsSnapshot.getChildren()) {
                params.put(paramSnapshot.getKey(), paramSnapshot.getValue(String.class));
            }
            command.setParams(params);
        }

        return command;
    }

    public void stopListeningForCommands() {
        if (commandListener != null) {
            mDatabase.child("users").child(userId).child("phones").child(deviceId)
                    .child("commands").removeEventListener(commandListener);
            Log.d(TAG, "Stopped listening for commands");
        }
    }

    private void updateCommandStatus(String date, String timestamp, String status, String result) {
        DatabaseReference commandRef = mDatabase.child("users").child(userId)
                .child("phones").child(deviceId).child("commands")
                .child(date).child(timestamp);

        commandRef.child("status").setValue(status);
        commandRef.child("lastUpdated").setValue(System.currentTimeMillis());
        if (result != null) {
            commandRef.child("result").setValue(result);
        }
    }
}