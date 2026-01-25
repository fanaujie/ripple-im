package com.fanaujie.ripple.integration.mock;

import com.fanaujie.ripple.cache.service.ConversationSummaryStorage;
import com.fanaujie.ripple.storage.model.ConversationSummaryInfo;
import com.fanaujie.ripple.storage.model.LastMessageInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MockConversationSummaryStorage implements ConversationSummaryStorage {

    // Key: "userId:conversationId", Value: unread count
    private final Map<String, Integer> unreadCounts = new ConcurrentHashMap<>();

    // Key: "userId:conversationId", Value: ConversationSummaryInfo
    private final Map<String, ConversationSummaryInfo> summaries = new ConcurrentHashMap<>();

    @Override
    public void updateConversationSummary(
            long recipientUserId,
            String conversationId,
            String messageText,
            long timestamp,
            long messageId) {
        String key = buildKey(recipientUserId, conversationId);
        unreadCounts.merge(key, 1, Integer::sum);
        LastMessageInfo lastMessage =
                new LastMessageInfo(messageText, timestamp, String.valueOf(messageId));
        summaries.put(key, new ConversationSummaryInfo(unreadCounts.get(key), lastMessage));
    }

    @Override
    public void updateGroupConversationSummary(
            long senderId,
            List<Long> recipientUserIds,
            String conversationId,
            String messageText,
            long timestamp,
            long messageId) {
        for (long userId : recipientUserIds) {
            if (userId != senderId) {
                updateConversationSummary(
                        userId, conversationId, messageText, timestamp, messageId);
            }
        }
        // Sender gets the summary but no unread count
        String senderKey = buildKey(senderId, conversationId);
        LastMessageInfo lastMessage =
                new LastMessageInfo(messageText, timestamp, String.valueOf(messageId));
        summaries.put(senderKey, new ConversationSummaryInfo(0, lastMessage));
    }

    @Override
    public int getUnreadCount(long userId, String conversationId) {
        return unreadCounts.getOrDefault(buildKey(userId, conversationId), 0);
    }

    @Override
    public void resetUnreadCount(long userId, String conversationId) {
        String key = buildKey(userId, conversationId);
        unreadCounts.put(key, 0);
        ConversationSummaryInfo existing = summaries.get(key);
        if (existing != null && existing.getLastMessage() != null) {
            summaries.put(key, new ConversationSummaryInfo(0, existing.getLastMessage()));
        }
    }

    @Override
    public Map<String, ConversationSummaryInfo> batchGetConversationSummary(
            long userId, List<String> conversationIds) {
        Map<String, ConversationSummaryInfo> result = new HashMap<>();
        for (String conversationId : conversationIds) {
            String key = buildKey(userId, conversationId);
            ConversationSummaryInfo info = summaries.get(key);
            if (info != null) {
                result.put(conversationId, info);
            }
        }
        return result;
    }

    public void clear() {
        unreadCounts.clear();
        summaries.clear();
    }

    /** Helper method for tests to get a single conversation summary. */
    public ConversationSummaryInfo getConversationSummary(long userId, String conversationId) {
        return summaries.get(buildKey(userId, conversationId));
    }

    private String buildKey(long userId, String conversationId) {
        return userId + ":" + conversationId;
    }
}
