package com.fanaujie.ripple.apigateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationItem {
    private String conversationId;
    private String peerId; // nullable for group chats
    private String groupId; // nullable for single chats
    private String lastReadMessageId;
    private int unreadCount;
    private String name;
    private String avatar;
    private String lastMessageText;
    private long lastMessageTimestamp;
    private String lastMessageId;
    private String botSessionId; // nullable, only present for bot conversations
}
