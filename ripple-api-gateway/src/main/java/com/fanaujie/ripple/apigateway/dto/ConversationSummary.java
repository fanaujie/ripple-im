package com.fanaujie.ripple.apigateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSummary {
    private String conversationId;
    private int unreadCount;
    private String lastMessageText;
    private long lastMessageTimestamp;
    private String lastMessageId;
}
