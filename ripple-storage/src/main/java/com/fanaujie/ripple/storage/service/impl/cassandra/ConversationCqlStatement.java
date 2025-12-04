package com.fanaujie.ripple.storage.service.impl.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import lombok.Getter;

@Getter
public class ConversationCqlStatement {
    private final PreparedStatement existsConversationStmt;
    private final PreparedStatement insertConversationStmt;
    private final PreparedStatement insertConversationVersionStmt;
    private final PreparedStatement updateNameStmt;
    private final PreparedStatement updateAvatarStmt;
    private final PreparedStatement updateLastReadMessageIdStmt;
    private final PreparedStatement insertMessageStmt;
    private final PreparedStatement selectConversationsFirstPageStmt;
    private final PreparedStatement selectConversationsNextPageStmt;
    private final PreparedStatement selectConversationChangesStmt;
    private final PreparedStatement selectLatestVersionStmt;
    private final PreparedStatement selectMessagesStmt;
    private final PreparedStatement selectConversationUnreadCountStmt;
    private final PreparedStatement selectLatestMessageStmt;

    public ConversationCqlStatement(CqlSession session) {
        this.existsConversationStmt =
                session.prepare(
                        "SELECT conversation_id FROM ripple.user_conversations "
                                + "WHERE owner_id = ? AND conversation_id = ?");

        this.insertConversationStmt =
                session.prepare(
                        "INSERT INTO ripple.user_conversations "
                                + "(owner_id, conversation_id, peer_id, group_id,"
                                + "last_read_message_id, name, avatar) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?)");

        this.insertConversationVersionStmt =
                session.prepare(
                        "INSERT INTO ripple.user_conversations_version "
                                + "(user_id, version, conversation_id, peer_id, group_id, operation, "
                                + "last_read_message_id, name, avatar) "
                                + "VALUES (?, now(), ?, ?, ?, ?, ?, ?, ?)");
        this.updateNameStmt =
                session.prepare(
                        "UPDATE ripple.user_conversations "
                                + "SET name = ? "
                                + "WHERE owner_id = ? AND conversation_id = ?");
        this.updateAvatarStmt =
                session.prepare(
                        "UPDATE ripple.user_conversations "
                                + "SET avatar = ? "
                                + "WHERE owner_id = ? AND conversation_id = ?");
        this.updateLastReadMessageIdStmt =
                session.prepare(
                        "UPDATE ripple.user_conversations "
                                + "SET last_read_message_id = ? "
                                + "WHERE owner_id = ? AND conversation_id = ?");

        this.insertMessageStmt =
                session.prepare(
                        "INSERT INTO ripple.user_messages "
                                + "(conversation_id, message_id, sender_id, receiver_id, group_id, "
                                + "send_timestamp, text, file_url, file_name) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");

        this.selectConversationsFirstPageStmt =
                session.prepare(
                        "SELECT conversation_id, peer_id, group_id,"
                                + "last_read_message_id, name, avatar "
                                + "FROM ripple.user_conversations "
                                + "WHERE owner_id = ? LIMIT ?");

        this.selectConversationsNextPageStmt =
                session.prepare(
                        "SELECT conversation_id, peer_id, group_id,"
                                + "last_read_message_id, name, avatar "
                                + "FROM ripple.user_conversations "
                                + "WHERE owner_id = ? AND conversation_id > ? LIMIT ?");

        this.selectConversationChangesStmt =
                session.prepare(
                        "SELECT version, conversation_id, peer_id, group_id, operation, "
                                + "last_read_message_id, name, avatar "
                                + "FROM ripple.user_conversations_version "
                                + "WHERE user_id = ? AND version > ? LIMIT ?");

        this.selectLatestVersionStmt =
                session.prepare(
                        "SELECT version FROM ripple.user_conversations_version "
                                + "WHERE user_id = ? ORDER BY version DESC LIMIT 1");

        this.selectMessagesStmt =
                session.prepare(
                        "SELECT conversation_id, message_id, sender_id, receiver_id, group_id, "
                                + "send_timestamp, text, file_url, file_name "
                                + "FROM ripple.user_messages "
                                + "WHERE conversation_id = ? AND message_id < ? "
                                + "ORDER BY message_id LIMIT ?");

        this.selectConversationUnreadCountStmt =
                session.prepare(
                        "SELECT message_id, receiver_id, send_timestamp, text "
                                + "FROM ripple.user_messages "
                                + "WHERE conversation_id = ? AND message_id > ?");
        this.selectLatestMessageStmt =
                session.prepare(
                        "SELECT message_id,send_timestamp, text "
                                + "FROM ripple.user_messages "
                                + "WHERE conversation_id = ? ORDER BY message_id DESC LIMIT 1");
    }
}
