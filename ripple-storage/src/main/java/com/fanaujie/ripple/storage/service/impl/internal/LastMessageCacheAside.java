package com.fanaujie.ripple.storage.service.impl.internal;

import com.fanaujie.ripple.storage.exception.LastMessageCacheException;
import com.fanaujie.ripple.storage.exception.LastMessageFallbackException;
import com.fanaujie.ripple.storage.exception.LastMessageStorageException;
import com.fanaujie.ripple.storage.model.LastMessageInfo;
import com.fanaujie.ripple.storage.service.impl.cassandra.CassandraLastMessageCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LastMessageCacheAside {

    private static final Logger logger = LoggerFactory.getLogger(LastMessageCacheAside.class);

    private final RedisConversationOperations redisOps;
    private final CassandraLastMessageCalculator cassandraCalculator;

    public LastMessageCacheAside(
            RedisConversationOperations redisOps,
            CassandraLastMessageCalculator cassandraCalculator) {
        this.redisOps = redisOps;
        this.cassandraCalculator = cassandraCalculator;
    }

    public LastMessageInfo getLastMessage(String conversationId) {
        boolean cacheError = false;

        try {
            LastMessageInfo cached = redisOps.getLastMessage(conversationId);
            if (cached != null) {
                return cached;
            }
        } catch (LastMessageCacheException e) {
            logger.warn(
                    "Redis cache error for conversation {}, falling back to Cassandra: {}",
                    conversationId,
                    e.getMessage());
            cacheError = true;
        }

        try {
            LastMessageInfo calculated = cassandraCalculator.getLastMessage(conversationId);

            if (calculated != null) {
                try {
                    redisOps.updateLastMessage(
                            conversationId,
                            calculated.getText(),
                            calculated.getTimestamp(),
                            calculated.getMessageId());
                } catch (Exception writeBackError) {
                    logger.warn(
                            "Failed to write back last message to Redis for conversation {}: {}",
                            conversationId,
                            writeBackError.getMessage());
                }
            }

            return calculated;
        } catch (Exception e) {
            String errorMsg =
                    String.format(
                            "Failed to get last message for conversation %s from both Redis and Cassandra",
                            conversationId);
            logger.error(errorMsg, e);

            if (cacheError) {
                throw new LastMessageStorageException(errorMsg, e);
            } else {
                throw new LastMessageFallbackException(errorMsg, e);
            }
        }
    }

    public Map<String, LastMessageInfo> batchGetLastMessage(List<String> conversationIds) {
        if (conversationIds == null || conversationIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, LastMessageInfo> result = new HashMap<>();
        List<String> missingConversationIds = new ArrayList<>();
        boolean cacheError = false;

        try {
            Map<String, LastMessageInfo> cached = redisOps.batchGetLastMessage(conversationIds);
            result.putAll(cached);

            for (String conversationId : conversationIds) {
                if (!cached.containsKey(conversationId)) {
                    missingConversationIds.add(conversationId);
                }
            }
        } catch (LastMessageCacheException e) {
            logger.warn(
                    "Redis batch cache error, falling back to Cassandra for all: {}",
                    e.getMessage());
            cacheError = true;
            missingConversationIds = new ArrayList<>(conversationIds);
        }

        if (missingConversationIds.isEmpty()) {
            return result;
        }

        try {
            Map<String, LastMessageInfo> calculated =
                    cassandraCalculator.batchGetLastMessage(missingConversationIds);
            result.putAll(calculated);

            try {
                for (Map.Entry<String, LastMessageInfo> entry : calculated.entrySet()) {
                    redisOps.updateLastMessage(
                            entry.getKey(),
                            entry.getValue().getText(),
                            entry.getValue().getTimestamp(),
                            entry.getValue().getMessageId());
                }
            } catch (Exception writeBackError) {
                logger.warn(
                        "Failed to write back batch last messages to Redis: {}",
                        writeBackError.getMessage());
            }

            return result;
        } catch (Exception e) {
            String errorMsg = "Failed to get last messages from both Redis and Cassandra";
            logger.error(errorMsg, e);

            if (cacheError && result.isEmpty()) {
                throw new LastMessageStorageException(errorMsg, e);
            } else if (!result.isEmpty()) {
                logger.warn(
                        "Partial last message results: {} of {} conversations",
                        result.size(),
                        conversationIds.size());
                return result;
            } else {
                throw new LastMessageFallbackException(errorMsg, e);
            }
        }
    }
}
