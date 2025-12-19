package com.fanaujie.ripple.storage.service.impl.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CassandraUnreadCountCalculator {

    private final CqlSession session;
    private final PreparedStatement selectLastReadStmt;
    private final PreparedStatement countUnreadMessagesStmt;

    public CassandraUnreadCountCalculator(CqlSession session) {
        this.session = session;

        this.selectLastReadStmt =
                session.prepare(
                        "SELECT last_read_message_id FROM ripple.user_conversations "
                                + "WHERE owner_id = ? AND conversation_id = ?");

        this.countUnreadMessagesStmt =
                session.prepare(
                        "SELECT sender_id FROM ripple.user_messages "
                                + "WHERE conversation_id = ? AND message_id > ?");
    }

    public int calculateUnreadCount(long userId, String conversationId) {
        Row conversationRow =
                session.execute(selectLastReadStmt.bind(userId, conversationId)).one();
        if (conversationRow == null) {
            return 0;
        }

        long lastReadMessageId = conversationRow.getLong("last_read_message_id");

        ResultSet countRow =
                session.execute(countUnreadMessagesStmt.bind(conversationId, lastReadMessageId));
        int unreadCount = 0;
        for (Row row : countRow) {
            long senderId = row.getLong("sender_id");
            if (senderId != userId) {
                unreadCount++;
            }
        }
        return unreadCount;
    }
}
