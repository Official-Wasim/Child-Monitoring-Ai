package com.childmonitorai;

public class AppUsageData {

    private String packageName;
    private String appName;
    private long usageDuration;
    private long timestamp;
    private long lastTimeUsed;
    private int launchCount;
    private boolean isForeground;
    private long screenTime;
    private long lastForegroundTime;

    public AppUsageData(String packageName, String appName, long usageDuration, long timestamp) {
        this.packageName = packageName;
        this.appName = appName;
        this.usageDuration = usageDuration;
        this.timestamp = timestamp;
        this.launchCount = 0;
        this.isForeground = false;
        this.screenTime = 0;
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

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public long getLastTimeUsed() {
        return lastTimeUsed;
    }

    public void setLastTimeUsed(long lastTimeUsed) {
        this.lastTimeUsed = lastTimeUsed;
    }

    public int getLaunchCount() {
        return launchCount;
    }

    public void setLaunchCount(int launchCount) {
        this.launchCount = launchCount;
    }

    public boolean isForeground() {
        return isForeground;
    }

    public void setForeground(boolean foreground) {
        isForeground = foreground;
    }

    public long getScreenTime() {
        return screenTime;
    }

    public void setScreenTime(long screenTime) {
        this.screenTime = screenTime;
    }

    public long getLastForegroundTime() {
        return lastForegroundTime;
    }

    public void setLastForegroundTime(long lastForegroundTime) {
        this.lastForegroundTime = lastForegroundTime;
    }
}
