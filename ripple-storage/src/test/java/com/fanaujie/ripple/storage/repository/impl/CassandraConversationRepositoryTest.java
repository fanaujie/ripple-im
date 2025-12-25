package com.fanaujie.ripple.storage.repository.impl;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.fanaujie.ripple.storage.exception.InvalidVersionException;
import com.fanaujie.ripple.storage.exception.NotFoundUserProfileException;
import com.fanaujie.ripple.storage.model.*;
import com.fanaujie.ripple.storage.service.impl.cassandra.CassandraStorageFacade;
import com.fanaujie.ripple.storage.service.impl.cassandra.CassandraStorageFacadeBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class CassandraConversationRepositoryTest {

    @Container
    static CassandraContainer<?> cassandraContainer =
            new CassandraContainer<>("cassandra:5.0.2").withInitScript("ripple.cql");

    private CqlSession session;
    private CassandraStorageFacade storageFacade;

    @BeforeEach
    void setUp() {
        this.session =
                CqlSession.builder()
                        .addContactPoint(cassandraContainer.getContactPoint())
                        .withLocalDatacenter(cassandraContainer.getLocalDatacenter())
                        .build();
        storageFacade = new CassandraStorageFacadeBuilder().cqlSession(session).build();
    }

    @AfterEach
    void tearDown() {
        if (session != null) {
            session.execute("TRUNCATE ripple.user");
            session.execute("TRUNCATE ripple.user_profile");
            session.execute("TRUNCATE ripple.user_relations");
            session.execute("TRUNCATE ripple.user_conversations");
            session.execute("TRUNCATE ripple.user_conversations_version");
            session.execute("TRUNCATE ripple.user_messages");
            session.close();
        }
    }

    private void createUserProfile(long userId, String account, String nickName, String avatar) {
        User user = new User(userId, account, "password", User.DEFAULT_ROLE_USER, (byte) 0);
        storageFacade.insertUser(user, nickName, avatar);
    }

    // ==================== existsById Tests ====================

    @Test
    void existsById_shouldReturnTrue_whenConversationExists() throws NotFoundUserProfileException {
        long ownerId = 1001L;
        long peerId = 2001L;
        createUserProfile(ownerId, "owner1", "Owner One", "avatar1.png");
        createUserProfile(peerId, "peer1", "Peer One", "avatar2.png");

        String conversationId =
                com.fanaujie.ripple.storage.service.utils.ConversationUtils.generateConversationId(
                        ownerId, peerId);
        storageFacade.createSingeMessageConversation(conversationId, ownerId, peerId);

        boolean exists = storageFacade.existsByConversationId(conversationId, ownerId);
        assertTrue(exists);
    }

    @Test
    void existsById_shouldReturnFalse_whenConversationDoesNotExist() {
        long ownerId = 1001L;
        String conversationId = "conv_nonexistent";

        boolean exists = storageFacade.existsByConversationId(conversationId, ownerId);
        assertFalse(exists);
    }

    // ==================== createSingeMessageConversation Tests ====================

    @Test
    void createSingeMessageConversation_shouldCreateConversation()
            throws NotFoundUserProfileException {
        long ownerId = 1002L;
        long peerId = 2002L;
        createUserProfile(ownerId, "owner2", "Owner Two", "avatar1.png");
        createUserProfile(peerId, "peer2", "Peer Two", "avatar2.png");

        String conversationId =
                com.fanaujie.ripple.storage.service.utils.ConversationUtils.generateConversationId(
                        ownerId, peerId);
        storageFacade.createSingeMessageConversation(conversationId, ownerId, peerId);

        assertTrue(storageFacade.existsByConversationId(conversationId, ownerId));
    }

    @Test
    void createSingeMessageConversation_shouldAppearInGetConversations()
            throws NotFoundUserProfileException {
        long ownerId = 1003L;
        long peerId = 2003L;
        createUserProfile(ownerId, "owner3", "Owner Three", "avatar1.png");
        createUserProfile(peerId, "peer3", "Peer Three", "avatar2.png");

        String conversationId =
                com.fanaujie.ripple.storage.service.utils.ConversationUtils.generateConversationId(
                        ownerId, peerId);
        storageFacade.createSingeMessageConversation(conversationId, ownerId, peerId);

        PagedConversationResult result = storageFacade.getConversations(ownerId, null, 10);
        assertEquals(1, result.getConversations().size());
        assertEquals(conversationId, result.getConversations().get(0).getConversationId());
    }

    // ==================== readMessages Tests ====================

    @Test
    void readMessages_shouldUpdateLastReadMessageId() throws NotFoundUserProfileException {
        long ownerId = 1007L;
        long peerId = 2007L;
        createUserProfile(ownerId, "owner7", "Owner Seven", "avatar1.png");
        createUserProfile(peerId, "peer7", "Peer Seven", "avatar2.png");

        String conversationId =
                com.fanaujie.ripple.storage.service.utils.ConversationUtils.generateConversationId(
                        ownerId, peerId);
        storageFacade.createSingeMessageConversation(conversationId, ownerId, peerId);

        long messageId = 5004L;
        storageFacade.markLastReadMessageId(conversationId, ownerId, messageId);

        PagedConversationResult result = storageFacade.getConversations(ownerId, null, 10);
        assertEquals(1, result.getConversations().size());
        assertEquals(messageId, result.getConversations().get(0).getLastReadMessageId());
    }

    // ==================== saveMessage Tests ====================

    @Test
    void saveMessage_shouldSaveSingleMessage() {
        String conversationId = "conv_msg1";
        long messageId = 6001L;
        long senderId = 1009L;
        long receiverId = 2009L;
        long timestamp = System.currentTimeMillis();

        storageFacade.saveTextMessage(
                conversationId, messageId, senderId, receiverId, timestamp, "Hello from sender", null, null);

        Messages result = storageFacade.getMessages(conversationId, 0, 10);
        assertNotNull(result);
        assertEquals(1, result.getMessages().size());
        Message msg = result.getMessages().get(0);
        assertEquals(messageId, msg.getMessageId());
        assertEquals(senderId, msg.getSenderId());
        assertEquals(receiverId, msg.getReceiverId());
        assertEquals("Hello from sender", msg.getText());
    }

    @Test
    void saveMessage_shouldSaveGroupMessage() {
        String conversationId = "conv_msg2";
        long messageId = 6002L;
        long senderId = 1010L;
        long groupId = 3001L;
        long timestamp = System.currentTimeMillis();

        storageFacade.saveGroupTextMessage(
                conversationId,
                messageId,
                senderId,
                groupId,
                timestamp,
                "Group message",
                "http://example.com/image.png",
                "image.png");

        Messages result = storageFacade.getMessages(conversationId, 0, 10);
        assertNotNull(result);
        assertEquals(1, result.getMessages().size());
        Message msg = result.getMessages().get(0);
        assertEquals(messageId, msg.getMessageId());
        assertEquals(senderId, msg.getSenderId());
        assertEquals(groupId, msg.getGroupId());
        assertEquals("Group message", msg.getText());
        assertEquals("http://example.com/image.png", msg.getFileUrl());
        assertEquals("image.png", msg.getFileName());
    }

    // ==================== getConversations Tests (Pagination) ====================

    @Test
    void getConversations_shouldReturnFirstPage() throws NotFoundUserProfileException {
        long userId = 1011L;
        createUserProfile(userId, "owner11", "Owner Eleven", "avatar.png");
        createConversationsForPagination(userId, 5);

        PagedConversationResult result = storageFacade.getConversations(userId, null, 3);

        assertNotNull(result);
        assertEquals(3, result.getConversations().size());
        assertTrue(result.isHasMore());
        assertNotNull(result.getNextPageToken());
    }

    @Test
    void getConversations_shouldReturnSecondPageWithToken() throws NotFoundUserProfileException {
        long userId = 1012L;
        createUserProfile(userId, "owner12", "Owner Twelve", "avatar.png");
        createConversationsForPagination(userId, 5);

        PagedConversationResult firstPage = storageFacade.getConversations(userId, null, 3);
        PagedConversationResult secondPage =
                storageFacade.getConversations(userId, firstPage.getNextPageToken(), 3);

        assertNotNull(secondPage);
        assertEquals(2, secondPage.getConversations().size());
        assertFalse(secondPage.isHasMore());
        assertNull(secondPage.getNextPageToken());
    }

    @Test
    void getConversations_shouldReturnEmptyResult_whenNoConversations() {
        long userId = 1013L;

        PagedConversationResult result = storageFacade.getConversations(userId, null, 10);

        assertNotNull(result);
        assertTrue(result.getConversations().isEmpty());
        assertFalse(result.isHasMore());
        assertNull(result.getNextPageToken());
    }

    @Test
    void getConversations_shouldHandleExactPageSize() throws NotFoundUserProfileException {
        long userId = 1014L;
        createUserProfile(userId, "owner14", "Owner Fourteen", "avatar.png");
        createConversationsForPagination(userId, 3);

        PagedConversationResult result = storageFacade.getConversations(userId, null, 3);

        assertNotNull(result);
        assertEquals(3, result.getConversations().size());
        assertFalse(result.isHasMore());
        assertNull(result.getNextPageToken());
    }

    // ==================== getConversationChanges Tests ====================

    @Test
    void getConversationChanges_shouldReturnChangesAfterVersion() throws Exception {
        long userId = 1016L;
        long peerId = 2016L;
        createUserProfile(userId, "owner16", "Owner Sixteen", "avatar.png");
        createUserProfile(peerId, "peer16", "Peer Sixteen", "avatar2.png");

        String conversationId =
                com.fanaujie.ripple.storage.service.utils.ConversationUtils.generateConversationId(
                        userId, peerId);
        storageFacade.createSingeMessageConversation(conversationId, userId, peerId);

        Thread.sleep(10);
        UUID versionBefore = Uuids.timeBased();
        Thread.sleep(10);

        storageFacade.markLastReadMessageId(conversationId, userId, 8001L);

        String afterVersion = String.valueOf(Uuids.unixTimestamp(versionBefore));

        List<ConversationVersionChange> changes =
                storageFacade.getConversationChanges(userId, afterVersion, 10);

        assertNotNull(changes);
        assertEquals(1, changes.size());
        assertEquals(conversationId, changes.get(0).getConversationId());
        assertEquals(ConversationOperation.READ_MESSAGES.getValue(), changes.get(0).getOperation());
    }

    @Test
    void getConversationChanges_shouldReturnEmptyList_whenNoChanges() throws Exception {
        long userId = 1017L;
        long peerId = 2017L;
        createUserProfile(userId, "owner17", "Owner Seventeen", "avatar.png");
        createUserProfile(peerId, "peer17", "Peer Seventeen", "avatar2.png");

        String conversationId =
                com.fanaujie.ripple.storage.service.utils.ConversationUtils.generateConversationId(
                        userId, peerId);
        storageFacade.createSingeMessageConversation(conversationId, userId, peerId);

        Thread.sleep(10);
        UUID versionAfterAll = Uuids.timeBased();
        String afterVersion = String.valueOf(Uuids.unixTimestamp(versionAfterAll));

        List<ConversationVersionChange> changes =
                storageFacade.getConversationChanges(userId, afterVersion, 10);

        assertNotNull(changes);
        assertTrue(changes.isEmpty());
    }

    @Test
    void getConversationChanges_shouldThrowException_whenNullVersion() {
        long userId = 1019L;

        assertThrows(
                InvalidVersionException.class,
                () -> storageFacade.getConversationChanges(userId, null, 10));
    }

    @Test
    void getConversationChanges_shouldThrowException_whenEmptyVersion() {
        long userId = 1020L;

        assertThrows(
                InvalidVersionException.class,
                () -> storageFacade.getConversationChanges(userId, "", 10));
    }

    @Test
    void getConversationChanges_shouldThrowException_whenInvalidVersion() {
        long userId = 1021L;
        String invalidVersion = "not-a-timestamp";

        assertThrows(
                InvalidVersionException.class,
                () -> storageFacade.getConversationChanges(userId, invalidVersion, 10));
    }

    // ==================== getLatestConversationVersion Tests ====================

    @Test
    void getLatestConversationVersion_shouldReturnLatestVersion() throws Exception {
        long userId = 1022L;
        long peerId = 2022L;
        createUserProfile(userId, "owner22", "Owner TwentyTwo", "avatar.png");
        createUserProfile(peerId, "peer22", "Peer TwentyTwo", "avatar2.png");

        String conversationId =
                com.fanaujie.ripple.storage.service.utils.ConversationUtils.generateConversationId(
                        userId, peerId);
        storageFacade.createSingeMessageConversation(conversationId, userId, peerId);

        Thread.sleep(10);
        long messageId = 9001L;
        storageFacade.markLastReadMessageId(conversationId, userId, messageId);

        String latestVersion = storageFacade.getLatestConversationVersion(userId);

        assertNotNull(latestVersion);
        assertFalse(latestVersion.isEmpty());

        long timestamp = Long.parseLong(latestVersion);
        assertTrue(timestamp > 0);
    }

    @Test
    void getLatestConversationVersion_shouldReturnNull_whenNoVersionsExist() {
        long userId = 1023L;

        String latestVersion = storageFacade.getLatestConversationVersion(userId);

        assertNull(latestVersion);
    }

    // ==================== getMessages Tests ====================

    @Test
    void getMessages_shouldReturnMessages() {
        String conversationId = "conv_messages1";
        for (long i = 1001; i <= 1010; i++) {
            storageFacade.saveTextMessage(
                    conversationId, i, 1050L, 2050L, System.currentTimeMillis(), "Message " + i, null, null);
        }

        Messages result = storageFacade.getMessages(conversationId, 0, 5);

        assertNotNull(result);
        assertEquals(5, result.getMessages().size());
    }

    @Test
    void getMessages_shouldReturnMessagesBeforeSpecificMessageId() {
        String conversationId = "conv_messages2";
        for (long i = 1001; i <= 1005; i++) {
            storageFacade.saveTextMessage(
                    conversationId, i, 1051L, 2051L, System.currentTimeMillis(), "Message " + i, null, null);
        }

        Messages result = storageFacade.getMessages(conversationId, 1004, 3);

        assertNotNull(result);
        assertEquals(3, result.getMessages().size());
        for (Message msg : result.getMessages()) {
            assertTrue(msg.getMessageId() < 1004);
        }
    }

    @Test
    void getMessages_shouldReturnEmptyWhenConversationHasNoMessages() {
        String conversationId = "conv_messages_empty";

        Messages result = storageFacade.getMessages(conversationId, 0, 10);

        assertNotNull(result);
        assertTrue(result.getMessages().isEmpty());
    }

    @Test
    void getMessages_shouldMaintainMessageMetadata() {
        String conversationId = "conv_messages5";
        long messageId = 2001L;
        long senderId = 1054L;
        long receiverId = 2054L;
        long timestamp = System.currentTimeMillis();

        storageFacade.saveTextMessage(
                conversationId,
                messageId,
                senderId,
                receiverId,
                timestamp,
                "Test message",
                "http://example.com/file.jpg",
                "file.jpg");

        Messages result = storageFacade.getMessages(conversationId, 0, 10);

        assertNotNull(result);
        assertEquals(1, result.getMessages().size());
        Message message = result.getMessages().get(0);
        assertEquals(conversationId, message.getConversationId());
        assertEquals(messageId, message.getMessageId());
        assertEquals(senderId, message.getSenderId());
        assertEquals(receiverId, message.getReceiverId());
        assertEquals("Test message", message.getText());
        assertEquals("http://example.com/file.jpg", message.getFileUrl());
        assertEquals("file.jpg", message.getFileName());
    }

    // ==================== Helper Methods ====================

    private void createConversationsForPagination(long userId, int count)
            throws NotFoundUserProfileException {
        for (int i = 0; i < count; i++) {
            long peerId = 2000L + i;
            createUserProfile(peerId, "peer_page_" + i, "Peer " + i, "avatar.png");
            String conversationId =
                    com.fanaujie.ripple.storage.service.utils.ConversationUtils.generateConversationId(
                            userId, peerId);
            storageFacade.createSingeMessageConversation(conversationId, userId, peerId);
        }
    }
}
