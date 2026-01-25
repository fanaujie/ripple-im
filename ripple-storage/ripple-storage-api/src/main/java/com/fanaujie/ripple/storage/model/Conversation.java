package com.fanaujie.ripple.storage.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {
    private long ownerId;
    private String conversationId;
    private long peerId; // 0 for group chats
    private long groupId; // 0 for single chats
    private long lastReadMessageId;
    private int unreadCount;
    private String name;
    private String avatar;
    private String botSessionId; // Session ID for bot conversations
}
