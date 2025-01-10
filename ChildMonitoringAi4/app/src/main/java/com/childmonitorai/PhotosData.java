package com.childmonitorai;

public class PhotosData {

    private String photoName;
    private String photoUrl;
    private long photoSize;
    private long timestamp;

    public PhotosData(String photoName, String photoUrl, long photoSize, long timestamp) {
        this.photoName = photoName;
        this.photoUrl = photoUrl;
        this.photoSize = photoSize;
        this.timestamp = timestamp;
    }

    // Getters and setters
    public String getPhotoName() {
        return photoName;
    }

    public void setPhotoName(String photoName) {
        this.photoName = photoName;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public long getPhotoSize() {
        return photoSize;
    }

    public void setPhotoSize(long photoSize) {
        this.photoSize = photoSize;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
