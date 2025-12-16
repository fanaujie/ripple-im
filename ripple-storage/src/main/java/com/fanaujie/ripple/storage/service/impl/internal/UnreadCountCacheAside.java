package com.fanaujie.ripple.storage.service.impl.internal;

import com.fanaujie.ripple.storage.exception.UnreadCountCacheException;
import com.fanaujie.ripple.storage.exception.UnreadCountFallbackException;
import com.fanaujie.ripple.storage.exception.UnreadCountStorageException;
import com.fanaujie.ripple.storage.service.impl.cassandra.CassandraUnreadCountCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UnreadCountCacheAside {

    private static final Logger logger = LoggerFactory.getLogger(UnreadCountCacheAside.class);

    private final RedisConversationOperations redisOps;
    private final CassandraUnreadCountCalculator cassandraCalculator;

    public UnreadCountCacheAside(
            RedisConversationOperations redisOps,
            CassandraUnreadCountCalculator cassandraCalculator) {
        this.redisOps = redisOps;
        this.cassandraCalculator = cassandraCalculator;
    }

    public int getUnreadCount(long userId, String conversationId) {
        boolean cacheError = false;
        try {
            return redisOps.getUnreadCount(userId, conversationId);
        } catch (UnreadCountCacheException e) {
            logger.warn(
                    "Redis cache error for user {} conversation {}, falling back to Cassandra: {}",
                    userId,
                    conversationId,
                    e.getMessage());
            cacheError = true;
        }

        try {
            int calculatedCount = cassandraCalculator.calculateUnreadCount(userId, conversationId);

            try {
                long timestamp = System.currentTimeMillis();
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
                            "Failed to get unread count for user %d conversation %s from both Redis and Cassandra",
                            userId, conversationId);
            logger.error(errorMsg, e);

            if (cacheError) {
                throw new UnreadCountStorageException(errorMsg, e);
            } else {
                throw new UnreadCountFallbackException(errorMsg, e);
            }
        }
    }

    public Map<String, Integer> batchGetUnreadCount(long userId, List<String> conversationIds) {
        if (conversationIds == null || conversationIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Integer> result = new HashMap<>();
        List<String> missingConversationIds = new ArrayList<>();
        boolean cacheError = false;

        try {
            Map<String, Integer> cachedCounts =
                    redisOps.batchGetUnreadCount(userId, conversationIds);
            result.putAll(cachedCounts);

            for (String conversationId : conversationIds) {
                if (!cachedCounts.containsKey(conversationId)) {
                    missingConversationIds.add(conversationId);
                }
            }
        } catch (UnreadCountCacheException e) {
            logger.warn(
                    "Redis batch cache error for user {}, falling back to Cassandra for all: {}",
                    userId,
                    e.getMessage());
            cacheError = true;
            missingConversationIds = new ArrayList<>(conversationIds);
        }

        if (missingConversationIds.isEmpty()) {
            return result;
        }

        try {
            Map<String, Integer> calculatedCounts =
                    cassandraCalculator.batchCalculateUnreadCount(userId, missingConversationIds);
            result.putAll(calculatedCounts);

            try {
                long timestamp = System.currentTimeMillis();
                for (Map.Entry<String, Integer> entry : calculatedCounts.entrySet()) {
                    redisOps.setUnreadCount(userId, entry.getKey(), entry.getValue(), timestamp);
                }
            } catch (Exception writeBackError) {
                logger.warn(
                        "Failed to write back batch unread counts to Redis for user {}: {}",
                        userId,
                        writeBackError.getMessage());
            }

            return result;
        } catch (Exception e) {
            String errorMsg =
                    String.format(
                            "Failed to get unread counts for user %d from both Redis and Cassandra",
                            userId);
            logger.error(errorMsg, e);

            if (cacheError && result.isEmpty()) {
                throw new UnreadCountStorageException(errorMsg, e);
            } else if (!result.isEmpty()) {
                logger.warn(
                        "Partial unread count results for user {}: {} of {} conversations",
                        userId,
                        result.size(),
                        conversationIds.size());
                return result;
            } else {
                throw new UnreadCountFallbackException(errorMsg, e);
            }
        }
    }
}
