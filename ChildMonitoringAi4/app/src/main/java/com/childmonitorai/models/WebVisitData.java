package com.childmonitorai.models;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class WebVisitData {
    private String url;
    private String title;
    private String packageName;
    private long timestamp;
    private long duration;
    private boolean active;
    private String databaseKey;
    private String date;

    public WebVisitData(String url, String packageName, long timestamp) {
        this.url = url;
        this.packageName = packageName;
        this.timestamp = timestamp;
        this.duration = 0;
        this.active = true;
        this.date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(timestamp));
    }

    public String getUrl() { return url; }
    public String getTitle() { return title; }
    public String getPackageName() { return packageName; }
    public long getTimestamp() { return timestamp; }
    public long getDuration() { return duration; }
    public boolean isActive() { return active; }
    public String getDatabaseKey() { return databaseKey; }
    public String getDate() { return date; }

    public void setTitle(String title) { this.title = title; }
    public void setActive(boolean active) { this.active = active; }
    public void setDatabaseKey(String key) { this.databaseKey = key; }

    public void updateDuration(long currentTime) {
        this.duration = currentTime - timestamp;
    }
}
