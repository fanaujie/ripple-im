package com.fanaujie.ripple.msgdispatcher.consumer.processor.utils;

import com.fanaujie.ripple.communication.msgqueue.GenericProducer;
import com.fanaujie.ripple.protobuf.msgapiserver.SendGroupCommandReq;
import com.fanaujie.ripple.protobuf.storageupdater.GroupInfoBatchUpdateData;
import com.fanaujie.ripple.protobuf.storageupdater.GroupMemberBatchInsertData;
import com.fanaujie.ripple.protobuf.storageupdater.StorageUpdatePayload;
import com.fanaujie.ripple.storage.model.UserProfile;
import com.fanaujie.ripple.cache.service.ConversationSummaryStorage;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import com.fanaujie.ripple.storage.service.utils.ConversationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

import static com.fanaujie.ripple.storage.model.GroupCommandType.GROUP_COMMAND_TYPE_MEMBER_JOIN;

public class GroupHelper {
    private static final Logger logger = LoggerFactory.getLogger(GroupHelper.class);
    private static final int MAX_BATCH_SIZE = 5;
    private final GenericProducer<String, StorageUpdatePayload> storageUpdateProducer;
    private final String storageUpdateTopic;
    private final RippleStorageFacade storageFacade;
    private final ConversationSummaryStorage conversationStorage;

    public GroupHelper(
            GenericProducer<String, StorageUpdatePayload> storageUpdateProducer,
            String storageUpdateTopic,
            RippleStorageFacade storageFacade,
            ConversationSummaryStorage conversationStorage) {
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

    public void sendBatchedGroupInfoUpdates(
            long groupId,
            String groupName,
            String groupAvatar,
            List<Long> memberIds,
            long sendTimestamp,
            GroupInfoBatchUpdateData.UpdateType updateType,
            long messageId,
            long senderId) {
        int totalBatches = (int) Math.ceil(memberIds.size() / (float) MAX_BATCH_SIZE);

        for (int i = 0; i < totalBatches; i++) {
            int start = i * MAX_BATCH_SIZE;
            int end = Math.min((i + 1) * MAX_BATCH_SIZE, memberIds.size());
            List<Long> batchMembers = memberIds.subList(start, end);

            GroupInfoBatchUpdateData.Builder builder =
                    GroupInfoBatchUpdateData.newBuilder()
                            .setGroupId(groupId)
                            .addAllMemberIds(batchMembers)
                            .setBatchIndex(i)
                            .setTotalBatches(totalBatches)
                            .setSendTimestamp(sendTimestamp)
                            .setUpdateType(updateType)
                            .setMessageId(messageId)
                            .setSenderId(senderId);
            if (groupName != null) {
                builder.setGroupName(groupName);
            }
            if (groupAvatar != null) {
                builder.setGroupAvatar(groupAvatar);
            }

            StorageUpdatePayload payload =
                    StorageUpdatePayload.newBuilder()
                            .setGroupInfoBatchUpdateData(builder.build())
                            .build();
            this.storageUpdateProducer.send(
                    this.storageUpdateTopic, String.valueOf(groupId), payload);
        }
    }

    public String getJoinGroupCommandMessage(List<UserProfile> newMembers) {
        String memberNames =
                newMembers.stream().map(UserProfile::getNickName).collect(Collectors.joining("、"));
        return memberNames + " joined the group";
    }

    public void writeGroupCommandMessage(
            String conversationId,
            long messageId,
            long senderId,
            long groupId,
            long timestamp,
            byte commandType,
            String commandText) {
        this.storageFacade.saveGroupCommandMessage(
                conversationId, messageId, senderId, groupId, timestamp, commandType, commandText);
    }

    public void updateGroupUnreadCount(
            long senderId,
            List<Long> recipientUserIds,
            String conversationId,
            String messageText,
            long timestamp,
            long messageId) {
        this.conversationStorage.updateGroupConversationSummary(
                senderId, recipientUserIds, conversationId, messageText, timestamp, messageId);
    }
}
