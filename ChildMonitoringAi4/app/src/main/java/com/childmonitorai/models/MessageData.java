package com.childmonitorai.models;

public class MessageData {
    private String sender;
    private String receiver;
    private String message;
    private String timestamp;
    private String direction; // "incoming" or "outgoing"
    private String platform; // WhatsApp, Instagram, etc.

    public MessageData() {
        // Default constructor required for calls to DataSnapshot.getValue(MessageData.class)
    }

    public MessageData(String sender, String receiver, String message, String timestamp, String direction, String platform) {
        this.sender = sender;
        this.receiver = receiver;
        this.message = message;
        this.timestamp = timestamp;
        this.direction = direction;
        this.platform = platform;
    }

    // Getters and setters
    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }
}
