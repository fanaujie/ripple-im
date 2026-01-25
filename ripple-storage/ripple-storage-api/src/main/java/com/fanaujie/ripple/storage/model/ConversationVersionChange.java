package com.fanaujie.ripple.storage.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationVersionChange {
    private String conversationId;
    private long peerId; // 0 for group chats
    private long groupId; // 0 for single chats
    private byte operation;
    private long lastReadMessageId;
    private String version;
    private String name;
    private String avatar;
    private String botSessionId;
}
