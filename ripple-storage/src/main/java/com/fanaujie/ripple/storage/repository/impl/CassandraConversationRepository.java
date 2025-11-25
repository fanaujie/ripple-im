package com.fanaujie.ripple.storage.repository.impl;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.*;
import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.fanaujie.ripple.protobuf.msgapiserver.SingleMessageContent;
import com.fanaujie.ripple.storage.exception.InvalidVersionException;
import com.fanaujie.ripple.storage.model.*;
import com.fanaujie.ripple.storage.repository.ConversationRepository;

import java.util.*;

public class CassandraConversationRepository implements ConversationRepository {

    private final CqlSession session;
    private final PreparedStatement existsConversationStmt;
    private final PreparedStatement insertConversationStmt;
    private final PreparedStatement insertConversationVersionStmt;
    private final PreparedStatement updateConversationStmt;
    private final PreparedStatement updateReadPositionStmt;
    private final PreparedStatement insertMessageStmt;
    private final PreparedStatement getConversationsFirstPageStmt;
    private final PreparedStatement getConversationsNextPageStmt;
    private final PreparedStatement getConversationChangesStmt;
    private final PreparedStatement getLatestVersionStmt;
    private final PreparedStatement getMessagesStmt;
    private final PreparedStatement getConversationUnreadCountStmt;

    public CassandraConversationRepository(CqlSession session) {
        this.session = session;

        // Check if conversation exists
        this.existsConversationStmt =
                session.prepare(
                        "SELECT conversation_id FROM ripple.user_conversations "
                                + "WHERE owner_id = ? AND conversation_id = ?");

        // Insert new conversation
        this.insertConversationStmt =
                session.prepare(
                        "INSERT INTO ripple.user_conversations "
                                + "(owner_id, conversation_id, peer_id, group_id, last_message_id, "
                                + "last_message, last_message_timestamp, last_read_message_id) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)");

        // Insert conversation version record
        this.insertConversationVersionStmt =
                session.prepare(
                        "INSERT INTO ripple.user_conversations_version "
                                + "(user_id, version, conversation_id, peer_id, group_id, operation, "
                                + "last_message_id, last_message, last_message_timestamp, last_read_message_id) "
                                + "VALUES (?, now(), ?, ?, ?, ?, ?, ?, ?, ?)");

        // Update conversation with new message
        this.updateConversationStmt =
                session.prepare(
                        "UPDATE ripple.user_conversations "
                                + "SET last_message_id = ?, last_message = ?, "
                                + "last_message_timestamp = ? "
                                + "WHERE owner_id = ? AND conversation_id = ?");

        // Update read position (set last_read_message_id to specified value)
        this.updateReadPositionStmt =
                session.prepare(
                        "UPDATE ripple.user_conversations "
                                + "SET last_read_message_id = ? "
                                + "WHERE owner_id = ? AND conversation_id = ?");

        // Insert message into user_messages table
        this.insertMessageStmt =
                session.prepare(
                        "INSERT INTO ripple.user_messages "
                                + "(conversation_id, message_id, sender_id, receiver_id, group_id, "
                                + "send_timestamp, text, file_url, file_name) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");

        // Get conversations with pagination
        this.getConversationsFirstPageStmt =
                session.prepare(
                        "SELECT conversation_id, peer_id, group_id, last_message_id, "
                                + "last_message, last_message_timestamp, last_read_message_id "
                                + "FROM ripple.user_conversations "
                                + "WHERE owner_id = ? LIMIT ?");

        this.getConversationsNextPageStmt =
                session.prepare(
                        "SELECT conversation_id, peer_id, group_id, last_message_id, "
                                + "last_message, last_message_timestamp, last_read_message_id "
                                + "FROM ripple.user_conversations "
                                + "WHERE owner_id = ? AND conversation_id > ? LIMIT ?");

        // Get conversation changes by version
        this.getConversationChangesStmt =
                session.prepare(
                        "SELECT version, conversation_id, peer_id, group_id, operation, "
                                + "last_message_id, last_message, last_message_timestamp, last_read_message_id "
                                + "FROM ripple.user_conversations_version "
                                + "WHERE user_id = ? AND version > ? LIMIT ?");

        // Get latest conversation version
        this.getLatestVersionStmt =
                session.prepare(
                        "SELECT version FROM ripple.user_conversations_version "
                                + "WHERE user_id = ? ORDER BY version DESC LIMIT 1");

        // Get messages before a specific message_id with pagination
        this.getMessagesStmt =
                session.prepare(
                        "SELECT conversation_id, message_id, sender_id, receiver_id, group_id, "
                                + "send_timestamp, text, file_url, file_name "
                                + "FROM ripple.user_messages "
                                + "WHERE conversation_id = ? AND message_id < ? "
                                + "ORDER BY message_id LIMIT ?");

        /*
                CREATE TABLE IF NOT EXISTS "ripple"."user_messages"
        (
            "conversation_id" text,
            "message_id"      bigint,
            "sender_id"       bigint,
            "receiver_id"     bigint,
            "group_id"        bigint,
            "send_timestamp"  bigint,
            "text"            text,
            "file_url"        text,
            "file_name"       text,
            primary key ( conversation_id, message_id )
        );
                 */
        this.getConversationUnreadCountStmt =
                session.prepare(
                        "SELECT message_id, receiver_id "
                                + "FROM ripple.user_messages "
                                + "WHERE conversation_id = ? AND message_id > ?");
    }

    @Override
    public boolean existsById(String conversationId, long ownerId) {
        Row ownerRow = session.execute(existsConversationStmt.bind(ownerId, conversationId)).one();
        return ownerRow != null;
    }

    @Override
    public void createSingeMessageConversation(String conversationId, long ownerId, long peerId) {

        // Create conversation for sender (ownerId)
        BatchStatement senderBatch =
                new BatchStatementBuilder(DefaultBatchType.LOGGED)
                        .addStatement(
                                insertConversationStmt.bind(
                                        ownerId,
                                        conversationId,
                                        peerId,
                                        null, // group_id is null for single conversations
                                        null,
                                        null,
                                        null,
                                        null)) // last_read_message_id is null initially
                        .addStatement(
                                insertConversationVersionStmt.bind(
                                        ownerId,
                                        conversationId,
                                        peerId,
                                        null,
                                        ConversationOperation.CREATE_CONVERSATION.getValue(),
                                        null, // last_message_id is null for create conversation
                                        null,
                                        null,
                                        null)) // last_read_message_id is null for create
                        // conversation
                        .build();
        session.execute(senderBatch);
    }

    @Override
    public void updateSingeMessageConversation(
            String conversationId,
            long ownerId,
            long peerId,
            long lastMessageId,
            long lastMessageTimestamp,
            SingleMessageContent singleMessageContent) {
        BatchStatementBuilder batchBuilder = new BatchStatementBuilder(DefaultBatchType.LOGGED);

        // Update conversation with new message
        batchBuilder.addStatement(
                updateConversationStmt.bind(
                        lastMessageId,
                        singleMessageContent.getText(),
                        lastMessageTimestamp,
                        ownerId,
                        conversationId));
        batchBuilder.addStatement(
                insertConversationVersionStmt.bind(
                        ownerId,
                        conversationId,
                        peerId,
                        null,
                        ConversationOperation.NEW_MESSAGE.getValue(),
                        lastMessageId,
                        singleMessageContent.getText(),
                        lastMessageTimestamp,
                        null)); // last_read_message_id not updated when new message arrives
        session.execute(batchBuilder.build());
    }

    @Override
    public void markLastReadMessageId(String conversationId, long ownerId, long readMessageId) {
        session.execute(updateReadPositionStmt.bind(readMessageId, ownerId, conversationId));
        session.execute(
                insertConversationVersionStmt.bind(
                        ownerId,
                        conversationId,
                        null, // peer_id
                        null, // group_id
                        ConversationOperation.READ_MESSAGES.getValue(),
                        null, // last_message_id
                        null, // last_message
                        null, // last_message_timestamp
                        readMessageId)); // last_read_message_id
    }

    @Override
    public void saveMessage(
            String conversationId,
            long messageId,
            long senderId,
            long receiverId,
            long groupId,
            long timestamp,
            SingleMessageContent content) {
        session.execute(
                insertMessageStmt.bind(
                        conversationId,
                        messageId,
                        senderId,
                        receiverId == 0 ? null : receiverId,
                        groupId == 0 ? null : groupId,
                        timestamp,
                        content.getText(),
                        content.getFileUrl(),
                        content.getFileName()));
    }

    @Override
    public PagedConversationResult getConversations(
            long userId, String nextPageToken, int pageSize) {
        // Query pageSize + 1 to check if there are more records
        int limit = pageSize + 1;
        ResultSet resultSet;

        if (nextPageToken == null || nextPageToken.isEmpty()) {
            // First page
            resultSet = session.execute(getConversationsFirstPageStmt.bind(userId, limit));
        } else {
            // Next page, parse token as last conversation_id
            resultSet =
                    session.execute(
                            getConversationsNextPageStmt.bind(userId, nextPageToken, limit));
        }
        List<Conversation> conversations = new ArrayList<>();
        for (Row row : resultSet) {
            String conversationId = row.getString("conversation_id");
            long lastReadMessageId = row.getLong("last_read_message_id");
            Conversation conversation = new Conversation();
            conversation.setOwnerId(userId);
            conversation.setConversationId(conversationId);
            conversation.setPeerId(row.getLong("peer_id"));
            conversation.setGroupId(row.getLong("group_id"));
            conversation.setLastMessageId(row.getLong("last_message_id"));
            conversation.setLastMessage(row.getString("last_message"));
            conversation.setLastMessageTimestamp(row.getLong("last_message_timestamp"));
            conversation.setLastReadMessageId(lastReadMessageId);
            conversation.setUnreadCount(getUnreadCount(conversationId, lastReadMessageId, userId));
            conversations.add(conversation);
        }

        // Check if there are more records
        boolean hasMore = conversations.size() > pageSize;
        String nextToken = null;

        if (hasMore) {
            // Remove the extra record and set next token
            conversations.remove(conversations.size() - 1);
            nextToken = conversations.get(conversations.size() - 1).getConversationId();
        }

        return new PagedConversationResult(conversations, nextToken, hasMore);
    }

    @Override
    public List<ConversationVersionChange> getConversationChanges(
            long userId, String afterVersion, int limit) throws InvalidVersionException {
        // Validate afterVersion parameter
        if (afterVersion == null || afterVersion.isEmpty()) {
            throw new InvalidVersionException(
                    "afterVersion cannot be null or empty. Use version from previous sync or call with fullSync.");
        }

        UUID afterVersionUuid;
        try {
            afterVersionUuid = Uuids.endOf(Long.parseLong(afterVersion));
        } catch (NumberFormatException e) {
            throw new InvalidVersionException(
                    "Invalid version format: "
                            + afterVersion
                            + ". Version must be a valid timestamp.");
        }

        if (afterVersionUuid.version() != 1) {
            throw new InvalidVersionException(
                    "Invalid version format: "
                            + afterVersion
                            + ". Version must be a valid UUIDv1.");
        }

        ResultSet resultSet =
                session.execute(getConversationChangesStmt.bind(userId, afterVersionUuid, limit));

        List<ConversationVersionChange> changes = new ArrayList<>();
        for (Row row : resultSet) {
            ConversationVersionChange record = new ConversationVersionChange();
            record.setVersion(String.valueOf(Uuids.unixTimestamp(row.getUuid("version"))));
            record.setConversationId(row.getString("conversation_id"));
            record.setPeerId(row.getLong("peer_id"));
            record.setGroupId(row.getLong("group_id"));
            record.setOperation(row.getByte("operation"));
            record.setLastMessageId(row.getLong("last_message_id"));
            record.setLastMessage(row.getString("last_message"));
            record.setLastMessageTimestamp(row.getLong("last_message_timestamp"));
            record.setLastReadMessageId(row.getLong("last_read_message_id"));
            changes.add(record);
        }
        return changes;
    }

    @Override
    public String getLatestConversationVersion(long userId) {
        Row row = session.execute(getLatestVersionStmt.bind(userId)).one();
        if (row == null) {
            return null;
        }
        return String.valueOf(Uuids.unixTimestamp(row.getUuid("version")));
    }

    @Override
    public Messages getMessages(String conversationId, long beforeMessageId, int pageSize) {
        // Query pageSize + 1 to check if there are more records
        int limit = pageSize + 1;

        // When beforeMessageId is 0, use Long.MAX_VALUE to get the latest messages
        long effectiveBeforeMessageId = (beforeMessageId == 0) ? Long.MAX_VALUE : beforeMessageId;

        ResultSet resultSet =
                session.execute(
                        getMessagesStmt.bind(conversationId, effectiveBeforeMessageId, limit));

        List<Message> messages = new ArrayList<>();
        for (Row row : resultSet) {
            Message message = new Message();
            message.setConversationId(row.getString("conversation_id"));
            message.setMessageId(row.getLong("message_id"));
            message.setSenderId(row.getLong("sender_id"));
            message.setReceiverId(row.getLong("receiver_id"));
            message.setGroupId(row.getLong("group_id"));
            message.setSendTimestamp(row.getLong("send_timestamp"));
            message.setText(row.getString("text"));
            message.setFileUrl(row.getString("file_url"));
            message.setFileName(row.getString("file_name"));
            messages.add(message);
        }
        return new Messages(messages);
    }

    private int getUnreadCount(String conversationId, long lastReadMessageId, long receiverId) {
        ResultSet resultSet =
                session.execute(
                        getConversationUnreadCountStmt.bind(conversationId, lastReadMessageId));
        int count = 0;
        for (Row row : resultSet) {
            long rowReceiverId = row.getLong("receiver_id");
            if (rowReceiverId == receiverId) {
                count++;
            }
        }
        return count;
    }
}
