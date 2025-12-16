package com.fanaujie.ripple.storage.service.impl.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.fanaujie.ripple.storage.model.LastMessageInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fetches last message directly from Cassandra tables.
 * Used as fallback when Redis cache misses or fails.
 */
public class CassandraLastMessageCalculator {

    private static final int MAX_TEXT_LENGTH = 100;

    private final CqlSession session;
    private final PreparedStatement selectLastMessageStmt;

    public CassandraLastMessageCalculator(CqlSession session) {
        this.session = session;

        // Get the most recent message for a conversation
        // message_id is a Snowflake ID so ordering by it gives chronological order
        this.selectLastMessageStmt = session.prepare(
                "SELECT message_id, text, send_timestamp FROM ripple.user_messages "
                        + "WHERE conversation_id = ? ORDER BY message_id DESC LIMIT 1");
    }

    private String truncateText(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > MAX_TEXT_LENGTH
                ? text.substring(0, MAX_TEXT_LENGTH) + "..."
                : text;
    }

    /**
     * Get last message for a single conversation.
     *
     * @param conversationId the conversation ID
     * @return LastMessageInfo or null if no messages exist
     */
    public LastMessageInfo getLastMessage(String conversationId) {
        Row row = session.execute(selectLastMessageStmt.bind(conversationId)).one();

        if (row == null) {
            return null;
        }

        long messageId = row.getLong("message_id");
        String text = row.getString("text");
        long timestamp = row.getLong("send_timestamp");

        return new LastMessageInfo(truncateText(text), timestamp, String.valueOf(messageId));
    }

    /**
     * Get last messages for multiple conversations.
     *
     * @param conversationIds list of conversation IDs
     * @return Map of conversationId -> LastMessageInfo (missing keys = no messages)
     */
    public Map<String, LastMessageInfo> batchGetLastMessage(List<String> conversationIds) {
        Map<String, LastMessageInfo> result = new HashMap<>();

        for (String conversationId : conversationIds) {
            LastMessageInfo info = getLastMessage(conversationId);
            if (info != null) {
                result.put(conversationId, info);
            }
        }

        return result;
    }
}
