package com.fanaujie.ripple.botexecutor.service;

import com.fanaujie.ripple.botexecutor.model.PendingBotMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPooled;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for managing pending bot messages in Redis.
 * Messages are queued when a bot requires authentication and the user hasn't authenticated yet.
 */
public class PendingMessageService {
    private static final Logger logger = LoggerFactory.getLogger(PendingMessageService.class);
    private static final String KEY_PREFIX = "bot:pending:";

    private final JedisPooled jedis;
    private final ObjectMapper objectMapper;
    private final long ttlSeconds;

    public PendingMessageService(JedisPooled jedis, ObjectMapper objectMapper, long ttlSeconds) {
        this.jedis = jedis;
        this.objectMapper = objectMapper;
        this.ttlSeconds = ttlSeconds;
    }

    /**
     * Queue a pending message for later delivery.
     */
    public void queueMessage(PendingBotMessage message) {
        String key = buildKey(message.getUserId(), message.getBotId());
        try {
            String json = objectMapper.writeValueAsString(message);
            jedis.rpush(key, json);
            jedis.expire(key, ttlSeconds);
            logger.info("Queued pending message for user {} bot {}", message.getUserId(), message.getBotId());
        } catch (Exception e) {
            logger.error("Failed to queue pending message", e);
        }
    }

    /**
     * Retrieve and remove all pending messages for a user-bot pair.
     */
    public List<PendingBotMessage> popAllMessages(long userId, long botId) {
        String key = buildKey(userId, botId);
        List<PendingBotMessage> messages = new ArrayList<>();

        try {
            List<String> jsonList = jedis.lrange(key, 0, -1);
            jedis.del(key);

            for (String json : jsonList) {
                try {
                    messages.add(objectMapper.readValue(json, PendingBotMessage.class));
                } catch (Exception e) {
                    logger.warn("Failed to deserialize pending message: {}", json, e);
                }
            }
            logger.info("Popped {} pending messages for user {} bot {}", messages.size(), userId, botId);
        } catch (Exception e) {
            logger.error("Failed to pop pending messages", e);
        }

        return messages;
    }

    /**
     * Check if there are any pending messages for a user-bot pair.
     */
    public boolean hasPendingMessages(long userId, long botId) {
        String key = buildKey(userId, botId);
        Long len = jedis.llen(key);
        return len != null && len > 0;
    }

    private String buildKey(long userId, long botId) {
        return KEY_PREFIX + userId + ":" + botId;
    }
}
