package com.childmonitorai.models;

public class MMSData {
    private String subject;
    private long date;
    private String senderAddress;
    private String content;

    // Default constructor required for Firebase
    public MMSData() {
    }

    public MMSData(String subject, long date, String senderAddress, String content) {
        this.subject = subject;
        this.date = date;
        this.senderAddress = senderAddress;
        this.content = content;
    }

    // Getters and Setters
    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public long getDate() {
        return date;
    }

    public void setDate(long date) {
        this.date = date;
    }

    public String getSenderAddress() {
        return senderAddress;
    }

    public void setSenderAddress(String senderAddress) {
        this.senderAddress = senderAddress;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    // Utility methods
    @Override
    public String toString() {
        return "MMSData{" +
                "subject='" + subject + '\'' +
                ", date=" + date +
                ", senderAddress='" + senderAddress + '\'' +
                ", content='" + content + '\'' +
                '}';
    }
}
