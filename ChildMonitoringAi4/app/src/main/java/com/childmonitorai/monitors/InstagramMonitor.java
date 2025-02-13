package com.childmonitorai.monitors;
import com.childmonitorai.database.DatabaseHelper;
import com.childmonitorai.models.MessageData;


import android.util.DisplayMetrics;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class InstagramMonitor {
    private static final String TAG = "InstagramMonitor";
    private static final String INSTAGRAM_PACKAGE = "com.instagram.android";
    private final DisplayMetrics displayMetrics;

    public InstagramMonitor(DisplayMetrics displayMetrics) {
        this.displayMetrics = displayMetrics;
    }

    public static class MessageInfo {
        String message;
        boolean isOutgoing;

        MessageInfo(String message, boolean isOutgoing) {
            this.message = message;
            this.isOutgoing = isOutgoing;
        }
    }

    public void processMessages(AccessibilityNodeInfo rootNode, Set<String> processedMessages, 
                              String userId, String deviceModel, DatabaseHelper databaseHelper,
                              int maxMessageLength) {
        Log.d(TAG, "Processing Instagram messages. Root node available: " + (rootNode != null));

        List<MessageInfo> messages = extractMessages(rootNode);
        String contactName = extractUserName(rootNode);
        String messageDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        for (MessageInfo messageInfo : messages) {
            String sanitizedMessage = sanitizeData(messageInfo.message);
            String sanitizedContactName = sanitizeData(contactName);

            if (sanitizedMessage.length() > maxMessageLength) {
                sanitizedMessage = sanitizedMessage.substring(0, maxMessageLength);
            }

            String messageKey = sanitizedMessage + "|" + messageInfo.isOutgoing + "|" + sanitizedContactName;

            if (processedMessages.add(messageKey)) {
                Log.d(TAG, "New Instagram message: " + messageInfo.message +
                        " | Outgoing: " + messageInfo.isOutgoing +
                        " | Contact: " + contactName);

                MessageData messageData = new MessageData(
                        messageInfo.isOutgoing ? "You" : contactName,
                        messageInfo.isOutgoing ? contactName : "You",
                        messageInfo.message,
                        String.valueOf(System.currentTimeMillis()),
                        messageInfo.isOutgoing ? "outgoing" : "incoming",
                        "instagram"
                );

                String uniqueMessageId = sanitizedContactName + "|" + messageInfo.isOutgoing + "|" + 
                                       sanitizedMessage + "|" + System.currentTimeMillis();
                databaseHelper.uploadSocialMessageData(userId, deviceModel, messageData, uniqueMessageId, messageDate, "instagram");
            }
        }
    }

    private boolean isChatScreen(AccessibilityNodeInfo rootNode) {
        if (rootNode == null) return false;

        String[] chatScreenIndicators = {
                INSTAGRAM_PACKAGE + ":id/direct_text_input_container",
                INSTAGRAM_PACKAGE + ":id/direct_thread_toolbar",
                INSTAGRAM_PACKAGE + ":id/direct_thread_messages_list",
                INSTAGRAM_PACKAGE + ":id/direct_thread_view",
                INSTAGRAM_PACKAGE + ":id/thread_message_list"
        };

        int indicatorsFound = 0;
        for (String indicator : chatScreenIndicators) {
            List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByViewId(indicator);
            if (!nodes.isEmpty()) {
                indicatorsFound++;
                if (indicatorsFound >= 2) {
                    return true;
                }
            }
        }
        return false;
    }

    private String extractUserName(AccessibilityNodeInfo rootNode) {
        if (rootNode == null) {
            Log.d(TAG, "Root node is null, returning default username.");
            return "Instagram User";
        }

        // Try header_subtitle first
        List<AccessibilityNodeInfo> subtitleNodes =
                rootNode.findAccessibilityNodeInfosByViewId(INSTAGRAM_PACKAGE + ":id/header_subtitle");
        if (!subtitleNodes.isEmpty() && subtitleNodes.get(0).getText() != null) {
            String username = subtitleNodes.get(0).getText().toString().trim();
            Log.d(TAG, "Found username in header_subtitle: " + username);
            return username;
        }

        // Try header_title if subtitle is empty
        List<AccessibilityNodeInfo> titleNodes =
                rootNode.findAccessibilityNodeInfosByViewId(INSTAGRAM_PACKAGE + ":id/header_title");
        if (!titleNodes.isEmpty() && titleNodes.get(0).getText() != null) {
            String username = titleNodes.get(0).getText().toString().trim();
            Log.d(TAG, "Found username in header_title: " + username);
            return username;
        }

        Log.d(TAG, "No username found in either header_subtitle or header_title");
        return "Instagram User";
    }

    private List<MessageInfo> extractMessages(AccessibilityNodeInfo rootNode) {
        List<MessageInfo> messages = new ArrayList<>();
        List<String> possibleMessageIds = Arrays.asList(
                INSTAGRAM_PACKAGE + ":id/direct_text_message_text_view",
                INSTAGRAM_PACKAGE + ":id/message_text",
                INSTAGRAM_PACKAGE + ":id/direct_message_text",
                INSTAGRAM_PACKAGE + ":id/message_content",
                INSTAGRAM_PACKAGE + ":id/direct_text",
                INSTAGRAM_PACKAGE + ":id/row_direct_message_text_view"
        );

        for (String viewId : possibleMessageIds) {
            List<AccessibilityNodeInfo> messageNodes = rootNode.findAccessibilityNodeInfosByViewId(viewId);
            for (AccessibilityNodeInfo node : messageNodes) {
                if (node != null && node.getText() != null) {
                    String messageText = node.getText().toString();
                    boolean isOutgoing = isOutgoingMessage(node);
                    messages.add(new MessageInfo(messageText, isOutgoing));
                }
            }
        }

        return messages;
    }

    private boolean isOutgoingMessage(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo parent = node;
        int maxDepth = 10;
        int currentDepth = 0;

        while (parent != null && currentDepth < maxDepth) {
            String viewId = parent.getViewIdResourceName();
            if (viewId != null) {
                if (viewId.contains("outgoing") ||
                        viewId.contains("message_outgoing") ||
                        viewId.contains("message_sent") ||
                        viewId.contains("container_right") ||
                        viewId.contains("direct_thread_right")) {
                    return true;
                }
            }

            parent = parent.getParent();
            currentDepth++;
        }

        return false;
    }

    private String sanitizeData(String input) {
        if (input == null) return "";
        return input.replaceAll("[.#$\\[\\]/]", "_");
    }
}
