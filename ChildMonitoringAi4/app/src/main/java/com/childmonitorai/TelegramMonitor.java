package com.childmonitorai;

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

public class TelegramMonitor {
    private static final String TAG = "TelegramMonitor";
    public static final String TELEGRAM_PACKAGE = "org.telegram.messenger";
    // Add alternative package names
    private static final String[] TELEGRAM_PACKAGES = {
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.telegram.messenger.beta"
    };
    private final DisplayMetrics displayMetrics;

    public TelegramMonitor(DisplayMetrics displayMetrics) {
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
            Log.d(TAG, "Unable to determine Telegram contact name");
            return;
        }

        List<MessageInfo> messages = extractMessages(rootNode);
        for (MessageInfo messageInfo : messages) {
            String sanitizedMessage = sanitizeData(messageInfo.message);
            String sanitizedContactName = sanitizeData(contactName);

            if (sanitizedMessage.length() > maxMessageLength) {
                sanitizedMessage = sanitizedMessage.substring(0, maxMessageLength);
            }

            String messageDirection = messageInfo.isOutgoing ? "outgoing" : "incoming";
            String messageKey = sanitizedMessage + "|" + messageInfo.isOutgoing + "|" + sanitizedContactName;

            if (processedMessages.add(messageKey)) {
                Log.d(TAG, "New Telegram message: " + messageInfo.message +
                        " | Outgoing: " + messageInfo.isOutgoing +
                        " | Contact: " + contactName);

                MessageData messageData = new MessageData(
                        messageInfo.isOutgoing ? "You" : contactName,
                        messageInfo.isOutgoing ? contactName : "You",
                        messageInfo.message,
                        String.valueOf(System.currentTimeMillis()),
                        messageDirection,
                        "telegram"
                );

                String uniqueMessageId = sanitizedContactName + "|" + System.currentTimeMillis() + 
                                       "|" + messageDirection + "|" + sanitizedMessage;
                String messageDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

                DatabaseHelper databaseHelper = new DatabaseHelper();
                //databaseHelper.uploadSocialMessageData(userId, deviceModel, messageData,uniqueMessageId, messageDate, "telegram");
            }
        }
    }

    private String extractContactName(AccessibilityNodeInfo rootNode) {
        Log.d(TAG, "Attempting to extract contact name");

        // Search for contact name in the app bar FrameLayout
        for (String packageName : TELEGRAM_PACKAGES) {
            List<AccessibilityNodeInfo> frameLayouts = rootNode.findAccessibilityNodeInfosByViewId(packageName + ":id/view");
            Log.d(TAG, "Found " + frameLayouts.size() + " frame layouts with ID " + packageName + ":id/view");
            for (AccessibilityNodeInfo frameLayout : frameLayouts) {
                if (frameLayout != null) {
                    for (int i = 0; i < frameLayout.getChildCount(); i++) {
                        AccessibilityNodeInfo child = frameLayout.getChild(i);
                        if (child != null && child.getText() != null) {
                            String name = child.getText().toString().trim();
                            if (!name.isEmpty()) {
                                Log.d(TAG, "Found contact name in app bar: " + name);
                                return name;
                            }
                        }
                    }
                }
            }
        }

        // Search for contact name using known view IDs
        String[] possibleIds = {
            ":id/action_bar_title",
            ":id/chat_title",
            ":id/name",
            ":id/chat_name",
            ":id/toolbar_title"
        };

        for (String packageName : TELEGRAM_PACKAGES) {
            for (String id : possibleIds) {
                List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByViewId(packageName + id);
                Log.d(TAG, "Found " + nodes.size() + " nodes with ID " + packageName + id);
                if (!nodes.isEmpty() && nodes.get(0) != null && nodes.get(0).getText() != null) {
                    String name = nodes.get(0).getText().toString();
                    Log.d(TAG, "Found contact name: " + name);
                    return name;
                }
            }
        }

        // Fallback: search for text nodes in the entire hierarchy
        List<AccessibilityNodeInfo> textNodes = new ArrayList<>();
        findTextNodes(rootNode, textNodes);
        for (AccessibilityNodeInfo textNode : textNodes) {
            String name = textNode.getText().toString().trim();
            if (!name.isEmpty()) {
                Log.d(TAG, "Found contact name in entire hierarchy: " + name);
                return name;
            }
        }

        Log.w(TAG, "Could not find contact name");
        return "Unknown Contact";
    }

    private List<MessageInfo> extractMessages(AccessibilityNodeInfo rootNode) {
        List<MessageInfo> messages = new ArrayList<>();
        Log.d(TAG, "Attempting to extract messages");

        // Search for messages using known view IDs
        String[] messageIds = {
            ":id/message_text",
            ":id/chat_message_text",
            ":id/messageText",
            ":id/bubble_message_text"
        };

        for (String packageName : TELEGRAM_PACKAGES) {
            for (String id : messageIds) {
                List<AccessibilityNodeInfo> messageContainers = rootNode.findAccessibilityNodeInfosByViewId(packageName + id);
                Log.d(TAG, "Searching messages with ID " + packageName + id + ". Found: " + messageContainers.size());

                for (AccessibilityNodeInfo container : messageContainers) {
                    if (container != null && container.getText() != null) {
                        String messageText = container.getText().toString();
                        boolean isOutgoing = isOutgoingMessage(container);
                        messages.add(new MessageInfo(messageText, isOutgoing));
                        Log.d(TAG, "Found message: " + messageText + " (outgoing: " + isOutgoing + ")");
                    } else {
                        Log.d(TAG, "Message container is null or has no text");
                    }
                }
            }
        }

        // Fallback: search for text nodes in the entire hierarchy
        if (messages.isEmpty()) {
            Log.d(TAG, "No messages found with specific IDs, searching entire hierarchy for text nodes");
            List<AccessibilityNodeInfo> textNodes = new ArrayList<>();
            findTextNodes(rootNode, textNodes);
            for (AccessibilityNodeInfo textNode : textNodes) {
                String messageText = textNode.getText().toString().trim();
                if (!messageText.isEmpty()) {
                    boolean isOutgoing = isOutgoingMessage(textNode);
                    messages.add(new MessageInfo(messageText, isOutgoing));
                    Log.d(TAG, "Found message in entire hierarchy: " + messageText + " (outgoing: " + isOutgoing + ")");
                }
            }
        }

        Log.d(TAG, "Total messages extracted: " + messages.size());
        return messages;
    }

    private void findTextNodes(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> textNodes) {
        if (node == null) return;
        if (node.getText() != null) {
            textNodes.add(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            findTextNodes(node.getChild(i), textNodes);
        }
    }

    private boolean isOutgoingMessage(AccessibilityNodeInfo node) {
        if (node == null) {
            return false;
        }

        // Check message position to determine if it's outgoing
        AccessibilityNodeInfo parentNode = node.getParent();
        while (parentNode != null) {
            Rect nodePosition = new Rect();
            parentNode.getBoundsInScreen(nodePosition);

            int screenWidth = displayMetrics.widthPixels;
            int messageCenter = (nodePosition.left + nodePosition.right) / 2;
            
            // Messages on the right side are typically outgoing
            if (messageCenter > (screenWidth / 2)) {
                return true;
            }
            
            parentNode = parentNode.getParent();
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
