package com.childmonitorai.models;

public class AppUsageData {

    private String packageName;
    private String appName;
    private long usageDuration;
    private long timestamp;
    private long lastTimeUsed;
    private int launchCount;
    private boolean isForeground;
    private long lastForegroundTime;
    private long totalForegroundTime;
    private String category;
    private boolean isSystemApp;
    private long firstTimeUsed;
    private int dayLaunchCount;
    private long dayUsageTime;
    private long lastUpdateTime;
    private long dailyTimeLimit;
    private boolean isRestricted;

    public AppUsageData(String packageName, String appName, long usageDuration, long timestamp) {
        this.packageName = packageName;
        this.appName = appName;
        this.usageDuration = usageDuration;
        this.timestamp = timestamp;
        this.launchCount = 0;
        this.isForeground = false;
        this.dayLaunchCount = 0;
        this.dayUsageTime = 0;
        this.firstTimeUsed = timestamp;
        this.lastUpdateTime = timestamp;
        this.dailyTimeLimit = 0;
        this.isRestricted = false;
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

    public long getLastForegroundTime() {
        return lastForegroundTime;
    }

    public void setLastForegroundTime(long lastForegroundTime) {
        this.lastForegroundTime = lastForegroundTime;
    }

    public long getTotalForegroundTime() {
        return totalForegroundTime;
    }

    public void setTotalForegroundTime(long totalForegroundTime) {
        this.totalForegroundTime = totalForegroundTime;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public boolean isSystemApp() {
        return isSystemApp;
    }

    public void setSystemApp(boolean systemApp) {
        isSystemApp = systemApp;
    }

    public long getFirstTimeUsed() {
        return firstTimeUsed;
    }

    public void setFirstTimeUsed(long firstTimeUsed) {
        this.firstTimeUsed = firstTimeUsed;
    }

    public int getDayLaunchCount() {
        return dayLaunchCount;
    }

    public void setDayLaunchCount(int dayLaunchCount) {
        this.dayLaunchCount = dayLaunchCount;
    }

    public long getDayUsageTime() {
        return dayUsageTime;
    }

    public void setDayUsageTime(long dayUsageTime) {
        this.dayUsageTime = dayUsageTime;
    }

    public long getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(long lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    public long getDailyTimeLimit() {
        return dailyTimeLimit;
    }

    public void setDailyTimeLimit(long dailyTimeLimit) {
        this.dailyTimeLimit = dailyTimeLimit;
    }

    public boolean isRestricted() {
        return isRestricted;
    }

    public void setRestricted(boolean restricted) {
        isRestricted = restricted;
    }
}
