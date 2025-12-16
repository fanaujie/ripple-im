package com.fanaujie.ripple.storage.service.impl.internal;

import com.fanaujie.ripple.storage.exception.LastMessageCacheException;
import com.fanaujie.ripple.storage.exception.UnreadCountCacheException;
import com.fanaujie.ripple.storage.model.ConversationState;
import com.fanaujie.ripple.storage.model.LastMessageInfo;
import com.fanaujie.ripple.storage.service.utils.LuaUtils;
import org.redisson.api.RBatch;
import org.redisson.api.RFuture;
import org.redisson.api.RScript;
import org.redisson.api.RScriptAsync;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.redisson.client.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Consolidated Redis operations for conversation storage. This is a package-private class that
 * handles all Redis interactions for: - Unread count operations (increment, get, set, reset) - Last
 * message operations (update, get) - Combined conversation state operations
 */
public class RedisConversationOperations {

    private static final Logger logger = LoggerFactory.getLogger(RedisConversationOperations.class);

    private static final String UNREAD_KEY_PREFIX = "unread_";
    private static final String FIELD_COUNT = "count";

    private static final String LASTMSG_KEY_PREFIX = "lastmsg:";
    private static final String FIELD_TEXT = "text";
    private static final String FIELD_TIMESTAMP = "timestamp";
    private static final String FIELD_MESSAGE_ID = "messageId";
    private static final int MAX_TEXT_LENGTH = 100;

    private final RedissonClient redissonClient;
    private final String incrementScript;

    public RedisConversationOperations(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
        this.incrementScript = LuaUtils.loadScript("lua/increment_unread_count.lua");
    }

    private String buildUnreadKey(long userId, String conversationId) {
        return UNREAD_KEY_PREFIX + userId + ":" + conversationId;
    }

    private String buildLastMsgKey(String conversationId) {
        return LASTMSG_KEY_PREFIX + conversationId;
    }

    private String truncateText(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) + "..." : text;
    }

    public boolean incrementUnreadCount(long userId, String conversationId, long timestamp) {
        String key = buildUnreadKey(userId, conversationId);
        try {
            RScript script = redissonClient.getScript(StringCodec.INSTANCE);
            Long result =
                    script.eval(
                            RScript.Mode.READ_WRITE,
                            incrementScript,
                            RScript.ReturnType.INTEGER,
                            Collections.singletonList(key),
                            String.valueOf(timestamp));
            return result != null && result == 1L;
        } catch (RedisException e) {
            logger.warn(
                    "Redis connection failed during increment for user {} conversation {}: {}",
                    userId,
                    conversationId,
                    e.getMessage());
            return false;
        }
    }

    public void batchIncrementUnreadCount(
            List<Long> userIds, String conversationId, long timestamp) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        try {
            RBatch batch = redissonClient.createBatch();
            RScriptAsync script = batch.getScript(StringCodec.INSTANCE);

            for (Long userId : userIds) {
                String key = buildUnreadKey(userId, conversationId);
                script.evalAsync(
                        RScript.Mode.READ_WRITE,
                        incrementScript,
                        RScript.ReturnType.INTEGER,
                        Collections.singletonList(key),
                        String.valueOf(timestamp));
            }

            batch.execute();
        } catch (RedisException e) {
            logger.warn(
                    "Redis batch increment failed for conversation {}: {}",
                    conversationId,
                    e.getMessage());
        }
    }

    public void resetUnreadCount(long userId, String conversationId) {
        String key = buildUnreadKey(userId, conversationId);
        try {
            redissonClient.getMap(key, StringCodec.INSTANCE).put(FIELD_COUNT, "0");
        } catch (RedisException e) {
            logger.warn(
                    "Redis reset failed for user {} conversation {}: {}",
                    userId,
                    conversationId,
                    e.getMessage());
        }
    }

    public int getUnreadCount(long userId, String conversationId) {
        String key = buildUnreadKey(userId, conversationId);
        try {
            Object value = redissonClient.getMap(key, StringCodec.INSTANCE).get(FIELD_COUNT);
            if (value == null) {
                throw new UnreadCountCacheException(
                        "Unread count not found for user "
                                + userId
                                + " conversation "
                                + conversationId);
            }
            return Integer.parseInt(value.toString());
        } catch (RedisException e) {
            throw new UnreadCountCacheException(
                    "Redis get failed for user " + userId + " conversation " + conversationId, e);
        } catch (NumberFormatException e) {
            logger.error(
                    "Invalid unread count format for user {} conversation {}: {}",
                    userId,
                    conversationId,
                    e.getMessage());
            throw new UnreadCountCacheException(
                    "Invalid unread count data for user "
                            + userId
                            + " conversation "
                            + conversationId,
                    e);
        }
    }

    public Map<String, Integer> batchGetUnreadCount(long userId, List<String> conversationIds) {
        if (conversationIds == null || conversationIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Integer> result = new HashMap<>();
        try {
            RBatch batch = redissonClient.createBatch();
            List<RFuture<Object>> futures = new ArrayList<>();

            for (String conversationId : conversationIds) {
                String key = buildUnreadKey(userId, conversationId);
                RFuture<Object> future =
                        batch.getMap(key, StringCodec.INSTANCE).getAsync(FIELD_COUNT);
                futures.add(future);
            }

            batch.execute();

            for (int i = 0; i < conversationIds.size(); i++) {
                Object value = futures.get(i).getNow();
                if (value != null) {
                    result.put(conversationIds.get(i), Integer.parseInt(value.toString()));
                }
            }
        } catch (RedisException e) {
            throw new UnreadCountCacheException("Redis batch get failed for user " + userId, e);
        } catch (NumberFormatException e) {
            throw new UnreadCountCacheException("Invalid unread count data for user " + userId, e);
        }
        return result;
    }

    public void setUnreadCount(long userId, String conversationId, int count, long timestamp) {
        String key = buildUnreadKey(userId, conversationId);
        try {
            Map<String, String> fields = new HashMap<>();
            fields.put(FIELD_COUNT, String.valueOf(count));
            fields.put(FIELD_TIMESTAMP, String.valueOf(timestamp));
            redissonClient.getMap(key, StringCodec.INSTANCE).putAll(fields);
        } catch (RedisException e) {
            logger.warn(
                    "Redis set failed for user {} conversation {}: {}",
                    userId,
                    conversationId,
                    e.getMessage());
        }
    }

    public void updateLastMessage(
            String conversationId, String text, long timestamp, String messageId) {
        String key = buildLastMsgKey(conversationId);
        try {
            Map<String, String> fields = new HashMap<>();
            fields.put(FIELD_TEXT, truncateText(text));
            fields.put(FIELD_TIMESTAMP, String.valueOf(timestamp));
            if (messageId != null) {
                fields.put(FIELD_MESSAGE_ID, messageId);
            }
            redissonClient.getMap(key, StringCodec.INSTANCE).putAll(fields);
        } catch (RedisException e) {
            logger.warn(
                    "Redis update last message failed for conversation {}: {}",
                    conversationId,
                    e.getMessage());
        }
    }

    public LastMessageInfo getLastMessage(String conversationId) {
        String key = buildLastMsgKey(conversationId);
        try {
            Map<Object, Object> data =
                    redissonClient.getMap(key, StringCodec.INSTANCE).readAllMap();
            if (data == null || data.isEmpty()) {
                return null;
            }
            String text = (String) data.get(FIELD_TEXT);
            String timestampStr = (String) data.get(FIELD_TIMESTAMP);
            String messageId = (String) data.get(FIELD_MESSAGE_ID);
            if (text == null || timestampStr == null) {
                return null;
            }
            return new LastMessageInfo(text, Long.parseLong(timestampStr), messageId);
        } catch (RedisException e) {
            throw new LastMessageCacheException(
                    "Redis get last message failed for conversation " + conversationId, e);
        } catch (NumberFormatException e) {
            logger.error(
                    "Invalid timestamp format for conversation {}: {}",
                    conversationId,
                    e.getMessage());
            throw new LastMessageCacheException(
                    "Invalid last message data for conversation " + conversationId, e);
        }
    }

    public Map<String, LastMessageInfo> batchGetLastMessage(List<String> conversationIds) {
        if (conversationIds == null || conversationIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, LastMessageInfo> result = new HashMap<>();
        try {
            RBatch batch = redissonClient.createBatch();
            List<RFuture<Map<Object, Object>>> futures = new ArrayList<>();

            for (String conversationId : conversationIds) {
                String key = buildLastMsgKey(conversationId);
                RFuture<Map<Object, Object>> future =
                        batch.getMap(key, StringCodec.INSTANCE).readAllMapAsync();
                futures.add(future);
            }

            batch.execute();

            for (int i = 0; i < conversationIds.size(); i++) {
                Map<Object, Object> data = futures.get(i).getNow();
                if (data != null && !data.isEmpty()) {
                    String text = (String) data.get(FIELD_TEXT);
                    String timestampStr = (String) data.get(FIELD_TIMESTAMP);
                    String messageId = (String) data.get(FIELD_MESSAGE_ID);
                    if (text != null && timestampStr != null) {
                        result.put(
                                conversationIds.get(i),
                                new LastMessageInfo(text, Long.parseLong(timestampStr), messageId));
                    }
                }
            }
        } catch (RedisException e) {
            throw new LastMessageCacheException("Redis batch get last message failed", e);
        } catch (NumberFormatException e) {
            throw new LastMessageCacheException("Invalid last message timestamp data", e);
        }
        return result;
    }

    public void updateConversation(
            long recipientUserId,
            String conversationId,
            String messageText,
            long timestamp,
            String messageId,
            boolean incrementUnread) {
        try {
            RBatch batch = redissonClient.createBatch();

            if (incrementUnread) {
                String unreadKey = buildUnreadKey(recipientUserId, conversationId);
                RScriptAsync script = batch.getScript(StringCodec.INSTANCE);
                script.evalAsync(
                        RScript.Mode.READ_WRITE,
                        incrementScript,
                        RScript.ReturnType.INTEGER,
                        Collections.singletonList(unreadKey),
                        String.valueOf(timestamp));
            }

            String lastMsgKey = buildLastMsgKey(conversationId);
            Map<String, String> fields = new HashMap<>();
            fields.put(FIELD_TEXT, truncateText(messageText));
            fields.put(FIELD_TIMESTAMP, String.valueOf(timestamp));
            if (messageId != null) {
                fields.put(FIELD_MESSAGE_ID, messageId);
            }
            batch.getMap(lastMsgKey, StringCodec.INSTANCE).putAllAsync(fields);

            batch.execute();
        } catch (RedisException e) {
            logger.warn(
                    "Redis update conversation failed for user {} conversation {}: {}",
                    recipientUserId,
                    conversationId,
                    e.getMessage());
        }
    }

    public void batchUpdateConversation(
            List<Long> recipientUserIds,
            String conversationId,
            String messageText,
            long timestamp,
            String messageId,
            boolean incrementUnread) {
        if (recipientUserIds == null) {
            recipientUserIds = Collections.emptyList();
        }

        try {
            RBatch batch = redissonClient.createBatch();

            if (incrementUnread && !recipientUserIds.isEmpty()) {
                RScriptAsync script = batch.getScript(StringCodec.INSTANCE);
                for (Long userId : recipientUserIds) {
                    String unreadKey = buildUnreadKey(userId, conversationId);
                    script.evalAsync(
                            RScript.Mode.READ_WRITE,
                            incrementScript,
                            RScript.ReturnType.INTEGER,
                            Collections.singletonList(unreadKey),
                            String.valueOf(timestamp));
                }
            }

            String lastMsgKey = buildLastMsgKey(conversationId);
            Map<String, String> fields = new HashMap<>();
            fields.put(FIELD_TEXT, truncateText(messageText));
            fields.put(FIELD_TIMESTAMP, String.valueOf(timestamp));
            if (messageId != null) {
                fields.put(FIELD_MESSAGE_ID, messageId);
            }
            batch.getMap(lastMsgKey, StringCodec.INSTANCE).putAllAsync(fields);

            batch.execute();
        } catch (RedisException e) {
            logger.warn(
                    "Redis batch update conversation failed for conversation {}: {}",
                    conversationId,
                    e.getMessage());
        }
    }

    public Map<String, ConversationState> batchGetConversationState(
            long userId, List<String> conversationIds) {
        if (conversationIds == null || conversationIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, ConversationState> result = new HashMap<>();

        try {
            RBatch batch = redissonClient.createBatch();

            List<RFuture<Object>> unreadFutures = new ArrayList<>();
            for (String conversationId : conversationIds) {
                String unreadKey = buildUnreadKey(userId, conversationId);
                RFuture<Object> future =
                        batch.getMap(unreadKey, StringCodec.INSTANCE).getAsync(FIELD_COUNT);
                unreadFutures.add(future);
            }

            List<RFuture<Map<Object, Object>>> lastMsgFutures = new ArrayList<>();
            for (String conversationId : conversationIds) {
                String lastMsgKey = buildLastMsgKey(conversationId);
                RFuture<Map<Object, Object>> future =
                        batch.getMap(lastMsgKey, StringCodec.INSTANCE).readAllMapAsync();
                lastMsgFutures.add(future);
            }

            batch.execute();

            for (int i = 0; i < conversationIds.size(); i++) {
                String conversationId = conversationIds.get(i);

                Integer unreadCount = null;
                Object unreadValue = unreadFutures.get(i).getNow();
                if (unreadValue != null) {
                    unreadCount = Integer.parseInt(unreadValue.toString());
                }

                LastMessageInfo lastMessage = null;
                Map<Object, Object> lastMsgData = lastMsgFutures.get(i).getNow();
                if (lastMsgData != null && !lastMsgData.isEmpty()) {
                    String text = (String) lastMsgData.get(FIELD_TEXT);
                    String timestampStr = (String) lastMsgData.get(FIELD_TIMESTAMP);
                    String messageId = (String) lastMsgData.get(FIELD_MESSAGE_ID);
                    if (text != null && timestampStr != null) {
                        lastMessage =
                                new LastMessageInfo(text, Long.parseLong(timestampStr), messageId);
                    }
                }

                result.put(conversationId, new ConversationState(unreadCount, lastMessage));
            }
        } catch (RedisException e) {
            logger.warn(
                    "Redis batch get conversation state failed for user {}: {}",
                    userId,
                    e.getMessage());
            throw new RedisException("Failed to get conversation state", e);
        }

        return result;
    }
}
