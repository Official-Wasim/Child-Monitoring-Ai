package com.childmonitorai;

public class CallData {
    private String phoneNumber;
    private String callType;
    private long callDuration;
    private String date; // e.g., "2024-11-27"
    private long timestamp; // Exact time in milliseconds

    public CallData(String phoneNumber, String callType, long callDuration, String date) {
        this.phoneNumber = phoneNumber;
        this.callType = callType;
        this.callDuration = callDuration;
        this.date = date;
    }

    // Getters and setters
    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getCallType() {
        return callType;
    }

    public void setCallType(String callType) {
        this.callType = callType;
    }

    public long getCallDuration() {
        return callDuration;
    }

    public void setCallDuration(long callDuration) {
        this.callDuration = callDuration;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
