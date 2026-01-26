package com.fanaujie.ripple.storage.repository;

import com.fanaujie.ripple.storage.exception.InvalidVersionException;
import com.fanaujie.ripple.storage.exception.NotFoundUserProfileException;
import com.fanaujie.ripple.storage.model.*;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public abstract class AbstractConversationRepositoryTest {

    protected abstract RippleStorageFacade getStorageFacade();

    protected void createUserProfile(long userId, String account, String nickName, String avatar) {
        User user = new User(userId, account, "password", User.DEFAULT_ROLE_USER, (byte) 0);
        getStorageFacade().insertUser(user, nickName, avatar);
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
        getStorageFacade().createSingeMessageConversation(conversationId, ownerId, peerId, System.currentTimeMillis());

        boolean exists = getStorageFacade().existsByConversationId(conversationId, ownerId);
        assertTrue(exists);
    }

    @Test
    void existsById_shouldReturnFalse_whenConversationDoesNotExist() {
        long ownerId = 1001L;
        String conversationId = "conv_nonexistent";

        boolean exists = getStorageFacade().existsByConversationId(conversationId, ownerId);
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
        getStorageFacade().createSingeMessageConversation(conversationId, ownerId, peerId, System.currentTimeMillis());

        assertTrue(getStorageFacade().existsByConversationId(conversationId, ownerId));
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
        getStorageFacade().createSingeMessageConversation(conversationId, ownerId, peerId, System.currentTimeMillis());

        PagedConversationResult result = getStorageFacade().getConversations(ownerId, null, 10);
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
        getStorageFacade().createSingeMessageConversation(conversationId, ownerId, peerId, System.currentTimeMillis());

        long messageId = 5004L;
        getStorageFacade().markLastReadMessageId(conversationId, ownerId, messageId, System.currentTimeMillis());

        PagedConversationResult result = getStorageFacade().getConversations(ownerId, null, 10);
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

        getStorageFacade().saveTextMessage(
                conversationId, messageId, senderId, receiverId, timestamp, "Hello from sender", null, null);

        Messages result = getStorageFacade().getMessages(conversationId, 0, 10);
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

        getStorageFacade().saveGroupTextMessage(
                conversationId,
                messageId,
                senderId,
                groupId,
                timestamp,
                "Group message",
                "http://example.com/image.png",
                "image.png");

        Messages result = getStorageFacade().getMessages(conversationId, 0, 10);
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

        PagedConversationResult result = getStorageFacade().getConversations(userId, null, 3);

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

        PagedConversationResult firstPage = getStorageFacade().getConversations(userId, null, 3);
        PagedConversationResult secondPage =
                getStorageFacade().getConversations(userId, firstPage.getNextPageToken(), 3);

        assertNotNull(secondPage);
        assertEquals(2, secondPage.getConversations().size());
        assertFalse(secondPage.isHasMore());
        assertNull(secondPage.getNextPageToken());
    }

    @Test
    void getConversations_shouldReturnEmptyResult_whenNoConversations() {
        long userId = 1013L;

        PagedConversationResult result = getStorageFacade().getConversations(userId, null, 10);

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

        PagedConversationResult result = getStorageFacade().getConversations(userId, null, 3);

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
        getStorageFacade().createSingeMessageConversation(conversationId, userId, peerId, System.currentTimeMillis());

        Thread.sleep(10);
        long versionBefore = System.currentTimeMillis();
        Thread.sleep(10);

        getStorageFacade().markLastReadMessageId(conversationId, userId, 8001L, System.currentTimeMillis());

        String afterVersion = String.valueOf(versionBefore);

        List<ConversationVersionChange> changes =
                getStorageFacade().getConversationChanges(userId, afterVersion, 10);

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
        getStorageFacade().createSingeMessageConversation(conversationId, userId, peerId, System.currentTimeMillis());

        Thread.sleep(10);
        long versionAfterAll = System.currentTimeMillis();
        String afterVersion = String.valueOf(versionAfterAll);

        List<ConversationVersionChange> changes =
                getStorageFacade().getConversationChanges(userId, afterVersion, 10);

        assertNotNull(changes);
        assertTrue(changes.isEmpty());
    }

    @Test
    void getConversationChanges_shouldThrowException_whenNullVersion() {
        long userId = 1019L;

        assertThrows(
                InvalidVersionException.class,
                () -> getStorageFacade().getConversationChanges(userId, null, 10));
    }

    @Test
    void getConversationChanges_shouldThrowException_whenEmptyVersion() {
        long userId = 1020L;

        assertThrows(
                InvalidVersionException.class,
                () -> getStorageFacade().getConversationChanges(userId, "", 10));
    }

    @Test
    void getConversationChanges_shouldThrowException_whenInvalidVersion() {
        long userId = 1021L;
        String invalidVersion = "not-a-timestamp";

        assertThrows(
                InvalidVersionException.class,
                () -> getStorageFacade().getConversationChanges(userId, invalidVersion, 10));
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
        getStorageFacade().createSingeMessageConversation(conversationId, userId, peerId, System.currentTimeMillis());

        Thread.sleep(10);
        long messageId = 9001L;
        getStorageFacade().markLastReadMessageId(conversationId, userId, messageId, System.currentTimeMillis());

        String latestVersion = getStorageFacade().getLatestConversationVersion(userId);

        assertNotNull(latestVersion);
        assertFalse(latestVersion.isEmpty());

        long timestamp = Long.parseLong(latestVersion);
        assertTrue(timestamp > 0);
    }

    @Test
    void getLatestConversationVersion_shouldReturnNull_whenNoVersionsExist() {
        long userId = 1023L;

        String latestVersion = getStorageFacade().getLatestConversationVersion(userId);

        assertNull(latestVersion);
    }

    // ==================== getMessages Tests ====================

    @Test
    void getMessages_shouldReturnMessages() {
        String conversationId = "conv_messages1";
        for (long i = 1001; i <= 1010; i++) {
            getStorageFacade().saveTextMessage(
                    conversationId, i, 1050L, 2050L, System.currentTimeMillis(), "Message " + i, null, null);
        }

        Messages result = getStorageFacade().getMessages(conversationId, 0, 5);

        assertNotNull(result);
        assertEquals(5, result.getMessages().size());
    }

    @Test
    void getMessages_shouldReturnMessagesBeforeSpecificMessageId() {
        String conversationId = "conv_messages2";
        for (long i = 1001; i <= 1005; i++) {
            getStorageFacade().saveTextMessage(
                    conversationId, i, 1051L, 2051L, System.currentTimeMillis(), "Message " + i, null, null);
        }

        Messages result = getStorageFacade().getMessages(conversationId, 1004, 3);

        assertNotNull(result);
        assertEquals(3, result.getMessages().size());
        for (Message msg : result.getMessages()) {
            assertTrue(msg.getMessageId() < 1004);
        }
    }

    @Test
    void getMessages_shouldReturnEmptyWhenConversationHasNoMessages() {
        String conversationId = "conv_messages_empty";

        Messages result = getStorageFacade().getMessages(conversationId, 0, 10);

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

        getStorageFacade().saveTextMessage(
                conversationId,
                messageId,
                senderId,
                receiverId,
                timestamp,
                "Test message",
                "http://example.com/file.jpg",
                "file.jpg");

        Messages result = getStorageFacade().getMessages(conversationId, 0, 10);

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

    // ==================== getMessagesAfter Tests ====================

    @Test
    void getMessagesAfter_shouldReturnMessagesAfterSpecificMessageId() {
        String conversationId = "conv_messages_after1";
        for (long i = 1001; i <= 1010; i++) {
            getStorageFacade().saveTextMessage(
                    conversationId, i, 1060L, 2060L, System.currentTimeMillis(), "Message " + i, null, null);
        }

        Messages result = getStorageFacade().getMessagesAfter(conversationId, 1005, 10);

        assertNotNull(result);
        assertEquals(5, result.getMessages().size());
        for (Message msg : result.getMessages()) {
            assertTrue(msg.getMessageId() > 1005);
        }
    }

    @Test
    void getMessagesAfter_shouldReturnMessagesInAscendingOrder() {
        String conversationId = "conv_messages_after2";
        for (long i = 1001; i <= 1005; i++) {
            getStorageFacade().saveTextMessage(
                    conversationId, i, 1061L, 2061L, System.currentTimeMillis(), "Message " + i, null, null);
        }

        Messages result = getStorageFacade().getMessagesAfter(conversationId, 1001, 10);

        assertNotNull(result);
        assertEquals(4, result.getMessages().size());
        long previousId = 0;
        for (Message msg : result.getMessages()) {
            assertTrue(msg.getMessageId() > previousId);
            previousId = msg.getMessageId();
        }
    }

    @Test
    void getMessagesAfter_shouldRespectPageSize() {
        String conversationId = "conv_messages_after3";
        for (long i = 1001; i <= 1010; i++) {
            getStorageFacade().saveTextMessage(
                    conversationId, i, 1062L, 2062L, System.currentTimeMillis(), "Message " + i, null, null);
        }

        Messages result = getStorageFacade().getMessagesAfter(conversationId, 1001, 3);

        assertNotNull(result);
        assertEquals(3, result.getMessages().size());
        assertEquals(1002, result.getMessages().get(0).getMessageId());
        assertEquals(1003, result.getMessages().get(1).getMessageId());
        assertEquals(1004, result.getMessages().get(2).getMessageId());
    }

    @Test
    void getMessagesAfter_shouldReturnEmptyWhenNoMessagesAfterGivenId() {
        String conversationId = "conv_messages_after4";
        for (long i = 1001; i <= 1005; i++) {
            getStorageFacade().saveTextMessage(
                    conversationId, i, 1063L, 2063L, System.currentTimeMillis(), "Message " + i, null, null);
        }

        Messages result = getStorageFacade().getMessagesAfter(conversationId, 1005, 10);

        assertNotNull(result);
        assertTrue(result.getMessages().isEmpty());
    }

    @Test
    void getMessagesAfter_shouldReturnEmptyWhenConversationHasNoMessages() {
        String conversationId = "conv_messages_after_empty";

        Messages result = getStorageFacade().getMessagesAfter(conversationId, 0, 10);

        assertNotNull(result);
        assertTrue(result.getMessages().isEmpty());
    }

    // ==================== calculateUnreadCount Tests ====================

    @Test
    void calculateUnreadCount_shouldReturnCorrectCount() throws NotFoundUserProfileException {
        long ownerId = 3001L;
        long peerId = 4001L;
        createUserProfile(ownerId, "owner_unread", "Owner Unread", "avatar.png");
        createUserProfile(peerId, "peer_unread", "Peer Unread", "avatar.png");

        String conversationId = com.fanaujie.ripple.storage.service.utils.ConversationUtils.generateConversationId(ownerId, peerId);
        getStorageFacade().createSingeMessageConversation(conversationId, ownerId, peerId, System.currentTimeMillis());

        // Assume last read is 100
        getStorageFacade().markLastReadMessageId(conversationId, ownerId, 100L, System.currentTimeMillis());

        // Add messages
        // Message 101 from peer (unread)
        getStorageFacade().saveTextMessage(conversationId, 101L, peerId, ownerId, System.currentTimeMillis(), "Msg 101", null, null);
        // Message 102 from owner (should not count as unread)
        getStorageFacade().saveTextMessage(conversationId, 102L, ownerId, peerId, System.currentTimeMillis(), "Msg 102", null, null);
        // Message 103 from peer (unread)
        getStorageFacade().saveTextMessage(conversationId, 103L, peerId, ownerId, System.currentTimeMillis(), "Msg 103", null, null);

        int unreadCount = getStorageFacade().calculateUnreadCount(ownerId, conversationId);

        assertEquals(2, unreadCount);
    }

    @Test
    void calculateUnreadCount_shouldReturnZero_whenNoUnreadMessages() throws NotFoundUserProfileException {
        long ownerId = 3002L;
        long peerId = 4002L;
        createUserProfile(ownerId, "owner_read", "Owner Read", "avatar.png");
        createUserProfile(peerId, "peer_read", "Peer Read", "avatar.png");

        String conversationId = com.fanaujie.ripple.storage.service.utils.ConversationUtils.generateConversationId(ownerId, peerId);
        getStorageFacade().createSingeMessageConversation(conversationId, ownerId, peerId, System.currentTimeMillis());

        getStorageFacade().markLastReadMessageId(conversationId, ownerId, 200L, System.currentTimeMillis());

        // Message 101 (read)
        getStorageFacade().saveTextMessage(conversationId, 101L, peerId, ownerId, System.currentTimeMillis(), "Msg 101", null, null);

        int unreadCount = getStorageFacade().calculateUnreadCount(ownerId, conversationId);

        assertEquals(0, unreadCount);
    }

    @Test
    void calculateUnreadCount_shouldReturnZero_whenConversationDoesNotExist() {
        long ownerId = 3003L;
        String conversationId = "non_existent_conv";

        int unreadCount = getStorageFacade().calculateUnreadCount(ownerId, conversationId);

        assertEquals(0, unreadCount);
    }

    // ==================== updateConversationBotSessionId Tests ====================

    @Test
    void updateConversationBotSessionId_shouldRecordVersionChange() throws Exception {
        long userId = 4001L;
        long peerId = 5001L;
        createUserProfile(userId, "owner_bot1", "Owner Bot One", "avatar.png");
        createUserProfile(peerId, "peer_bot1", "Peer Bot One", "avatar2.png");

        String conversationId =
                com.fanaujie.ripple.storage.service.utils.ConversationUtils.generateConversationId(
                        userId, peerId);
        getStorageFacade().createSingeMessageConversation(conversationId, userId, peerId, System.currentTimeMillis());

        Thread.sleep(10);
        long versionBefore = System.currentTimeMillis();
        Thread.sleep(10);

        String botSessionId = "test-session-123";
        getStorageFacade().updateConversationBotSessionId(userId, conversationId, botSessionId, System.currentTimeMillis());

        String afterVersion = String.valueOf(versionBefore);

        List<ConversationVersionChange> changes =
                getStorageFacade().getConversationChanges(userId, afterVersion, 10);

        assertNotNull(changes);
        assertEquals(1, changes.size());
        assertEquals(conversationId, changes.get(0).getConversationId());
        assertEquals(ConversationOperation.UPDATE_BOT_SESSION_ID.getValue(), changes.get(0).getOperation());
        assertEquals(botSessionId, changes.get(0).getBotSessionId());
    }

    @Test
    void updateConversationBotSessionId_shouldUpdateBotSessionIdInConversation() throws Exception {
        long userId = 4002L;
        long peerId = 5002L;
        createUserProfile(userId, "owner_bot2", "Owner Bot Two", "avatar.png");
        createUserProfile(peerId, "peer_bot2", "Peer Bot Two", "avatar2.png");

        String conversationId =
                com.fanaujie.ripple.storage.service.utils.ConversationUtils.generateConversationId(
                        userId, peerId);
        getStorageFacade().createSingeMessageConversation(conversationId, userId, peerId, System.currentTimeMillis());

        String botSessionId = "test-session-456";
        getStorageFacade().updateConversationBotSessionId(userId, conversationId, botSessionId, System.currentTimeMillis());

        Conversation conversation = getStorageFacade().getConversation(userId, conversationId);

        assertNotNull(conversation);
        assertEquals(botSessionId, conversation.getBotSessionId());
    }

    // ==================== Helper Methods ====================

    protected void createConversationsForPagination(long userId, int count)
            throws NotFoundUserProfileException {
        for (int i = 0; i < count; i++) {
            long peerId = 2000L + i;
            createUserProfile(peerId, "peer_page_" + i, "Peer " + i, "avatar.png");
            String conversationId =
                    com.fanaujie.ripple.storage.service.utils.ConversationUtils.generateConversationId(
                            userId, peerId);
            getStorageFacade().createSingeMessageConversation(conversationId, userId, peerId, System.currentTimeMillis());
        }
    }
}
