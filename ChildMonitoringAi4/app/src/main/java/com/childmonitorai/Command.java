// Command.java
package com.childmonitorai;

import com.google.firebase.database.PropertyName;
import java.util.HashMap;
import java.util.Map;

public class Command {
    @PropertyName("command")
    private String command;

    @PropertyName("status")
    private String status;

    @PropertyName("params")
    private Map<String, String> params;

    @PropertyName("result")
    private String result;

    @PropertyName("lastUpdated")
    private long lastUpdated;

    // Default constructor required for Firebase
    public Command() {
        this.params = new HashMap<>();
    }

    public Command(String command, String status) {
        this(command, null, status);
    }

    public Command(String command, Map<String, String> params, String status) {
        this.command = command;
        this.status = status;
        this.params = params != null ? params : new HashMap<>();
    }

    @PropertyName("command")
    public String getCommand() {
        return command;
    }

    @PropertyName("command")
    public void setCommand(String command) {
        this.command = command;
    }

    @PropertyName("status")
    public String getStatus() {
        return status;
    }

    @PropertyName("status")
    public void setStatus(String status) {
        this.status = status;
    }

    @PropertyName("params")
    public Map<String, String> getParams() {
        return params != null ? params : new HashMap<>();
    }

    @PropertyName("params")
    public void setParams(Map<String, String> params) {
        this.params = params != null ? params : new HashMap<>();
    }

    @PropertyName("result")
    public String getResult() {
        return result;
    }

    @PropertyName("result")
    public void setResult(String result) {
        this.result = result;
    }

    @PropertyName("lastUpdated")
    public long getLastUpdated() {
        return lastUpdated;
    }

    @PropertyName("lastUpdated")
    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    // Utility method to safely get param with default value
    public String getParam(String key, String defaultValue) {
        if (params != null && params.containsKey(key)) {
            return params.get(key);
        }
        return defaultValue;
    }

    @Override
    public String toString() {
        return "Command{" +
                "command='" + command + '\'' +
                ", params=" + params +
                ", status='" + status + '\'' +
                ", result='" + result + '\'' +
                ", lastUpdated=" + lastUpdated +
                '}';
    }
}