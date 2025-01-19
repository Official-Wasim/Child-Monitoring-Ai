package com.childmonitorai;

import java.util.Arrays;
import java.util.List;

public class KeywordMonitor {
    // Hardcoded list of flagged URLs and keywords
    private static final List<String> FLAGGED_CONTENT = Arrays.asList(
        "gambling",
        "bet365",
        "whatsapp.com",
        "adult",
        "playboy",
        "tinder",
        "violence",
        "drugs",
        "betting.com",
        "casino.com"
    );

    // Hardcoded parent device token for testing
    private static final String PARENT_DEVICE_TOKEN = "your_test_device_token_here";
    
    public KeywordMonitor(String userId) {
        // Constructor kept simple since we're using hardcoded values
    }

    public boolean isFlaggedContent(String content) {
        if (content == null) return false;
        String lowerContent = content.toLowerCase();
        for (String keyword : FLAGGED_CONTENT) {
            if (lowerContent.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public String getParentDeviceToken() {
        return PARENT_DEVICE_TOKEN;
    }
}
