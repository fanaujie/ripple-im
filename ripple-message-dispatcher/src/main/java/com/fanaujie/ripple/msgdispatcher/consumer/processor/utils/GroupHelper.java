package com.fanaujie.ripple.msgdispatcher.consumer.processor.utils;

import com.fanaujie.ripple.communication.msgqueue.GenericProducer;
import com.fanaujie.ripple.protobuf.msgapiserver.SendGroupCommandReq;
import com.fanaujie.ripple.protobuf.storageupdater.GroupMemberBatchInsertData;
import com.fanaujie.ripple.protobuf.storageupdater.StorageUpdatePayload;
import com.fanaujie.ripple.storage.model.UserProfile;
import com.fanaujie.ripple.storage.service.ConversationStateFacade;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import com.fanaujie.ripple.storage.service.utils.ConversationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.fanaujie.ripple.storage.model.GroupCommandType.GROUP_COMMAND_TYPE_MEMBER_JOIN;

public class GroupHelper {
    private static final Logger logger = LoggerFactory.getLogger(GroupHelper.class);
    private static final int MAX_BATCH_SIZE = 5;
    private final GenericProducer<String, StorageUpdatePayload> storageUpdateProducer;
    private final String storageUpdateTopic;
    private final RippleStorageFacade storageFacade;
    private final ConversationStateFacade conversationStorage;

    public GroupHelper(
            GenericProducer<String, StorageUpdatePayload> storageUpdateProducer,
            String storageUpdateTopic,
            RippleStorageFacade storageFacade,
            ConversationStateFacade conversationStorage) {
        this.storageUpdateProducer = storageUpdateProducer;
        this.storageUpdateTopic = storageUpdateTopic;
        this.storageFacade = storageFacade;
        this.conversationStorage = conversationStorage;
    }

    public void sendBatchedStorageUpdates(
            long groupId,
            String groupName,
            String groupAvatar,
            List<UserProfile> newMembers,
            long creatorUserId,
            long sendTimestamp,
            long messageId,
            long senderId) {
        int totalBatches = (int) Math.ceil(newMembers.size() / (float) MAX_BATCH_SIZE);

        for (int i = 0; i < totalBatches; i++) {
            int start = i * MAX_BATCH_SIZE;
            int end = Math.min((i + 1) * MAX_BATCH_SIZE, newMembers.size());
            List<Long> batchMembers =
                    newMembers.subList(start, end).stream().map(UserProfile::getUserId).toList();
            GroupMemberBatchInsertData batchData =
                    GroupMemberBatchInsertData.newBuilder()
                            .setGroupId(groupId)
                            .setGroupName(groupName)
                            .setGroupAvatar(groupAvatar)
                            .addAllMemberIds(batchMembers)
                            .setBatchIndex(i)
                            .setTotalBatches(totalBatches)
                            .setCreatorUserId(creatorUserId)
                            .setSendTimestamp(sendTimestamp)
                            .setMessageId(messageId)
                            .setSenderId(senderId)
                            .build();

            StorageUpdatePayload payload =
                    StorageUpdatePayload.newBuilder()
                            .setGroupMemberBatchInsertData(batchData)
                            .build();
            this.storageUpdateProducer.send(
                    this.storageUpdateTopic, String.valueOf(groupId), payload);
        }
    }

    public void writeJoinGroupCommandMessage(
            SendGroupCommandReq sendGroupCommandReq, long groupId, List<UserProfile> newMembers) {

        String memberNames =
                newMembers.stream().map(UserProfile::getNickName).collect(Collectors.joining("、"));
        String commandText = memberNames + " joined the group";
        String conversationId = ConversationUtils.generateGroupConversationId(groupId);
        this.storageFacade.saveGroupCommandMessage(
                conversationId,
                sendGroupCommandReq.getMessageId(),
                sendGroupCommandReq.getSenderId(),
                groupId,
                sendGroupCommandReq.getSendTimestamp(),
                GROUP_COMMAND_TYPE_MEMBER_JOIN.getValue(),
                commandText);

        // Update last message only (group commands don't increment unread count)
        // Uses pipeline for efficiency
        conversationStorage.batchUpdateConversation(
                Collections.emptyList(), // No recipients for unread increment
                conversationId,
                commandText,
                sendGroupCommandReq.getSendTimestamp(),
                String.valueOf(sendGroupCommandReq.getMessageId()),
                false); // incrementUnread = false for group commands
    }
}
