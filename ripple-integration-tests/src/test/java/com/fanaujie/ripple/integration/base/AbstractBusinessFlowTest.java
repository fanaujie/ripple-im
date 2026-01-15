package com.fanaujie.ripple.integration.base;

import com.fanaujie.ripple.integration.mock.MockConversationSummaryStorage;
import com.fanaujie.ripple.integration.mock.MockProducer;
import com.fanaujie.ripple.integration.mock.MockUserProfileStorage;
import com.fanaujie.ripple.msgapiserver.processor.RelationEventProcessor;
import com.fanaujie.ripple.msgapiserver.processor.SingleMessageContentProcessor;
import com.fanaujie.ripple.msgdispatcher.consumer.processor.RelationUpdateEventPayloadProcessor;
import com.fanaujie.ripple.msgdispatcher.consumer.processor.SingleMessagePayloadProcessor;
import com.fanaujie.ripple.protobuf.msgapiserver.*;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;
import com.fanaujie.ripple.protobuf.push.PushMessage;
import com.fanaujie.ripple.protobuf.storageupdater.StorageUpdatePayload;
import com.fanaujie.ripple.storage.model.User;
import com.fanaujie.ripple.storage.model.UserProfile;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import com.fanaujie.ripple.storage.service.utils.ConversationUtils;
import com.fanaujie.ripple.storageupdater.consumer.processor.FriendStorageUpdatePayloadProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import java.util.concurrent.Executors;

public abstract class AbstractBusinessFlowTest {

    protected static final String TOPIC_MESSAGES = "ripple-messages";
    protected static final String TOPIC_PUSH = "ripple-push-notifications";
    protected static final String TOPIC_STORAGE_UPDATES = "ripple-storage-updates";

    protected RippleStorageFacade storageFacade;

    // Mock producers to capture messages
    protected MockProducer<String, MessagePayload> messagePayloadProducer;
    protected MockProducer<String, PushMessage> pushMessageProducer;
    protected MockProducer<String, StorageUpdatePayload> storageUpdateProducer;

    // Processors - Relation
    protected RelationEventProcessor relationEventProcessor;
    protected RelationUpdateEventPayloadProcessor relationUpdateEventPayloadProcessor;
    protected FriendStorageUpdatePayloadProcessor friendStorageUpdatePayloadProcessor;

    // Processors - Message
    protected SingleMessageContentProcessor singleMessageContentProcessor;
    protected SingleMessagePayloadProcessor singleMessagePayloadProcessor;

    // Mock services
    protected MockConversationSummaryStorage conversationSummaryStorage;
    protected MockUserProfileStorage userProfileStorage;

    @BeforeEach
    void setUp() throws Exception {
        // Initialize storage (implemented by subclasses)
        initializeStorage();

        // Initialize mock producers
        this.messagePayloadProducer = new MockProducer<>();
        this.pushMessageProducer = new MockProducer<>();
        this.storageUpdateProducer = new MockProducer<>();

        // Initialize processors
        initializeProcessors();
    }

    /** Subclasses must initialize storageFacade and any required database connection. */
    protected abstract void initializeStorage() throws Exception;

    private void initializeProcessors() {
        // Initialize mock services
        this.conversationSummaryStorage = new MockConversationSummaryStorage();
        this.userProfileStorage = new MockUserProfileStorage(storageFacade);

        // API Server processor - produces to ripple-messages topic
        this.relationEventProcessor =
                new RelationEventProcessor(
                        TOPIC_MESSAGES,
                        messagePayloadProducer,
                        Executors.newSingleThreadExecutor(),
                        storageFacade);

        // Message Dispatcher processor - consumes from ripple-messages, produces to push and
        // storage-updates
        this.relationUpdateEventPayloadProcessor =
                new RelationUpdateEventPayloadProcessor(
                        storageFacade,
                        storageUpdateProducer,
                        TOPIC_STORAGE_UPDATES,
                        pushMessageProducer,
                        TOPIC_PUSH);

        // Async Storage Updater processor - consumes from ripple-storage-updates
        this.friendStorageUpdatePayloadProcessor =
                new FriendStorageUpdatePayloadProcessor(storageFacade);

        // Message processors
        this.singleMessageContentProcessor =
                new SingleMessageContentProcessor(
                        TOPIC_MESSAGES,
                        storageFacade,
                        messagePayloadProducer,
                        Executors.newSingleThreadExecutor());

        this.singleMessagePayloadProcessor =
                new SingleMessagePayloadProcessor(
                        storageFacade, conversationSummaryStorage, pushMessageProducer, TOPIC_PUSH);
    }

    @AfterEach
    void tearDown() throws Exception {
        // Clean up storage (implemented by subclasses)
        cleanupStorage();

        // Clear mock producers
        if (messagePayloadProducer != null) messagePayloadProducer.clear();
        if (pushMessageProducer != null) pushMessageProducer.clear();
        if (storageUpdateProducer != null) storageUpdateProducer.clear();

        // Clear mock services
        if (conversationSummaryStorage != null) conversationSummaryStorage.clear();
        if (userProfileStorage != null) userProfileStorage.clear();
    }

    /** Subclasses must perform database cleanup. */
    protected abstract void cleanupStorage() throws Exception;

    // ==================== Test Data Setup Methods ====================

    /** Creates a test user with profile. */
    protected void createUser(long userId, String account, String nickName, String avatar) {
        User user = new User(userId, account, "password123", User.DEFAULT_ROLE_USER, (byte) 0);
        storageFacade.insertUser(user, nickName, avatar);
    }

    /** Creates a test user with default avatar. */
    protected void createUser(long userId, String account, String nickName) {
        createUser(userId, account, nickName, "default-avatar.png");
    }

    // ==================== Business Flow Execution Methods ====================

    /**
     * Executes the complete add friend flow through all processors.
     *
     * <p>Flow: API Server -> Message Dispatcher -> (optional) Async Storage Updater
     */
    protected void executeAddFriendFlow(long userId, long targetUserId) throws Exception {
        // Step 1: API Server - Create and send event
        SendEventReq request = createAddFriendRequest(userId, targetUserId);
        SendEventResp response = relationEventProcessor.handle(request);

        // Step 2: Message Dispatcher - Process the message
        processMessagePayloads();

        // Step 3: Async Storage Updater - Process storage updates (if any)
        processStorageUpdates();
    }

    /** Executes the remove friend flow. */
    protected void executeRemoveFriendFlow(long userId, long targetUserId) throws Exception {
        SendEventReq request = createRemoveFriendRequest(userId, targetUserId);
        relationEventProcessor.handle(request);
        processMessagePayloads();
        processStorageUpdates();
    }

    /** Executes the block friend flow. */
    protected void executeBlockFriendFlow(long userId, long targetUserId) throws Exception {
        SendEventReq request = createBlockFriendRequest(userId, targetUserId);
        relationEventProcessor.handle(request);
        processMessagePayloads();
        processStorageUpdates();
    }

    /** Executes the block stranger flow. */
    protected void executeBlockStrangerFlow(long userId, long targetUserId) throws Exception {
        SendEventReq request = createBlockStrangerRequest(userId, targetUserId);
        relationEventProcessor.handle(request);
        processMessagePayloads();
        processStorageUpdates();
    }

    /** Executes the unblock user flow. */
    protected void executeUnblockUserFlow(long userId, long targetUserId) throws Exception {
        SendEventReq request = createUnblockUserRequest(userId, targetUserId);
        relationEventProcessor.handle(request);
        processMessagePayloads();
        processStorageUpdates();
    }

    /** Executes the update friend remark name flow. */
    protected void executeUpdateFriendRemarkNameFlow(
            long userId, long targetUserId, String remarkName) throws Exception {
        SendEventReq request =
                createUpdateFriendRemarkNameRequest(userId, targetUserId, remarkName);
        relationEventProcessor.handle(request);
        processMessagePayloads();
        processStorageUpdates();
    }

    /** Executes the hide blocked user flow. */
    protected void executeHideBlockedUserFlow(long userId, long targetUserId) throws Exception {
        SendEventReq request = createHideBlockedUserRequest(userId, targetUserId);
        relationEventProcessor.handle(request);
        processMessagePayloads();
        processStorageUpdates();
    }

    // ==================== Message Flow Execution Methods ====================

    /** Executes the complete single message flow through all processors. */
    protected void executeSendMessageFlow(
            long senderId, long receiverId, String conversationId, long messageId, String text)
            throws Exception {
        SendMessageReq request =
                createSendMessageRequest(senderId, receiverId, conversationId, messageId, text);
        singleMessageContentProcessor.handle(request);
        processMessagePayloads();
    }

    /** Executes the complete group message flow through all processors. */
    protected void executeSendGroupMessageFlow(
            long senderId, long groupId, String conversationId, long messageId, String text)
            throws Exception {
        SendMessageReq request =
                createSendGroupMessageRequest(senderId, groupId, conversationId, messageId, text);
        singleMessageContentProcessor.handle(request);
        processMessagePayloads();
    }

    // ==================== Group Operations (Direct Storage) ====================

    /**
     * Creates a group with the specified members. Uses storage facade directly since the processor
     * has Redis dependencies.
     */
    protected void createGroup(long groupId, List<Long> memberIds, long version) throws Exception {
        List<UserProfile> members =
                memberIds.stream()
                        .map(
                                id -> {
                                    try {
                                        return storageFacade.getUserProfile(id);
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                })
                        .toList();
        storageFacade.createGroup(groupId, members, version);
    }

    /** Adds members to an existing group. */
    protected void inviteGroupMembers(long groupId, List<Long> memberIds, long version)
            throws Exception {
        List<UserProfile> members =
                memberIds.stream()
                        .map(
                                id -> {
                                    try {
                                        return storageFacade.getUserProfile(id);
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                })
                        .toList();
        storageFacade.createGroupMembersProfile(groupId, members, version);
    }

    /** Removes a member from a group. */
    protected void quitGroup(long groupId, long userId, long version) throws Exception {
        storageFacade.removeGroupMember(groupId, userId, version);
    }

    // ==================== Message Processing Methods ====================

    /** Processes all captured message payloads through the dispatcher. */
    protected void processMessagePayloads() throws Exception {
        var messages = messagePayloadProducer.getCapturedMessages();
        messagePayloadProducer.clear();

        for (var msg : messages) {
            MessagePayload payload = msg.value();
            if (payload.hasEventData()) {
                relationUpdateEventPayloadProcessor.handle(payload.getEventData());
            }
            if (payload.hasMessageData()) {
                singleMessagePayloadProcessor.handle(payload.getMessageData());
            }
        }
    }

    /** Processes all captured storage update payloads. */
    protected void processStorageUpdates() throws Exception {
        var updates = storageUpdateProducer.getCapturedMessages();
        storageUpdateProducer.clear();

        for (var msg : updates) {
            StorageUpdatePayload payload = msg.value();
            if (payload.hasFriendStorageUpdateData()) {
                friendStorageUpdatePayloadProcessor.handle(payload);
            }
        }
    }

    // ==================== Request Builder Methods ====================

    protected SendEventReq createAddFriendRequest(long userId, long targetUserId) {
        RelationEvent event =
                RelationEvent.newBuilder()
                        .setEventType(RelationEvent.EventType.ADD_FRIEND)
                        .setUserId(userId)
                        .setTargetUserId(targetUserId)
                        .build();
        return SendEventReq.newBuilder()
                .setRelationEvent(event)
                .setSendTimestamp(System.currentTimeMillis())
                .build();
    }

    protected SendEventReq createRemoveFriendRequest(long userId, long targetUserId) {
        RelationEvent event =
                RelationEvent.newBuilder()
                        .setEventType(RelationEvent.EventType.REMOVE_FRIEND)
                        .setUserId(userId)
                        .setTargetUserId(targetUserId)
                        .build();
        return SendEventReq.newBuilder()
                .setRelationEvent(event)
                .setSendTimestamp(System.currentTimeMillis())
                .build();
    }

    protected SendEventReq createBlockFriendRequest(long userId, long targetUserId) {
        RelationEvent event =
                RelationEvent.newBuilder()
                        .setEventType(RelationEvent.EventType.BLOCK_FRIEND)
                        .setUserId(userId)
                        .setTargetUserId(targetUserId)
                        .build();
        return SendEventReq.newBuilder()
                .setRelationEvent(event)
                .setSendTimestamp(System.currentTimeMillis())
                .build();
    }

    protected SendEventReq createBlockStrangerRequest(long userId, long targetUserId) {
        RelationEvent event =
                RelationEvent.newBuilder()
                        .setEventType(RelationEvent.EventType.BLOCK_STRANGER)
                        .setUserId(userId)
                        .setTargetUserId(targetUserId)
                        .build();
        return SendEventReq.newBuilder()
                .setRelationEvent(event)
                .setSendTimestamp(System.currentTimeMillis())
                .build();
    }

    protected SendEventReq createUnblockUserRequest(long userId, long targetUserId) {
        RelationEvent event =
                RelationEvent.newBuilder()
                        .setEventType(RelationEvent.EventType.UNBLOCK_USER)
                        .setUserId(userId)
                        .setTargetUserId(targetUserId)
                        .build();
        return SendEventReq.newBuilder()
                .setRelationEvent(event)
                .setSendTimestamp(System.currentTimeMillis())
                .build();
    }

    protected SendEventReq createUpdateFriendRemarkNameRequest(
            long userId, long targetUserId, String remarkName) {
        RelationEvent event =
                RelationEvent.newBuilder()
                        .setEventType(RelationEvent.EventType.UPDATE_FRIEND_REMARK_NAME)
                        .setUserId(userId)
                        .setTargetUserId(targetUserId)
                        .setTargetUserRemarkName(remarkName)
                        .build();
        return SendEventReq.newBuilder()
                .setRelationEvent(event)
                .setSendTimestamp(System.currentTimeMillis())
                .build();
    }

    protected SendEventReq createHideBlockedUserRequest(long userId, long targetUserId) {
        RelationEvent event =
                RelationEvent.newBuilder()
                        .setEventType(RelationEvent.EventType.HIDE_BLOCKED_USER)
                        .setUserId(userId)
                        .setTargetUserId(targetUserId)
                        .build();
        return SendEventReq.newBuilder()
                .setRelationEvent(event)
                .setSendTimestamp(System.currentTimeMillis())
                .build();
    }

    // ==================== Message Request Builder Methods ====================

    protected SendMessageReq createSendMessageRequest(
            long senderId, long receiverId, String conversationId, long messageId, String text) {
        SingleMessageContent content = SingleMessageContent.newBuilder().setText(text).build();
        return SendMessageReq.newBuilder()
                .setSenderId(senderId)
                .setReceiverId(receiverId)
                .setConversationId(conversationId)
                .setMessageId(messageId)
                .setSendTimestamp(System.currentTimeMillis())
                .setSingleMessageContent(content)
                .build();
    }

    protected SendMessageReq createSendGroupMessageRequest(
            long senderId, long groupId, String conversationId, long messageId, String text) {
        SingleMessageContent content = SingleMessageContent.newBuilder().setText(text).build();
        return SendMessageReq.newBuilder()
                .setSenderId(senderId)
                .setGroupId(groupId)
                .setConversationId(conversationId)
                .setMessageId(messageId)
                .setSendTimestamp(System.currentTimeMillis())
                .setSingleMessageContent(content)
                .build();
    }

    // ==================== Helper Methods ====================

    /** Generates a conversation ID for a single chat between two users. */
    protected String generateSingleConversationId(long userId1, long userId2) {
        return ConversationUtils.generateConversationId(userId1, userId2);
    }

    /** Generates a conversation ID for a group chat. */
    protected String generateGroupConversationId(long groupId) {
        return ConversationUtils.generateGroupConversationId(groupId);
    }
}