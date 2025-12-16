package com.fanaujie.ripple.storage.service.impl.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calculates unread counts directly from Cassandra tables.
 * Used as fallback when Redis cache misses or fails.
 */
public class CassandraUnreadCountCalculator {

    private final CqlSession session;
    private final PreparedStatement selectLastReadStmt;
    private final PreparedStatement countUnreadMessagesStmt;

    public CassandraUnreadCountCalculator(CqlSession session) {
        this.session = session;

        this.selectLastReadStmt = session.prepare(
                "SELECT last_read_message_id FROM ripple.user_conversations "
                        + "WHERE owner_id = ? AND conversation_id = ?");

        this.countUnreadMessagesStmt = session.prepare(
                "SELECT COUNT(*) FROM ripple.user_messages "
                        + "WHERE conversation_id = ? AND message_id > ?");
    }

    /**
     * Calculate unread count for a single conversation.
     *
     * @param userId         the user ID
     * @param conversationId the conversation ID
     * @return unread count (0 if conversation doesn't exist or no unread messages)
     */
    public int calculateUnreadCount(long userId, String conversationId) {
        Row conversationRow = session.execute(
                selectLastReadStmt.bind(userId, conversationId)).one();

        if (conversationRow == null) {
            return 0;
        }

        long lastReadMessageId = conversationRow.getLong("last_read_message_id");

        Row countRow = session.execute(
                countUnreadMessagesStmt.bind(conversationId, lastReadMessageId)).one();

        if (countRow == null) {
            return 0;
        }

        return (int) countRow.getLong(0);
    }

    /**
     * Calculate unread counts for multiple conversations.
     *
     * @param userId          the user ID
     * @param conversationIds list of conversation IDs
     * @return Map of conversationId -> unreadCount
     */
    public Map<String, Integer> batchCalculateUnreadCount(long userId, List<String> conversationIds) {
        Map<String, Integer> result = new HashMap<>();

        for (String conversationId : conversationIds) {
            result.put(conversationId, calculateUnreadCount(userId, conversationId));
        }

        return result;
    }
}
