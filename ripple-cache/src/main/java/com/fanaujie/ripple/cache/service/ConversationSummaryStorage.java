package com.fanaujie.ripple.cache.service;

import com.fanaujie.ripple.storage.model.ConversationSummaryInfo;

import java.util.List;
import java.util.Map;

public interface ConversationSummaryStorage {

    void updateConversationSummary(
            long recipientUserId,
            String conversationId,
            String messageText,
            long timestamp,
            long messageId);

    void updateGroupConversationSummary(
            long senderId,
            List<Long> recipientUserIds,
            String conversationId,
            String messageText,
            long timestamp,
            long messageId);

    int getUnreadCount(long userId, String conversationId);

    void resetUnreadCount(long userId, String conversationId);

    Map<String, ConversationSummaryInfo> batchGetConversationSummary(
            long userId, List<String> conversationIds);
}
