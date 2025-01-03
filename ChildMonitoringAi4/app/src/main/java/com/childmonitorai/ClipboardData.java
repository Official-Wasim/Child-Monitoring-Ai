package com.childmonitorai;

public class ClipboardData {
    private String content;
    private long timestamp;

    // Constructor
    public ClipboardData(String content, long timestamp) {
        this.content = content;
        this.timestamp = timestamp;
    }

    // Getter for content
    public String getContent() {
        return content;
    }

    // Getter for timestamp
    public long getTimestamp() {
        return timestamp;
    }
}
