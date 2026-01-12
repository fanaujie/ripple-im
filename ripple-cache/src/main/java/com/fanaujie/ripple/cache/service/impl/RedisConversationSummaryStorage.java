package com.fanaujie.ripple.cache.service.impl;

import com.fanaujie.ripple.storage.model.ConversationSummaryInfo;
import com.fanaujie.ripple.cache.service.ConversationSummaryStorage;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import com.fanaujie.ripple.cache.service.impl.internal.RedisConversationOperations;
import com.fanaujie.ripple.cache.service.impl.internal.UnreadCountCacheAside;
import org.redisson.api.RedissonClient;

import java.util.List;
import java.util.Map;

public class RedisConversationSummaryStorage implements ConversationSummaryStorage {

    private final RedisConversationOperations redisOps;
    private final UnreadCountCacheAside unreadCountCacheAside;

    public RedisConversationSummaryStorage(
            RedissonClient redissonClient, RippleStorageFacade storageFacade) {
        this.redisOps = new RedisConversationOperations(redissonClient);
        this.unreadCountCacheAside = new UnreadCountCacheAside(redisOps, storageFacade);
    }

    @Override
    public void updateConversationSummary(
            long receiverId,
            String conversationId,
            String messageText,
            long timestamp,
            long messageId) {
        redisOps.updateUserConversationSummary(
                receiverId, conversationId, messageText, timestamp, messageId, true);
    }

    @Override
    public void updateGroupConversationSummary(
            long senderId,
            List<Long> recipientUserIds,
            String conversationId,
            String messageText,
            long timestamp,
            long messageId) {
        redisOps.batchUpdateConversationSummary(
                senderId, recipientUserIds, conversationId, messageText, timestamp, messageId);
    }

    @Override
    public void resetUnreadCount(long userId, String conversationId) {
        redisOps.resetUnreadCount(userId, conversationId);
    }

    @Override
    public int getUnreadCount(long userId, String conversationId) {
        return unreadCountCacheAside.getUnreadCount(userId, conversationId);
    }

    @Override
    public Map<String, ConversationSummaryInfo> batchGetConversationSummary(
            long userId, List<String> conversationIds) {
        return redisOps.batchGetConversationSummary(userId, conversationIds);
    }
}
