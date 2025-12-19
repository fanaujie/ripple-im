package com.fanaujie.ripple.cache.service.impl.internal;

import com.fanaujie.ripple.cache.exception.LastMessageCacheException;
import com.fanaujie.ripple.cache.exception.UnreadCountCacheException;
import com.fanaujie.ripple.storage.model.ConversationSummaryInfo;
import com.fanaujie.ripple.storage.model.LastMessageInfo;
import com.fanaujie.ripple.cache.utils.LuaUtils;
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
import java.util.concurrent.ExecutionException;

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

    public void updateUserConversationSummary(
            long receiverId,
            String conversationId,
            String messageText,
            long timestamp,
            long messageId,
            boolean incrementUnread) {
        try {
            RBatch batch = redissonClient.createBatch();

            if (incrementUnread) {
                String unreadKey = buildUnreadKey(receiverId, conversationId);
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
            fields.put(FIELD_MESSAGE_ID, String.valueOf(messageId));
            batch.getMap(lastMsgKey, StringCodec.INSTANCE).putAllAsync(fields);
            batch.execute();
        } catch (RedisException e) {
            logger.warn(
                    "Redis update conversation failed for user {} conversation {}: {}",
                    receiverId,
                    conversationId,
                    e.getMessage());
        }
    }

    public void batchUpdateConversationSummary(
            long senderId,
            List<Long> receiverIds,
            String conversationId,
            String messageText,
            long timestamp,
            long messageId) {
        try {
            RBatch batch = redissonClient.createBatch();
            RScriptAsync script = batch.getScript(StringCodec.INSTANCE);
            for (long userId : receiverIds) {
                if (userId != senderId) {
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
            fields.put(FIELD_MESSAGE_ID, String.valueOf(messageId));
            batch.getMap(lastMsgKey, StringCodec.INSTANCE).putAllAsync(fields);
            batch.execute();
        } catch (RedisException e) {
            logger.warn(
                    "Redis update group conversation failed for conversation {}: {}",
                    conversationId,
                    e.getMessage());
        }
    }

    public Map<String, ConversationSummaryInfo> batchGetConversationSummary(
            long userId, List<String> conversationIds) {
        if (conversationIds == null || conversationIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, ConversationSummaryInfo> result = new HashMap<>();

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

                int unreadCount = 0;
                Object unreadValue = unreadFutures.get(i).get();
                if (unreadValue != null) {
                    unreadCount = Integer.parseInt(unreadValue.toString());
                }
                LastMessageInfo lastMessage = null;
                Map<Object, Object> lastMsgData = lastMsgFutures.get(i).get();
                if (lastMsgData != null && !lastMsgData.isEmpty()) {
                    String text = (String) lastMsgData.get(FIELD_TEXT);
                    String timestampStr = (String) lastMsgData.get(FIELD_TIMESTAMP);
                    String messageId = (String) lastMsgData.get(FIELD_MESSAGE_ID);
                    if (text != null && timestampStr != null) {
                        lastMessage =
                                new LastMessageInfo(text, Long.parseLong(timestampStr), messageId);
                    }
                }
                result.put(conversationId, new ConversationSummaryInfo(unreadCount, lastMessage));
            }
        } catch (RedisException e) {
            logger.warn(
                    "Redis batch get conversation state failed for user {}: {}",
                    userId,
                    e.getMessage());
            throw new RedisException("Failed to get conversation state", e);
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        return result;
    }
}
