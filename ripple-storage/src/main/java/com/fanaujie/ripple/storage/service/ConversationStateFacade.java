package com.fanaujie.ripple.storage.service;

import com.fanaujie.ripple.storage.model.ConversationState;
import com.fanaujie.ripple.storage.model.LastMessageInfo;

import java.util.List;
import java.util.Map;

public interface ConversationStateFacade {

    void updateConversation(
            long recipientUserId,
            String conversationId,
            String messageText,
            long timestamp,
            String messageId,
            boolean incrementUnread);

    void batchUpdateConversation(
            List<Long> recipientUserIds,
            String conversationId,
            String messageText,
            long timestamp,
            String messageId,
            boolean incrementUnread);

    int getUnreadCount(long userId, String conversationId);

    Map<String, Integer> batchGetUnreadCount(long userId, List<String> conversationIds);

    void resetUnreadCount(long userId, String conversationId);

    LastMessageInfo getLastMessage(String conversationId);

    Map<String, LastMessageInfo> batchGetLastMessage(List<String> conversationIds);

    Map<String, ConversationState> batchGetConversationState(
            long userId, List<String> conversationIds);
}
