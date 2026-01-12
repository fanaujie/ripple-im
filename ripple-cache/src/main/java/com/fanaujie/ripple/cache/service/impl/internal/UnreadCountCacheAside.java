package com.fanaujie.ripple.cache.service.impl.internal;

import com.fanaujie.ripple.cache.exception.UnreadCountCacheException;
import com.fanaujie.ripple.cache.exception.UnreadCountFallbackException;
import com.fanaujie.ripple.storage.exception.UnreadCountStorageException;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UnreadCountCacheAside {

    private static final Logger logger = LoggerFactory.getLogger(UnreadCountCacheAside.class);

    private final RedisConversationOperations redisOps;
    private final RippleStorageFacade storageFacade;

    public UnreadCountCacheAside(
            RedisConversationOperations redisOps,
            RippleStorageFacade storageFacade) {
        this.redisOps = redisOps;
        this.storageFacade = storageFacade;
    }

    public void setUnreadCount(long userId, String conversationId, int count, long timestamp) {
        try {
            redisOps.setUnreadCount(userId, conversationId, count, timestamp);
        } catch (Exception e) {
            logger.warn(
                    "Failed to set unread count in Redis for user {} conversation {}: {}",
                    userId,
                    conversationId,
                    e.getMessage());
        }
    }

    public int getUnreadCount(long userId, String conversationId) {
        try {
            return redisOps.getUnreadCount(userId, conversationId);
        } catch (UnreadCountCacheException e) {
            logger.warn(
                    "Redis cache error for user {} conversation {}, falling back to storage: {}",
                    userId,
                    conversationId,
                    e.getMessage());
        }

        try {
            int calculatedCount = storageFacade.calculateUnreadCount(userId, conversationId);
            try {
                long timestamp = Instant.now().getEpochSecond();
                redisOps.setUnreadCount(userId, conversationId, calculatedCount, timestamp);
            } catch (Exception writeBackError) {
                logger.warn(
                        "Failed to write back unread count to Redis for user {} conversation {}: {}",
                        userId,
                        conversationId,
                        writeBackError.getMessage());
            }
            return calculatedCount;
        } catch (Exception e) {
            String errorMsg =
                    String.format(
                            "Failed to get unread count for user %d conversation %s from both Redis and storage",
                            userId, conversationId);
            logger.error(errorMsg, e);
            throw new UnreadCountStorageException(errorMsg, e);
        }
    }
}
