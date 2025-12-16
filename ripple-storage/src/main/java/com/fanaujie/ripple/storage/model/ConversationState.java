package com.fanaujie.ripple.storage.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Combined state for a conversation including unread count and last message.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationState {
    private Integer unreadCount;
    private LastMessageInfo lastMessage;
}
