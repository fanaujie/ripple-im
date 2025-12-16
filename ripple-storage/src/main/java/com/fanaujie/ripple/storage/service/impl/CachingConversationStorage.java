package com.fanaujie.ripple.storage.service.impl;

import com.fanaujie.ripple.storage.model.ConversationState;
import com.fanaujie.ripple.storage.model.LastMessageInfo;
import com.fanaujie.ripple.storage.service.ConversationStateFacade;
import com.fanaujie.ripple.storage.service.impl.cassandra.CassandraLastMessageCalculator;
import com.fanaujie.ripple.storage.service.impl.cassandra.CassandraUnreadCountCalculator;
import com.fanaujie.ripple.storage.service.impl.internal.LastMessageCacheAside;
import com.fanaujie.ripple.storage.service.impl.internal.RedisConversationOperations;
import com.fanaujie.ripple.storage.service.impl.internal.UnreadCountCacheAside;
import org.redisson.api.RedissonClient;

import java.util.List;
import java.util.Map;

public class CachingConversationStorage implements ConversationStateFacade {

    private final RedisConversationOperations redisOps;
    private final UnreadCountCacheAside unreadCountCacheAside;
    private final LastMessageCacheAside lastMessageCacheAside;

    public CachingConversationStorage(
            RedissonClient redissonClient,
            CassandraUnreadCountCalculator unreadCountCalculator,
            CassandraLastMessageCalculator lastMessageCalculator) {
        this.redisOps = new RedisConversationOperations(redissonClient);
        this.unreadCountCacheAside = new UnreadCountCacheAside(redisOps, unreadCountCalculator);
        this.lastMessageCacheAside = new LastMessageCacheAside(redisOps, lastMessageCalculator);
    }

    @Override
    public void updateConversation(
            long recipientUserId,
            String conversationId,
            String messageText,
            long timestamp,
            String messageId,
            boolean incrementUnread) {
        redisOps.updateConversation(
                recipientUserId, conversationId, messageText, timestamp, messageId, incrementUnread);
    }

    @Override
    public void batchUpdateConversation(
            List<Long> recipientUserIds,
            String conversationId,
            String messageText,
            long timestamp,
            String messageId,
            boolean incrementUnread) {
        redisOps.batchUpdateConversation(
                recipientUserIds, conversationId, messageText, timestamp, messageId, incrementUnread);
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
    public Map<String, Integer> batchGetUnreadCount(long userId, List<String> conversationIds) {
        return unreadCountCacheAside.batchGetUnreadCount(userId, conversationIds);
    }

    @Override
    public LastMessageInfo getLastMessage(String conversationId) {
        return lastMessageCacheAside.getLastMessage(conversationId);
    }

    @Override
    public Map<String, LastMessageInfo> batchGetLastMessage(List<String> conversationIds) {
        return lastMessageCacheAside.batchGetLastMessage(conversationIds);
    }

    @Override
    public Map<String, ConversationState> batchGetConversationState(
            long userId, List<String> conversationIds) {
        return redisOps.batchGetConversationState(userId, conversationIds);
    }
}
