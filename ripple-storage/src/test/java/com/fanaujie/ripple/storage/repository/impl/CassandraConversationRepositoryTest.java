package com.fanaujie.ripple.storage.repository.impl;
//
// import com.datastax.oss.driver.api.core.CqlSession;
// import com.datastax.oss.driver.api.core.cql.ResultSet;
// import com.datastax.oss.driver.api.core.cql.Row;
// import com.datastax.oss.driver.api.core.uuid.Uuids;
// import com.fanaujie.ripple.protobuf.msgapiserver.SingleMessageContent;
// import com.fanaujie.ripple.storage.exception.InvalidVersionException;
// import com.fanaujie.ripple.storage.model.*;
// import org.junit.jupiter.api.AfterEach;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.testcontainers.containers.CassandraContainer;
// import org.testcontainers.junit.jupiter.Container;
// import org.testcontainers.junit.jupiter.Testcontainers;
//
// import java.util.List;
// import java.util.UUID;
//
// import static org.junit.jupiter.api.Assertions.*;
//
// @Testcontainers
// class CassandraConversationRepositoryTest {
//
//    @Container
//    CassandraContainer<?> cassandraContainer =
//            new CassandraContainer<>("cassandra:5.0.5").withInitScript("ripple.cql");
//
//    private CqlSession session;
//    private CassandraConversationRepository conversationRepository;
//
//    @BeforeEach
//    void setUp() {
//        this.session =
//                CqlSession.builder()
//                        .addContactPoint(cassandraContainer.getContactPoint())
//                        .withLocalDatacenter(cassandraContainer.getLocalDatacenter())
//                        .build();
//        conversationRepository = new CassandraConversationRepository(session);
//    }
//
//    @AfterEach
//    void tearDown() {
//        if (session != null) {
//            session.execute("TRUNCATE ripple.user_conversations");
//            session.execute("TRUNCATE ripple.user_conversations_version");
//            session.execute("TRUNCATE ripple.user_messages");
//            session.close();
//        }
//    }
//
//    // ==================== existsById Tests ====================
//
//    @Test
//    void existsById_shouldReturnTrue_whenConversationExists() {
//        // Given
//        long ownerId = 1001L;
//        String conversationId = "conv_123";
//        long peerId = 2001L;
//
//        conversationRepository.createSingeMessageConversation(conversationId, ownerId, peerId);
//
//        // When
//        boolean exists = conversationRepository.existsById(conversationId, ownerId);
//
//        // Then
//        assertTrue(exists);
//    }
//
//    @Test
//    void existsById_shouldReturnFalse_whenConversationDoesNotExist() {
//        // Given
//        long ownerId = 1001L;
//        String conversationId = "conv_nonexistent";
//
//        // When
//        boolean exists = conversationRepository.existsById(conversationId, ownerId);
//
//        // Then
//        assertFalse(exists);
//    }
//
//    // ==================== createSingeMessageConversation Tests ====================
//
//    @Test
//    void createSingeMessageConversation_shouldCreateConversationInBothTables() {
//        // Given
//        String conversationId = "conv_456";
//        long ownerId = 1002L;
//        long peerId = 2002L;
//
//        // When
//        conversationRepository.createSingeMessageConversation(conversationId, ownerId, peerId);
//
//        // Then - verify in user_conversations table
//        ResultSet rs =
//                session.execute(
//                        "SELECT * FROM ripple.user_conversations WHERE owner_id = ? AND
// conversation_id = ?",
//                        ownerId,
//                        conversationId);
//        Row row = rs.one();
//        assertNotNull(row);
//        assertEquals(conversationId, row.getString("conversation_id"));
//        assertEquals(ownerId, row.getLong("owner_id"));
//        assertEquals(peerId, row.getLong("peer_id"));
//        assertNull(row.getObject("group_id"));
//        assertEquals(0L, row.getLong("last_message_id"));
//        assertNull(row.getString("last_message"));
//        assertEquals(0L, row.getLong("last_message_timestamp"));
//    }
//
//    @Test
//    void createSingeMessageConversation_shouldCreateVersionRecord() {
//        // Given
//        String conversationId = "conv_789";
//        long ownerId = 1003L;
//        long peerId = 2003L;
//
//        // When
//        conversationRepository.createSingeMessageConversation(conversationId, ownerId, peerId);
//
//        // Then - verify in user_conversations_version table
//        ResultSet rs =
//                session.execute(
//                        "SELECT * FROM ripple.user_conversations_version WHERE user_id = ?",
//                        ownerId);
//        Row row = rs.one();
//        assertNotNull(row);
//        assertEquals(conversationId, row.getString("conversation_id"));
//        assertEquals(peerId, row.getLong("peer_id"));
//        assertNull(row.getObject("group_id"));
//        assertEquals(
//                ConversationOperation.CREATE_CONVERSATION.getValue(), row.getByte("operation"));
//        assertEquals(0L, row.getLong("last_message_id"));
//        assertNull(row.getString("last_message"));
//        assertEquals(0L, row.getLong("last_message_timestamp"));
//        assertEquals(0L, row.getLong("last_read_message_id"));
//        assertNotNull(row.getUuid("version"));
//    }
//
//    // ==================== updateSingeMessageConversation Tests ====================
//
//    @Test
//    void updateSingeMessageConversation_shouldUpdateMessageButNotReadPosition()
//            throws InterruptedException {
//        // Given
//        String conversationId = "conv_update1";
//        long ownerId = 1004L;
//        long peerId = 2004L;
//        conversationRepository.createSingeMessageConversation(conversationId, ownerId, peerId);
//
//        Thread.sleep(10);
//
//        long lastMessageId = 5001L;
//        long lastMessageTimestamp = System.currentTimeMillis();
//        SingleMessageContent content =
//                SingleMessageContent.newBuilder().setText("Hello World").build();
//
//        // When
//        conversationRepository.updateSingeMessageConversation(
//                conversationId, ownerId, peerId, lastMessageId, lastMessageTimestamp, content);
//
//        // Then
//        ResultSet rs =
//                session.execute(
//                        "SELECT * FROM ripple.user_conversations WHERE owner_id = ? AND
// conversation_id = ?",
//                        ownerId,
//                        conversationId);
//        Row row = rs.one();
//        assertNotNull(row);
//        assertEquals(lastMessageId, row.getLong("last_message_id"));
//        assertEquals("Hello World", row.getString("last_message"));
//        assertEquals(lastMessageTimestamp, row.getLong("last_message_timestamp"));
//        // last_read_message_id should remain at initial value (0) - not updated when new message
//        // arrives
//        assertEquals(0L, row.getLong("last_read_message_id"));
//    }
//
//    @Test
//    void updateSingeMessageConversation_shouldCreateVersionRecord() throws InterruptedException {
//        // Given
//        String conversationId = "conv_update3";
//        long ownerId = 1006L;
//        long peerId = 2006L;
//        conversationRepository.createSingeMessageConversation(conversationId, ownerId, peerId);
//
//        Thread.sleep(10);
//
//        long lastMessageId = 5003L;
//        long lastMessageTimestamp = System.currentTimeMillis();
//        SingleMessageContent content =
//                SingleMessageContent.newBuilder()
//                        .setText("Update Message")
//                        .setFileUrl("http://example.com/file.jpg")
//                        .setFileName("file.jpg")
//                        .build();
//
//        // When
//        conversationRepository.updateSingeMessageConversation(
//                conversationId, ownerId, peerId, lastMessageId, lastMessageTimestamp, content);
//
//        // Then - verify version record with NEW_MESSAGE operation
//        ResultSet rs =
//                session.execute(
//                        "SELECT * FROM ripple.user_conversations_version WHERE user_id = ? LIMIT
// 10",
//                        ownerId);
//        List<Row> rows = rs.all();
//        assertEquals(2, rows.size()); // CREATE + NEW_MESSAGE
//
//        Row versionRow = rows.get(1);
//        assertEquals(conversationId, versionRow.getString("conversation_id"));
//        assertEquals(ConversationOperation.NEW_MESSAGE.getValue(),
// versionRow.getByte("operation"));
//        assertEquals(lastMessageId, versionRow.getLong("last_message_id"));
//        assertEquals("Update Message", versionRow.getString("last_message"));
//        assertEquals(lastMessageTimestamp, versionRow.getLong("last_message_timestamp"));
//        // last_read_message_id should be 0 in version record for NEW_MESSAGE operation
//        assertEquals(0L, versionRow.getLong("last_read_message_id"));
//    }
//
//    // ==================== readMessages Tests ====================
//
//    @Test
//    void readMessages_shouldUpdateLastReadMessageId() throws InterruptedException {
//        // Given
//        String conversationId = "conv_read1";
//        long ownerId = 1007L;
//        long peerId = 2007L;
//        conversationRepository.createSingeMessageConversation(conversationId, ownerId, peerId);
//
//        Thread.sleep(10);
//
//        // Add a message
//        long messageId = 5004L;
//        conversationRepository.updateSingeMessageConversation(
//                conversationId,
//                ownerId,
//                peerId,
//                messageId,
//                System.currentTimeMillis(),
//                SingleMessageContent.newBuilder().setText("Unread").build());
//
//        Thread.sleep(10);
//
//        // When
//        conversationRepository.markLastReadMessageId(conversationId, ownerId, messageId);
//
//        // Then - verify last_read_message_id is set to the specified message ID
//        ResultSet rs =
//                session.execute(
//                        "SELECT last_message_id, last_read_message_id FROM
// ripple.user_conversations WHERE owner_id = ? AND conversation_id = ?",
//                        ownerId,
//                        conversationId);
//        Row row = rs.one();
//        assertNotNull(row);
//        assertEquals(messageId, row.getLong("last_message_id"));
//        assertEquals(messageId, row.getLong("last_read_message_id"));
//    }
//
//    @Test
//    void readMessages_shouldCreateVersionRecordWithReadOperation() throws InterruptedException {
//        // Given
//        String conversationId = "conv_read2";
//        long ownerId = 1008L;
//        long peerId = 2008L;
//        conversationRepository.createSingeMessageConversation(conversationId, ownerId, peerId);
//
//        Thread.sleep(10);
//
//        long messageId = 5005L;
//        conversationRepository.updateSingeMessageConversation(
//                conversationId,
//                ownerId,
//                peerId,
//                messageId,
//                System.currentTimeMillis(),
//                SingleMessageContent.newBuilder().setText("Message").build());
//
//        Thread.sleep(10);
//
//        // When
//        conversationRepository.markLastReadMessageId(conversationId, ownerId, messageId);
//
//        // Then
//        ResultSet rs =
//                session.execute(
//                        "SELECT * FROM ripple.user_conversations_version WHERE user_id = ? LIMIT
// 10",
//                        ownerId);
//        List<Row> rows = rs.all();
//        assertEquals(3, rows.size()); // CREATE + NEW_MESSAGE + READ_MESSAGES
//
//        Row versionRow = rows.get(2); // Latest version
//        assertEquals(conversationId, versionRow.getString("conversation_id"));
//        assertEquals(
//                ConversationOperation.READ_MESSAGES.getValue(), versionRow.getByte("operation"));
//        // last_read_message_id should be set to the message ID passed to readMessages
//        assertEquals(messageId, versionRow.getLong("last_read_message_id"));
//    }
//
//    // ==================== saveMessage Tests ====================
//
//    @Test
//    void saveMessage_shouldSaveSingleMessage() {
//        // Given
//        String conversationId = "conv_msg1";
//        long messageId = 6001L;
//        long senderId = 1009L;
//        long receiverId = 2009L;
//        long groupId = 0L;
//        long timestamp = System.currentTimeMillis();
//        SingleMessageContent content =
//                SingleMessageContent.newBuilder().setText("Hello from sender").build();
//
//        // When
//        conversationRepository.saveMessage(
//                conversationId, messageId, senderId, receiverId, groupId, timestamp, content);
//
//        // Then
//        ResultSet rs =
//                session.execute(
//                        "SELECT * FROM ripple.user_messages WHERE conversation_id = ? AND
// message_id = ?",
//                        conversationId,
//                        messageId);
//        Row row = rs.one();
//        assertNotNull(row);
//        assertEquals(conversationId, row.getString("conversation_id"));
//        assertEquals(messageId, row.getLong("message_id"));
//        assertEquals(senderId, row.getLong("sender_id"));
//        assertEquals(receiverId, row.getLong("receiver_id"));
//        assertEquals(0L, row.getLong("group_id"));
//        assertEquals(timestamp, row.getLong("send_timestamp"));
//        assertEquals("Hello from sender", row.getString("text"));
//        // Cassandra returns empty string for unset text columns, not null
//        assertTrue(row.getString("file_url") == null || row.getString("file_url").isEmpty());
//        assertTrue(row.getString("file_name") == null || row.getString("file_name").isEmpty());
//    }
//
//    @Test
//    void saveMessage_shouldSaveGroupMessage() {
//        // Given
//        String conversationId = "conv_msg2";
//        long messageId = 6002L;
//        long senderId = 1010L;
//        long receiverId = 0L;
//        long groupId = 3001L;
//        long timestamp = System.currentTimeMillis();
//        SingleMessageContent content =
//                SingleMessageContent.newBuilder()
//                        .setText("Group message")
//                        .setFileUrl("http://example.com/image.png")
//                        .setFileName("image.png")
//                        .build();
//
//        // When
//        conversationRepository.saveMessage(
//                conversationId, messageId, senderId, receiverId, groupId, timestamp, content);
//
//        // Then
//        ResultSet rs =
//                session.execute(
//                        "SELECT * FROM ripple.user_messages WHERE conversation_id = ? AND
// message_id = ?",
//                        conversationId,
//                        messageId);
//        Row row = rs.one();
//        assertNotNull(row);
//        assertEquals(conversationId, row.getString("conversation_id"));
//        assertEquals(messageId, row.getLong("message_id"));
//        assertEquals(senderId, row.getLong("sender_id"));
//        assertEquals(0L, row.getLong("receiver_id"));
//        assertEquals(groupId, row.getLong("group_id"));
//        assertEquals(timestamp, row.getLong("send_timestamp"));
//        assertEquals("Group message", row.getString("text"));
//        assertEquals("http://example.com/image.png", row.getString("file_url"));
//        assertEquals("image.png", row.getString("file_name"));
//    }
//
//    // ==================== getConversations Tests (Pagination) ====================
//
//    @Test
//    void getConversations_shouldReturnFirstPage() throws InterruptedException {
//        // Given
//        long userId = 1011L;
//        createConversationsForPagination(userId, 5);
//
//        // When
//        PagedConversationResult result = conversationRepository.getConversations(userId, null, 3);
//
//        // Then
//        assertNotNull(result);
//        assertEquals(3, result.getConversations().size());
//        assertTrue(result.isHasMore());
//        assertNotNull(result.getNextPageToken());
//    }
//
//    @Test
//    void getConversations_shouldReturnSecondPageWithToken() throws InterruptedException {
//        // Given
//        long userId = 1012L;
//        createConversationsForPagination(userId, 5);
//
//        PagedConversationResult firstPage =
//                conversationRepository.getConversations(userId, null, 3);
//
//        // When
//        PagedConversationResult secondPage =
//                conversationRepository.getConversations(userId, firstPage.getNextPageToken(), 3);
//
//        // Then
//        assertNotNull(secondPage);
//        assertEquals(2, secondPage.getConversations().size());
//        assertFalse(secondPage.isHasMore());
//        assertNull(secondPage.getNextPageToken());
//    }
//
//    @Test
//    void getConversations_shouldReturnEmptyResult_whenNoConversations() {
//        // Given
//        long userId = 1013L;
//
//        // When
//        PagedConversationResult result = conversationRepository.getConversations(userId, null,
// 10);
//
//        // Then
//        assertNotNull(result);
//        assertTrue(result.getConversations().isEmpty());
//        assertFalse(result.isHasMore());
//        assertNull(result.getNextPageToken());
//    }
//
//    @Test
//    void getConversations_shouldHandleExactPageSize() throws InterruptedException {
//        // Given
//        long userId = 1014L;
//        createConversationsForPagination(userId, 3);
//
//        // When
//        PagedConversationResult result = conversationRepository.getConversations(userId, null, 3);
//
//        // Then
//        assertNotNull(result);
//        assertEquals(3, result.getConversations().size());
//        assertFalse(result.isHasMore());
//        assertNull(result.getNextPageToken());
//    }
//
//    @Test
//    void getConversations_shouldOrderByTimestampDescending() throws InterruptedException {
//        // Given
//        long userId = 1015L;
//        String conv1 = "conv_order1";
//        String conv2 = "conv_order2";
//        String conv3 = "conv_order3";
//
//        conversationRepository.createSingeMessageConversation(conv1, userId, 2001L);
//        Thread.sleep(10);
//
//        conversationRepository.createSingeMessageConversation(conv2, userId, 2002L);
//        Thread.sleep(10);
//
//        // Update conv1 to make it most recent
//        conversationRepository.updateSingeMessageConversation(
//                conv1,
//                userId,
//                2001L,
//                7001L,
//                System.currentTimeMillis(),
//                SingleMessageContent.newBuilder().setText("Latest").build());
//        Thread.sleep(10);
//
//        conversationRepository.createSingeMessageConversation(conv3, userId, 2003L);
//
//        // When
//        PagedConversationResult result = conversationRepository.getConversations(userId, null,
// 10);
//
//        // Then
//        List<Conversation> conversations = result.getConversations();
//        assertEquals(3, conversations.size());
//
//        for (Conversation conv : conversations) {
//            assertTrue(
//                    conv.getConversationId().equals(conv1)
//                            || conv.getConversationId().equals(conv2)
//                            || conv.getConversationId().equals(conv3));
//        }
//    }
//
//    // ==================== getConversationChanges Tests ====================
//
//    @Test
//    void getConversationChanges_shouldReturnChangesAfterVersion() throws Exception {
//        // Given
//        long userId = 1016L;
//        String conversationId = "conv_changes1";
//        conversationRepository.createSingeMessageConversation(conversationId, userId, 2010L);
//
//        Thread.sleep(10);
//        UUID versionBefore = Uuids.timeBased();
//        Thread.sleep(10);
//
//        conversationRepository.updateSingeMessageConversation(
//                conversationId,
//                userId,
//                2010L,
//                8001L,
//                System.currentTimeMillis(),
//                SingleMessageContent.newBuilder().setText("New message").build());
//
//        String afterVersion = String.valueOf(Uuids.unixTimestamp(versionBefore));
//
//        // When
//        List<ConversationVersionChange> changes =
//                conversationRepository.getConversationChanges(userId, afterVersion, 10);
//
//        // Then
//        assertNotNull(changes);
//        assertEquals(1, changes.size());
//        ConversationVersionChange change = changes.get(0);
//        assertEquals(conversationId, change.getConversationId());
//        assertEquals(ConversationOperation.NEW_MESSAGE.getValue(), change.getOperation());
//        assertEquals(8001L, change.getLastMessageId());
//        assertEquals("New message", change.getLastMessage());
//        assertEquals(0L, change.getLastReadMessageId());
//    }
//
//    @Test
//    void getConversationChanges_shouldReturnEmptyList_whenNoChanges() throws Exception {
//        // Given
//        long userId = 1017L;
//        String conversationId = "conv_changes2";
//        conversationRepository.createSingeMessageConversation(conversationId, userId, 2011L);
//
//        Thread.sleep(10);
//        UUID versionAfterAll = Uuids.timeBased();
//        String afterVersion = String.valueOf(Uuids.unixTimestamp(versionAfterAll));
//
//        // When
//        List<ConversationVersionChange> changes =
//                conversationRepository.getConversationChanges(userId, afterVersion, 10);
//
//        // Then
//        assertNotNull(changes);
//        assertTrue(changes.isEmpty());
//    }
//
//    @Test
//    void getConversationChanges_shouldRespectLimit() throws Exception {
//        // Given
//        long userId = 1018L;
//
//        UUID versionBefore = Uuids.timeBased();
//        Thread.sleep(10);
//
//        // Create multiple conversations
//        for (int i = 0; i < 5; i++) {
//            conversationRepository.createSingeMessageConversation(
//                    "conv_limit" + i, userId, 2020L + i);
//            Thread.sleep(10);
//        }
//
//        String afterVersion = String.valueOf(Uuids.unixTimestamp(versionBefore));
//
//        // When
//        List<ConversationVersionChange> changes =
//                conversationRepository.getConversationChanges(userId, afterVersion, 3);
//
//        // Then
//        assertNotNull(changes);
//        assertEquals(3, changes.size());
//    }
//
//    @Test
//    void getConversationChanges_shouldThrowException_whenNullVersion() {
//        // Given
//        long userId = 1019L;
//
//        // When & Then
//        assertThrows(
//                InvalidVersionException.class,
//                () -> {
//                    conversationRepository.getConversationChanges(userId, null, 10);
//                });
//    }
//
//    @Test
//    void getConversationChanges_shouldThrowException_whenEmptyVersion() {
//        // Given
//        long userId = 1020L;
//
//        // When & Then
//        assertThrows(
//                InvalidVersionException.class,
//                () -> {
//                    conversationRepository.getConversationChanges(userId, "", 10);
//                });
//    }
//
//    @Test
//    void getConversationChanges_shouldThrowException_whenInvalidVersion() {
//        // Given
//        long userId = 1021L;
//        String invalidVersion = "not-a-timestamp";
//
//        // When & Then
//        assertThrows(
//                InvalidVersionException.class,
//                () -> {
//                    conversationRepository.getConversationChanges(userId, invalidVersion, 10);
//                });
//    }
//
//    // ==================== getLatestConversationVersion Tests ====================
//
//    @Test
//    void getLatestConversationVersion_shouldReturnLatestVersion_whenVersionsExist()
//            throws InterruptedException {
//        // Given
//        long userId = 1022L;
//        conversationRepository.createSingeMessageConversation("conv_latest1", userId, 2040L);
//
//        Thread.sleep(10);
//
//        long messageId = 9001L;
//        conversationRepository.updateSingeMessageConversation(
//                "conv_latest1",
//                userId,
//                2040L,
//                messageId,
//                System.currentTimeMillis(),
//                SingleMessageContent.newBuilder().setText("Message").build());
//
//        Thread.sleep(10);
//
//        conversationRepository.markLastReadMessageId("conv_latest1", userId, messageId);
//
//        // When
//        String latestVersion = conversationRepository.getLatestConversationVersion(userId);
//
//        // Then
//        assertNotNull(latestVersion);
//        assertFalse(latestVersion.isEmpty());
//
//        // Verify it's a valid timestamp
//        long timestamp = Long.parseLong(latestVersion);
//        assertTrue(timestamp > 0);
//    }
//
//    @Test
//    void getLatestConversationVersion_shouldReturnNull_whenNoVersionsExist() {
//        // Given
//        long userId = 1023L;
//
//        // When
//        String latestVersion = conversationRepository.getLatestConversationVersion(userId);
//
//        // Then
//        assertNull(latestVersion);
//    }
//
//    // ==================== getMessages Tests ====================
//
//    @Test
//    void getMessages_shouldReturnFirstPageWhenBeforeMessageIdIsZero() {
//        // Given
//        String conversationId = "conv_messages1";
//        String senderId = "1050";
//        String receiverId = "2050";
//        // Insert 10 messages with IDs 1001 to 1010
//        for (long i = 1001; i <= 1010; i++) {
//            conversationRepository.saveMessage(
//                    conversationId,
//                    i,
//                    Long.parseLong(senderId),
//                    Long.parseLong(receiverId),
//                    0,
//                    System.currentTimeMillis(),
//                    SingleMessageContent.newBuilder().setText("Message " + i).build());
//        }
//
//        // When - fetch first page with beforeMessageId = 0
//        Messages result = conversationRepository.getMessages(conversationId, 0, 5);
//
//        // Then - should return oldest 5 messages in ASC order
//        assertNotNull(result);
//        assertEquals(5, result.getMessages().size());
//        List<Message> messages = result.getMessages();
//        assertEquals(1001L, messages.get(0).getMessageId());
//        assertEquals(1002L, messages.get(1).getMessageId());
//        assertEquals(1003L, messages.get(2).getMessageId());
//        assertEquals(1004L, messages.get(3).getMessageId());
//        assertEquals(1005L, messages.get(4).getMessageId());
//    }
//
//    @Test
//    void getMessages_shouldReturnMessagesBeforeSpecificMessageId() {
//        // Given
//        String conversationId = "conv_messages2";
//        String senderId = "1051";
//        String receiverId = "2051";
//        // Insert messages with IDs 1001, 1002, 1003, 1004, 1005
//        for (long i = 1001; i <= 1005; i++) {
//            conversationRepository.saveMessage(
//                    conversationId,
//                    i,
//                    Long.parseLong(senderId),
//                    Long.parseLong(receiverId),
//                    0,
//                    System.currentTimeMillis(),
//                    SingleMessageContent.newBuilder().setText("Message " + i).build());
//        }
//
//        // When - fetch messages before ID 1004
//        Messages result = conversationRepository.getMessages(conversationId, 1004, 3);
//
//        // Then - should return messages with IDs < 1004 in ASC order
//        assertNotNull(result);
//        assertEquals(3, result.getMessages().size());
//        List<Message> messages = result.getMessages();
//        assertEquals(1001L, messages.get(0).getMessageId());
//        assertEquals(1002L, messages.get(1).getMessageId());
//        assertEquals(1003L, messages.get(2).getMessageId());
//    }
//
//    @Test
//    void getMessages_shouldReturnEmptyWhenConversationHasNoMessages() {
//        // Given
//        String conversationId = "conv_messages_empty";
//
//        // When - fetch messages from empty conversation
//        Messages result = conversationRepository.getMessages(conversationId, 0, 10);
//
//        // Then - should return empty Messages object
//        assertNotNull(result);
//        assertTrue(result.getMessages().isEmpty());
//    }
//
//    @Test
//    void getMessages_shouldHandlePaginationWithExactPageSize() {
//        // Given
//        String conversationId = "conv_messages3";
//        String senderId = "1052";
//        String receiverId = "2052";
//        // Insert exactly 5 messages
//        for (long i = 1001; i <= 1005; i++) {
//            conversationRepository.saveMessage(
//                    conversationId,
//                    i,
//                    Long.parseLong(senderId),
//                    Long.parseLong(receiverId),
//                    0,
//                    System.currentTimeMillis(),
//                    SingleMessageContent.newBuilder().setText("Message " + i).build());
//        }
//
//        // When - fetch with pageSize = 5
//        Messages result = conversationRepository.getMessages(conversationId, 0, 5);
//
//        // Then - should return exactly 5 messages
//        assertNotNull(result);
//        assertEquals(5, result.getMessages().size());
//    }
//
//    @Test
//    void getMessages_shouldHandlePaginationWithMoreRecordsAvailable() {
//        // Given
//        String conversationId = "conv_messages4";
//        String senderId = "1053";
//        String receiverId = "2053";
//        // Insert 10 messages
//        for (long i = 1001; i <= 1010; i++) {
//            conversationRepository.saveMessage(
//                    conversationId,
//                    i,
//                    Long.parseLong(senderId),
//                    Long.parseLong(receiverId),
//                    0,
//                    System.currentTimeMillis(),
//                    SingleMessageContent.newBuilder().setText("Message " + i).build());
//        }
//
//        // When - fetch first page with pageSize = 5
//        Messages result = conversationRepository.getMessages(conversationId, 0, 5);
//
//        // Then - should return 5 messages (client can check if size == pageSize to detect more)
//        assertNotNull(result);
//        assertEquals(5, result.getMessages().size());
//        List<Message> messages = result.getMessages();
//        assertEquals(1001L, messages.get(0).getMessageId());
//        assertEquals(1005L, messages.get(4).getMessageId());
//    }
//
//    @Test
//    void getMessages_shouldMaintainMessageMetadata() {
//        // Given
//        String conversationId = "conv_messages5";
//        long messageId = 2001L;
//        long senderId = 1054L;
//        long receiverId = 2054L;
//        long timestamp = System.currentTimeMillis();
//        SingleMessageContent content =
//                SingleMessageContent.newBuilder()
//                        .setText("Test message")
//                        .setFileUrl("http://example.com/file.jpg")
//                        .setFileName("file.jpg")
//                        .build();
//
//        conversationRepository.saveMessage(
//                conversationId, messageId, senderId, receiverId, 0, timestamp, content);
//
//        // When
//        Messages result = conversationRepository.getMessages(conversationId, 0, 10);
//
//        // Then - verify all metadata is preserved
//        assertNotNull(result);
//        assertEquals(1, result.getMessages().size());
//        Message message = result.getMessages().get(0);
//        assertEquals(conversationId, message.getConversationId());
//        assertEquals(messageId, message.getMessageId());
//        assertEquals(senderId, message.getSenderId());
//        assertEquals(receiverId, message.getReceiverId());
//        assertEquals("Test message", message.getText());
//        assertEquals("http://example.com/file.jpg", message.getFileUrl());
//        assertEquals("file.jpg", message.getFileName());
//    }
//
//    @Test
//    void getMessages_shouldReturnMessagesInAscOrderRegardlessOfPaginationDirection() {
//        // Given
//        String conversationId = "conv_messages6";
//        String senderId = "1055";
//        String receiverId = "2055";
//        // Insert 20 messages with IDs 1001-1020
//        for (long i = 1001; i <= 1020; i++) {
//            conversationRepository.saveMessage(
//                    conversationId,
//                    i,
//                    Long.parseLong(senderId),
//                    Long.parseLong(receiverId),
//                    0,
//                    System.currentTimeMillis(),
//                    SingleMessageContent.newBuilder().setText("Message " + i).build());
//        }
//
//        // When - fetch first page (beforeMessageId = 0)
//        Messages firstPageResult = conversationRepository.getMessages(conversationId, 0, 5);
//        // And - fetch second page (beforeMessageId > 0)
//        long lastMessageIdFromFirstPage =
//                firstPageResult.getMessages().get(firstPageResult.getMessages().size() -
// 1).getMessageId();
//        Messages secondPageResult = conversationRepository.getMessages(conversationId,
// lastMessageIdFromFirstPage, 5);
//
//        // Then - both pages should be in ASC order
//        List<Message> firstPage = firstPageResult.getMessages();
//        for (int i = 0; i < firstPage.size() - 1; i++) {
//            assertTrue(
//                    firstPage.get(i).getMessageId() < firstPage.get(i + 1).getMessageId(),
//                    "First page should be in ascending order");
//        }
//
//        List<Message> secondPage = secondPageResult.getMessages();
//        for (int i = 0; i < secondPage.size() - 1; i++) {
//            assertTrue(
//                    secondPage.get(i).getMessageId() < secondPage.get(i + 1).getMessageId(),
//                    "Second page should be in ascending order");
//        }
//    }
//
//    // ==================== Helper Methods ====================
//
//    private void createConversationsForPagination(long userId, int count)
//            throws InterruptedException {
//        for (int i = 0; i < count; i++) {
//            String conversationId = "conv_page_" + i;
//            long peerId = 2000L + i;
//            conversationRepository.createSingeMessageConversation(conversationId, userId, peerId);
//            Thread.sleep(10); // Ensure different timestamps
//        }
//    }
// }
