package com.childmonitorai.models;

public class ContactData {
    private String name;
    private String phoneNumber;
    private long creationTime;  // Store the creation time of the contact
    private long lastModifiedTime;  // Store the last modified time
    private String nameBeforeModification;  // Store the original name before modification

    // No-argument constructor required by Firebase
    public ContactData() {
    }

    public ContactData(String name, String phoneNumber, long creationTime, long lastModifiedTime, String nameBeforeModification) {
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.creationTime = creationTime;
        this.lastModifiedTime = lastModifiedTime;
        this.nameBeforeModification = nameBeforeModification;
    }

    // Getters and setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(long creationTime) {
        this.creationTime = creationTime;
    }

    public long getLastModifiedTime() {
        return lastModifiedTime;
    }

    public void setLastModifiedTime(long lastModifiedTime) {
        this.lastModifiedTime = lastModifiedTime;
    }

    public String getNameBeforeModification() {
        return nameBeforeModification;
    }

    public void setNameBeforeModification(String nameBeforeModification) {
        this.nameBeforeModification = nameBeforeModification;
    }
}
