package com.childmonitorai.models;

public class SessionData {
    private String sessionId;
    private String packageName;
    private String appName;
    private long startTime;
    private long endTime;
    private long duration;
    private boolean timedOut;

    // Constructor
    public SessionData(String sessionId, String packageName, String appName, long startTime) {
        this.sessionId = sessionId;
        this.packageName = packageName;
        this.appName = appName;
        this.startTime = startTime;
        this.timedOut = false;
    }

    // Getters
    public String getSessionId() {
        return sessionId;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getAppName() {
        return appName;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public long getDuration() {
        return duration;
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    // Setters
    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public void setTimedOut(boolean timedOut) {
        this.timedOut = timedOut;
    }

    @Override
    public String toString() {
        return "SessionData{" +
                "sessionId='" + sessionId + '\'' +
                ", packageName='" + packageName + '\'' +
                ", appName='" + appName + '\'' +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", duration=" + duration +
                ", timedOut=" + timedOut +
                '}';
    }
}