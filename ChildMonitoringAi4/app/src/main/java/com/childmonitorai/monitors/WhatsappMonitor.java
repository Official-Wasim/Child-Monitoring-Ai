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

public class WhatsappMonitor {
    private static final String TAG = "WhatsappMonitor";
    public static final String WHATSAPP_PACKAGE = "com.whatsapp";
    private final DisplayMetrics displayMetrics;

    public WhatsappMonitor(DisplayMetrics displayMetrics) {
        this.displayMetrics = displayMetrics;
    }

    private static class MessageInfo {
        String message;
        boolean isOutgoing;

        MessageInfo(String message, boolean isOutgoing) {
            this.message = message;
            this.isOutgoing = isOutgoing;
        }
    }

    public void processMessages(AccessibilityNodeInfo rootNode, Set<String> processedMessages, 
                              String userId, String deviceModel, int maxMessageLength) {
        String contactName = extractContactName(rootNode);
        if (contactName.equals("Unknown Contact")) {
            Log.d(TAG, "Unable to determine WhatsApp contact name");
            return;
        }

        List<MessageInfo> messages = extractMessages(rootNode);
        for (MessageInfo messageInfo : messages) {
            String sanitizedMessage = sanitizeData(messageInfo.message);
            String sanitizedContactName = sanitizeData(contactName);

            // Truncate the message if needed
            if (sanitizedMessage.length() > maxMessageLength) {
                sanitizedMessage = sanitizedMessage.substring(0, maxMessageLength);
            }

            String messageDirection = messageInfo.isOutgoing ? "outgoing" : "incoming";
            String messageKey = sanitizedMessage + "|" + messageInfo.isOutgoing + "|" + sanitizedContactName;

            if (processedMessages.add(messageKey)) {
                Log.d(TAG, "New WhatsApp message: " + messageInfo.message +
                        " | Outgoing: " + messageInfo.isOutgoing +
                        " | Contact: " + contactName);

                MessageData messageData = new MessageData(
                        messageInfo.isOutgoing ? "You" : contactName,
                        messageInfo.isOutgoing ? contactName : "You",
                        messageInfo.message,
                        String.valueOf(System.currentTimeMillis()),
                        messageDirection,
                        "whatsapp"
                );

                String uniqueMessageId = sanitizedContactName + "|" + System.currentTimeMillis() + 
                                       "|" + messageDirection + "|" + sanitizedMessage;
                String messageDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

                DatabaseHelper databaseHelper = new DatabaseHelper();
                databaseHelper.uploadSocialMessageData(userId, deviceModel, messageData, 
                                                     uniqueMessageId, messageDate, "whatsapp");
            }
        }
    }

    private String extractContactName(AccessibilityNodeInfo rootNode) {
        List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByViewId(WHATSAPP_PACKAGE + ":id/conversation_contact_name");
        if (!nodes.isEmpty() && nodes.get(0) != null && nodes.get(0).getText() != null) {
            return nodes.get(0).getText().toString();
        }

        nodes = rootNode.findAccessibilityNodeInfosByViewId(WHATSAPP_PACKAGE + ":id/conversation_title");
        if (!nodes.isEmpty() && nodes.get(0) != null && nodes.get(0).getText() != null) {
            return nodes.get(0).getText().toString();
        }

        return "Unknown Contact";
    }

    private List<MessageInfo> extractMessages(AccessibilityNodeInfo rootNode) {
        List<MessageInfo> messages = new ArrayList<>();
        List<AccessibilityNodeInfo> messageContainers = rootNode.findAccessibilityNodeInfosByViewId(WHATSAPP_PACKAGE + ":id/message_text");

        for (AccessibilityNodeInfo container : messageContainers) {
            if (container != null && container.getText() != null) {
                String messageText = container.getText().toString();
                boolean isOutgoing = isOutgoingMessage(container);
                messages.add(new MessageInfo(messageText, isOutgoing));
            }
        }

        return messages;
    }

    private boolean isOutgoingMessage(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }

        if (node.getViewIdResourceName() != null && node.getViewIdResourceName().contains("message_text")) {
            AccessibilityNodeInfo parentNode = node.getParent();
            if (parentNode != null) {
                Rect nodePosition = new Rect();
                parentNode.getBoundsInScreen(nodePosition);

                int screenWidth = displayMetrics.widthPixels;
                int messageCenter = (nodePosition.left + nodePosition.right) / 2;
                return messageCenter > (screenWidth / 2);
            }
        }

        return false;
    }

    private String sanitizeData(String input) {
        if (input == null) {
            return "";
        }
        return input.replaceAll("[.#$\\[\\]/]", "_");
    }
}
