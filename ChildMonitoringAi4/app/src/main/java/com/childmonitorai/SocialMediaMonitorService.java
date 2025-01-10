package com.childmonitorai;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class SocialMediaMonitorService extends AccessibilityService {
    private static final String TAG = "SocialMediaMonitorService";
    private static final String WHATSAPP_PACKAGE = "com.whatsapp";
    private static final String INSTAGRAM_PACKAGE = "com.instagram.android";
    private static final String SNAPCHAT_PACKAGE = "com.snapchat.android";

    private DatabaseReference mDatabase;
    private Set<String> processedMessages = new HashSet<>();
    private static final int MAX_PROCESSED_MESSAGES = 100;

    public static class MessageData {
        public String senderName;
        public String receiverName;
        public String message;
        public String timestamp;
        public String messageType;
        public String appName;

        public MessageData(String senderName, String receiverName, String message,
                           String timestamp, String messageType, String appName) {
            this.senderName = senderName;
            this.receiverName = receiverName;
            this.message = message;
            this.timestamp = timestamp;
            this.messageType = messageType;
            this.appName = appName;
        }

        public MessageData() {
            // Required empty constructor for Firebase
        }
    }

    private static class MessageInfo {
        String message;
        boolean isOutgoing;

        MessageInfo(String message, boolean isOutgoing) {
            this.message = message;
            this.isOutgoing = isOutgoing;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mDatabase = FirebaseDatabase.getInstance().getReference("social_media_messages");
        Log.d(TAG, "Service created");
    }

    @Override
    public void onServiceConnected() {
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED |
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED |
                AccessibilityEvent.TYPE_VIEW_CLICKED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS |
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY |
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        info.notificationTimeout = 100;

        String[] packageNames = {WHATSAPP_PACKAGE, INSTAGRAM_PACKAGE, SNAPCHAT_PACKAGE} ;
        info.packageNames = packageNames;

        setServiceInfo(info);
        Log.d(TAG, "Service connected and configured");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        String packageName = event.getPackageName() != null ? event.getPackageName().toString() : "";

        if (!packageName.equals(WHATSAPP_PACKAGE) &&
                !packageName.equals(INSTAGRAM_PACKAGE) &&
                !packageName.equals(SNAPCHAT_PACKAGE)) {
            return;
        }
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) {
            Log.e(TAG, "Root node is null for package: " + packageName);
            return;
        }

        try {
            if (packageName.equals(WHATSAPP_PACKAGE)) {
                processWhatsappMessages(rootNode);
            } else if (packageName.equals(INSTAGRAM_PACKAGE)) {
                processInstagramMessages(rootNode);
            } else if (packageName.equals(SNAPCHAT_PACKAGE)) {
                processSnapchatMessages(rootNode);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing messages: " + e.getMessage(), e);
        } finally {
            rootNode.recycle();
        }

        // Cleanup processed messages if needed
        if (processedMessages.size() > MAX_PROCESSED_MESSAGES) {
            processedMessages.clear();
        }
    }

    private void processWhatsappMessages(AccessibilityNodeInfo rootNode) {

        String contactName = extractContactName(rootNode);
        if (contactName.equals("Unknown Contact")) {
            Log.d(TAG, "Unable to determine WhatsApp contact name");
            return;
        }

        List<MessageInfo> messages = extractWhatsappMessages(rootNode);
        for (MessageInfo messageInfo : messages) {
            String messageKey = messageInfo.message + "|" + messageInfo.isOutgoing + "|" + contactName;

            if (processedMessages.add(messageKey)) {
                Log.d(TAG, "New WhatsApp message: " + messageInfo.message +
                        " | Outgoing: " + messageInfo.isOutgoing +
                        " | Contact: " + contactName);

                MessageData messageData = new MessageData(
                        messageInfo.isOutgoing ? "You" : contactName,
                        messageInfo.isOutgoing ? contactName : "You",
                        messageInfo.message,
                        String.valueOf(System.currentTimeMillis()),
                        messageInfo.isOutgoing ? "outgoing" : "incoming",
                        "whatsapp"
                );

                //mDatabase.push().setValue(messageData);
            }
        }
    }

    private void processInstagramMessages(AccessibilityNodeInfo rootNode) {
        Log.d(TAG, "Processing Instagram messages. Root node available: " + (rootNode != null));

        List<MessageInfo> messages = extractInstagramMessages(rootNode);
        String contactName = extractInstagramContactName(rootNode);

        for (MessageInfo messageInfo : messages) {
            String messageKey = messageInfo.message + "|" + messageInfo.isOutgoing + "|" + contactName;

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

                //mDatabase.push().setValue(messageData);
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

    private boolean isInstagramChatScreen(AccessibilityNodeInfo rootNode) {
        if (rootNode == null) return false;

        // Check for common chat screen indicators
        String[] chatScreenIndicators = {
                INSTAGRAM_PACKAGE + ":id/direct_text_input_container",  // Message input box
                INSTAGRAM_PACKAGE + ":id/direct_thread_toolbar",        // Chat toolbar
                INSTAGRAM_PACKAGE + ":id/direct_thread_messages_list",  // Messages list
                INSTAGRAM_PACKAGE + ":id/direct_thread_view",          // Main thread view
                INSTAGRAM_PACKAGE + ":id/thread_message_list"          // Alternative messages list
        };

        // Check if at least two of these indicators are present (more reliable than just one)
        int indicatorsFound = 0;
        for (String indicator : chatScreenIndicators) {
            List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByViewId(indicator);
            if (!nodes.isEmpty()) {
                indicatorsFound++;
                if (indicatorsFound >= 2) {
                    Log.d(TAG, "Confirmed Instagram chat screen");
                    return true;
                }
            }
        }

        Log.d(TAG, "Not in Instagram chat screen (indicators found: " + indicatorsFound + ")");
        return false;
    }



    private String extractInstagramContactName(AccessibilityNodeInfo rootNode) {
        Log.d(TAG, "Attempting to extract Instagram contact name");

        // First verify we're in a chat screen
        if (!isInstagramChatScreen(rootNode)) {
            Log.d(TAG, "Not in Instagram chat screen - skipping contact name extraction");
            return "Instagram User";
        }

        // Log all visible text nodes for debugging
        logAllVisibleText(rootNode);

        // 1. Try direct chat-specific IDs first
        String[] chatTitleIds = {
                INSTAGRAM_PACKAGE + ":id/thread_title",
                INSTAGRAM_PACKAGE + ":id/direct_thread_name",
                INSTAGRAM_PACKAGE + ":id/action_bar_title",
                INSTAGRAM_PACKAGE + ":id/toolbar_title",
                INSTAGRAM_PACKAGE + ":id/conversation_title",
                INSTAGRAM_PACKAGE + ":id/chat_title",
                INSTAGRAM_PACKAGE + ":id/direct_recipient_name",
                INSTAGRAM_PACKAGE + ":id/recipient_username"
        };

        for (String id : chatTitleIds) {
            List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByViewId(id);
            for (AccessibilityNodeInfo node : nodes) {
                if (node != null && node.getText() != null) {
                    String text = node.getText().toString().trim();
                    Log.d(TAG, "Found potential name in " + id + ": " + text);
                    if (isValidUsername(text)) {
                        Log.d(TAG, "Valid username found: " + text);
                        return text;
                    }
                }
            }
        }

        // 2. Search in Toolbar/ActionBar
        AccessibilityNodeInfo toolbar = findToolbar(rootNode);
        if (toolbar != null) {
            String toolbarText = extractTextFromToolbar(toolbar);
            if (toolbarText != null) {
                Log.d(TAG, "Found name in toolbar: " + toolbarText);
                return toolbarText;
            }
        }

        // 3. Last resort: Search in top area of screen
        String topAreaName = searchTopAreaForName(rootNode);
        if (topAreaName != null) {
            Log.d(TAG, "Found name in top area: " + topAreaName);
            return topAreaName;
        }

        Log.w(TAG, "Could not find valid Instagram username");
        return "Instagram User";
    }

    private void logAllVisibleText(AccessibilityNodeInfo node) {
        if (node == null) return;

        if (node.getText() != null) {
            Log.d(TAG, "Found text node: " + node.getText() +
                    " | Class: " + (node.getClassName() != null ? node.getClassName() : "null") +
                    " | ID: " + (node.getViewIdResourceName() != null ? node.getViewIdResourceName() : "null"));
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                logAllVisibleText(child);
            }
        }
    }

    private boolean isValidUsername(String text) {
        if (text == null || text.isEmpty() || text.length() > 30) return false;

        // Common UI text to ignore
        String[] invalidTexts = {
                "Your story", "Stories", "Message", "Back", "Navigate up",
                "Messages", "New Message", "Search", "Menu", "More options",
                "Send Message", "Active now", "active now", "Seen", "Typing...",
                "Direct", "Camera", "Photo", "Video", "Share", "Instagram User"
        };

        for (String invalid : invalidTexts) {
            if (text.equalsIgnoreCase(invalid)) return false;
        }

        // Additional validation rules
        return !text.contains("@") &&         // No @ symbol
                !text.contains("http") &&      // No URLs
                !text.matches(".*\\d+.*%.*") && // No percentages
                text.matches("^[\\p{L}\\p{N}_.-]+$"); // Only letters, numbers, underscores, dots, and hyphens
    }

    private AccessibilityNodeInfo findToolbar(AccessibilityNodeInfo rootNode) {
        if (rootNode == null) return null;

        // Try to find toolbar or action bar
        String[] toolbarClasses = {
                "androidx.appcompat.widget.Toolbar",
                "android.widget.Toolbar",
                "com.instagram.ui.widget.actionbar.ActionBar"
        };

        for (String className : toolbarClasses) {
            AccessibilityNodeInfo toolbar = findNodeByClassName(rootNode, className);
            if (toolbar != null) return toolbar;
        }

        return null;
    }

    private AccessibilityNodeInfo findNodeByClassName(AccessibilityNodeInfo rootNode, String className) {
        if (rootNode == null) return null;

        if (rootNode.getClassName() != null && rootNode.getClassName().toString().equals(className)) {
            return rootNode;
        }

        for (int i = 0; i < rootNode.getChildCount(); i++) {
            AccessibilityNodeInfo child = rootNode.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo result = findNodeByClassName(child, className);
                if (result != null) return result;
            }
        }

        return null;
    }

    private String extractTextFromToolbar(AccessibilityNodeInfo toolbar) {
        if (toolbar == null) return null;

        // First try direct text
        if (toolbar.getText() != null) {
            String text = toolbar.getText().toString().trim();
            if (isValidUsername(text)) return text;
        }

        // Then try children
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            AccessibilityNodeInfo child = toolbar.getChild(i);
            if (child != null) {
                if (child.getText() != null) {
                    String text = child.getText().toString().trim();
                    if (isValidUsername(text)) return text;
                }
            }
        }

        return null;
    }

    private String searchTopAreaForName(AccessibilityNodeInfo rootNode) {
        if (rootNode == null) return null;

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int topThreshold = (int)(metrics.heightPixels * 0.15);

        Queue<AccessibilityNodeInfo> queue = new LinkedList<>();
        queue.add(rootNode);

        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.poll();
            if (node != null) {
                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);

                if (bounds.top < topThreshold && node.getText() != null) {
                    String text = node.getText().toString().trim();
                    if (isValidUsername(text)) {
                        return text;
                    }
                }

                for (int i = 0; i < node.getChildCount(); i++) {
                    AccessibilityNodeInfo child = node.getChild(i);
                    if (child != null) {
                        queue.add(child);
                    }
                }
            }
        }

        return null;
    }


    private List<MessageInfo> extractWhatsappMessages(AccessibilityNodeInfo rootNode) {
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


    private List<MessageInfo> extractInstagramMessages(AccessibilityNodeInfo rootNode) {
        List<MessageInfo> messages = new ArrayList<>();

        // Try multiple possible view IDs for Instagram messages
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
                    boolean isOutgoing = isInstagramOutgoingMessage(node);
                    messages.add(new MessageInfo(messageText, isOutgoing));

                    Log.d(TAG, "Found Instagram message: " + messageText +
                            " | Outgoing: " + isOutgoing +
                            " | Using viewId: " + viewId);
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
            if (viewId != null && viewId.contains("out_row")) {
                return true;
            }

            parent = parent.getParent();
            currentDepth++;
        }

        return false;
    }

    private boolean isInstagramOutgoingMessage(AccessibilityNodeInfo node) {
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

            // Check layout parameters for right alignment
            if (parent.isClickable()) {
                AccessibilityNodeInfo.CollectionItemInfo itemInfo = parent.getCollectionItemInfo();
                if (itemInfo != null) {
                    return true;
                }
            }

            parent = parent.getParent();
            currentDepth++;
        }

        return false;
    }


    // Add Snapchat processing methods
    private void processSnapchatMessages(AccessibilityNodeInfo rootNode) {
        Log.d(TAG, "Processing Snapchat messages. Root node available: " + (rootNode != null));

        if (!isSnapchatChatScreen(rootNode)) {
            Log.d(TAG, "Not in Snapchat chat screen - skipping message processing");
            return;
        }

        String contactName = extractSnapchatContactName(rootNode);
        List<MessageInfo> messages = extractSnapchatMessages(rootNode);

        for (MessageInfo messageInfo : messages) {
            String messageKey = messageInfo.message + "|" + messageInfo.isOutgoing + "|" + contactName;

            if (processedMessages.add(messageKey)) {
                Log.d(TAG, "New Snapchat message: " + messageInfo.message +
                        " | Outgoing: " + messageInfo.isOutgoing +
                        " | Contact: " + contactName);

                MessageData messageData = new MessageData(
                        messageInfo.isOutgoing ? "You" : contactName,
                        messageInfo.isOutgoing ? contactName : "You",
                        messageInfo.message,
                        String.valueOf(System.currentTimeMillis()),
                        messageInfo.isOutgoing ? "outgoing" : "incoming",
                        "snapchat"
                );

                //mDatabase.push().setValue(messageData);
            }
        }
    }

    private boolean isSnapchatChatScreen(AccessibilityNodeInfo rootNode) {
        if (rootNode == null) return false;

        // Chat screen indicators for Snapchat
        String[] chatIndicators = {
                SNAPCHAT_PACKAGE + ":id/chat_input_text_field",    // Message input field
                SNAPCHAT_PACKAGE + ":id/chat_message_list",        // Message list
                SNAPCHAT_PACKAGE + ":id/chat_input_layout",        // Input layout
                SNAPCHAT_PACKAGE + ":id/chat_message_composer",    // Message composer
                SNAPCHAT_PACKAGE + ":id/chat_screen_container"     // Chat screen container
        };

        int indicatorsFound = 0;
        for (String indicator : chatIndicators) {
            List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByViewId(indicator);
            if (!nodes.isEmpty()) {
                indicatorsFound++;
                Log.d(TAG, "Found Snapchat chat indicator: " + indicator);
                if (indicatorsFound >= 2) {
                    return true;
                }
            }
        }

        return false;
    }

    private String extractSnapchatContactName(AccessibilityNodeInfo rootNode) {
        Log.d(TAG, "Attempting to extract Snapchat contact name");

        // Try various possible IDs for contact name
        String[] nameIds = {
                SNAPCHAT_PACKAGE + ":id/chat_title_bar_username",
                SNAPCHAT_PACKAGE + ":id/chat_username",
                SNAPCHAT_PACKAGE + ":id/chat_title_text",
                SNAPCHAT_PACKAGE + ":id/action_bar_title",
                SNAPCHAT_PACKAGE + ":id/conversation_title"
        };

        for (String id : nameIds) {
            List<AccessibilityNodeInfo> nodes = rootNode.findAccessibilityNodeInfosByViewId(id);
            for (AccessibilityNodeInfo node : nodes) {
                if (node != null && node.getText() != null) {
                    String name = node.getText().toString().trim();
                    if (isValidSnapchatUsername(name)) {
                        Log.d(TAG, "Found Snapchat username: " + name);
                        return name;
                    }
                }
            }
        }

        // Search in toolbar area
        AccessibilityNodeInfo toolbar = findNodeByClassName(rootNode, "androidx.appcompat.widget.Toolbar");
        if (toolbar != null) {
            String name = extractTextFromNode(toolbar);
            if (isValidSnapchatUsername(name)) {
                return name;
            }
        }

        Log.w(TAG, "Could not find valid Snapchat username");
        return "Snapchat User";
    }

    private boolean isValidSnapchatUsername(String text) {
        if (text == null || text.isEmpty() || text.length() > 30) return false;

        String[] invalidTexts = {
                "Chat", "Snap", "Story", "Stories", "Camera", "Memories",
                "Discover", "Spotlight", "Map", "Send To", "New Chat",
                "Add Friends", "Search", "Settings", "Snapchat User"
        };

        for (String invalid : invalidTexts) {
            if (text.equalsIgnoreCase(invalid)) return false;
        }

        return text.matches("^[\\w.-]+$"); // Letters, numbers, underscores, dots, and hyphens
    }

    private List<MessageInfo> extractSnapchatMessages(AccessibilityNodeInfo rootNode) {
        List<MessageInfo> messages = new ArrayList<>();

        // Possible message container IDs
        String[] messageIds = {
                SNAPCHAT_PACKAGE + ":id/chat_message_text",
                SNAPCHAT_PACKAGE + ":id/chat_message_content",
                SNAPCHAT_PACKAGE + ":id/message_text_view",
                SNAPCHAT_PACKAGE + ":id/chat_message"
        };

        for (String id : messageIds) {
            List<AccessibilityNodeInfo> messageNodes = rootNode.findAccessibilityNodeInfosByViewId(id);
            for (AccessibilityNodeInfo node : messageNodes) {
                if (node != null && node.getText() != null) {
                    String messageText = node.getText().toString();
                    boolean isOutgoing = isSnapchatOutgoingMessage(node);
                    messages.add(new MessageInfo(messageText, isOutgoing));

                    Log.d(TAG, "Found Snapchat message: " + messageText +
                            " | Outgoing: " + isOutgoing);
                }
            }
        }

        return messages;
    }

    private boolean isSnapchatOutgoingMessage(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo parent = node;
        int maxDepth = 10;
        int currentDepth = 0;

        while (parent != null && currentDepth < maxDepth) {
            String viewId = parent.getViewIdResourceName();
            if (viewId != null) {
                if (viewId.contains("outgoing") ||
                        viewId.contains("sent") ||
                        viewId.contains("right_aligned") ||
                        viewId.contains("chat_message_sent")) {
                    return true;
                }
            }

            // Check if the message is right-aligned
            Rect bounds = new Rect();
            parent.getBoundsInScreen(bounds);
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            if (bounds.right > metrics.widthPixels * 0.7) { // If message is aligned to the right side
                return true;
            }

            parent = parent.getParent();
            currentDepth++;
        }

        return false;
    }

    private String extractTextFromNode(AccessibilityNodeInfo node) {
        if (node == null || node.getText() == null) return null;
        return node.getText().toString().trim();
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Service interrupted");
    }
}