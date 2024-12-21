package com.childmonitorai;

public class AppUsageData {
    private String appName;
    private String packageName;
    private long usageDuration;
    private long timestamp;

    public AppUsageData(String appName, String packageName, long usageDuration, long timestamp) {
        this.appName = appName;
        this.packageName = packageName;
        this.usageDuration = usageDuration;
        this.timestamp = timestamp;
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

    public long getUsageDuration() {
        return usageDuration;
    }

    public void setUsageDuration(long usageDuration) {
        this.usageDuration = usageDuration;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
