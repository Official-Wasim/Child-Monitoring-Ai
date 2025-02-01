package com.childmonitorai;

public class FlaggedContents {
    private static final String[] FLAGGED_URLS = {
        // Keywords
        "gambling", "betting", "poker",
        "casino","porn", "xxx", "drug",
        "darkweb", "weapon", "violence",
        "suicide", "hate", "racism",
        "tobacco", "alcohol", "vape",
        
        // Specific URLs
        "pornhub.com", "xvideos.com",
        "bet365.com", "888casino.com",
        "tinder.com"
    };

    public static boolean isFlaggedContent(String url) {
        if (url == null) return false;
        String lowerUrl = url.toLowerCase();
        for (String flaggedWord : FLAGGED_URLS) {
            if (lowerUrl.contains(flaggedWord)) {
                return true;
            }
        }
        return false;
    }
}
