package com.childmonitorai;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class WebVisitData {
    private String url;
    private String title;
    private long timestamp; // Use long for timestamp
    private Date date;

    private static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    public WebVisitData(String url, String title, long timestamp) {
        this.url = url;
        this.title = (title == null || title.isEmpty()) ? "Untitled" : title;  // Default title if null or empty
        this.timestamp = timestamp;
        this.date = new Date(timestamp); // Convert timestamp to Date
    }

    // Getters and setters
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
        this.date = new Date(timestamp); // Update date when timestamp is updated
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    @Override
    public String toString() {
        return "WebVisitData{" +
                "url='" + url + '\'' +
                ", title='" + title + '\'' +
                ", timestamp=" + timestamp +
                ", date=" + (date != null ? date.toString() : "null") +
                '}';
    }
}
