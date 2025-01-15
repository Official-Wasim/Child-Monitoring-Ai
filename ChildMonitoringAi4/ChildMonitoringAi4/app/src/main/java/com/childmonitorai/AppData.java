package com.childmonitorai;

import java.util.HashMap;
import java.util.Map;

public class AppData {
    private String appName;
    private String packageName;
    private long timestamp;
    private String status; // "installed" or "uninstalled"
    private long size; // size of the app in bytes
    private String version; // version of the app

    // No-argument constructor required by Firebase
    public AppData() {}

    public AppData(String appName, String packageName, long timestamp, String status, long size, String version) {
        this.appName = appName;
        this.packageName = packageName;
        this.timestamp = timestamp;
        this.status = status;
        this.size = size;
        this.version = version;
    }

    // Getters and setters
    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    // Method to convert AppData to Map<String, Object> for Firebase
    public Map<String, Object> toMap() {
        Map<String, Object> result = new HashMap<>();
        result.put("appName", appName);
        result.put("packageName", packageName);
        result.put("timestamp", timestamp);
        result.put("status", status);
        result.put("size", size);
        result.put("version", version);
        return result;
    }
}
