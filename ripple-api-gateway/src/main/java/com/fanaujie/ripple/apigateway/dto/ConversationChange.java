package com.fanaujie.ripple.apigateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationChange {
    private String version;
    private int operation;
    private String conversationId;
    private String peerId; // nullable for group chats
    private String groupId; // nullable for single chats
    private String lastMessageId;
    private String lastMessage;
    private long lastMessageTimestamp;
    private String lastReadMessageId;
}
