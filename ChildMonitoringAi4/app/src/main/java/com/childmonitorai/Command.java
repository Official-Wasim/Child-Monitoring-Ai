package com.childmonitorai;

import java.util.HashMap;
import java.util.Map;

public class Command {
    private String command;
    private Map<String, String> params;
    private String status;

    // Default constructor for Firebase deserialization
    public Command() {
        params = new HashMap<>();
    }

    public Command(String command, Map<String, String> params, String status) {
        this.command = command;
        this.params = params != null ? params : new HashMap<>();
        this.status = status;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public Map<String, String> getParams() {
        return params;
    }

    public void setParams(Map<String, String> params) {
        this.params = params != null ? params : new HashMap<>();
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "Command{" +
                "command='" + command + '\'' +
                ", params=" + params +
                ", status='" + status + '\'' +
                '}';
    }
}
