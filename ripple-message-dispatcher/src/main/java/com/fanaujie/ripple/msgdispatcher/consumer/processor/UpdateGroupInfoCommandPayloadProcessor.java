package com.fanaujie.ripple.msgdispatcher.consumer.processor;

import com.fanaujie.ripple.communication.msgqueue.GenericProducer;
import com.fanaujie.ripple.communication.processor.Processor;
import com.fanaujie.ripple.protobuf.msgapiserver.GroupUpdateInfoCommand;
import com.fanaujie.ripple.protobuf.msgapiserver.SendGroupCommandReq;
import com.fanaujie.ripple.protobuf.msgdispatcher.GroupCommandData;
import com.fanaujie.ripple.protobuf.storageupdater.GroupInfoBatchUpdateData;
import com.fanaujie.ripple.protobuf.storageupdater.StorageUpdatePayload;
import com.fanaujie.ripple.storage.model.UserProfile;
import com.fanaujie.ripple.storage.service.ConversationStateFacade;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import com.fanaujie.ripple.storage.service.impl.CachingUserProfileStorage;
import com.fanaujie.ripple.storage.service.utils.ConversationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

import static com.fanaujie.ripple.protobuf.msgapiserver.SendGroupCommandReq.CommandContentCase.GROUP_UPDATE_INFO_COMMAND;
import static com.fanaujie.ripple.storage.model.GroupCommandType.GROUP_COMMAND_TYPE_INFO_UPDATE;

public class UpdateGroupInfoCommandPayloadProcessor implements Processor<GroupCommandData, Void> {
    private static final int MAX_BATCH_SIZE = 5;
    private final Logger logger =
            LoggerFactory.getLogger(UpdateGroupInfoCommandPayloadProcessor.class);
    private final RippleStorageFacade storageFacade;
    private final GenericProducer<String, StorageUpdatePayload> storageUpdateProducer;
    private final String storageUpdateTopic;
    private final ConversationStateFacade conversationStorage;
    private final CachingUserProfileStorage userProfileCache;

    public UpdateGroupInfoCommandPayloadProcessor(
            RippleStorageFacade storageFacade,
            GenericProducer<String, StorageUpdatePayload> storageUpdateProducer,
            String storageUpdateTopic,
            ConversationStateFacade conversationStorage,
            CachingUserProfileStorage userProfileCache) {
        this.storageFacade = storageFacade;
        this.storageUpdateProducer = storageUpdateProducer;
        this.storageUpdateTopic = storageUpdateTopic;
        this.conversationStorage = conversationStorage;
        this.userProfileCache = userProfileCache;
    }

    @Override
    public Void handle(GroupCommandData groupCommandData) throws Exception {
        SendGroupCommandReq sendGroupCommandReq = groupCommandData.getData();
        if (sendGroupCommandReq.getCommandContentCase() == GROUP_UPDATE_INFO_COMMAND) {
            this.updateGroupStorage(sendGroupCommandReq);
            return null;
        }
        throw new IllegalArgumentException(
                "Unknown message type for UpdateGroupInfoCommandPayloadProcessor");
    }

    private void updateGroupStorage(SendGroupCommandReq sendGroupCommandReq) throws Exception {
        GroupUpdateInfoCommand updateCommand = sendGroupCommandReq.getGroupUpdateInfoCommand();
        long groupId = sendGroupCommandReq.getGroupId();
        long senderId = sendGroupCommandReq.getSenderId();
        long messageId = sendGroupCommandReq.getMessageId();

        GroupInfoBatchUpdateData.UpdateType batchUpdateType;
        String newName = null;
        String newAvatar = null;
        String commandText;

        // Get updater's name
        UserProfile updaterProfile = this.userProfileCache.get(senderId);
        String updaterName = updaterProfile != null ? updaterProfile.getNickName() : "User";

        switch (updateCommand.getUpdateType()) {
            case UPDATE_NAME:
                newName = updateCommand.getNewName();
                batchUpdateType = GroupInfoBatchUpdateData.UpdateType.UPDATE_NAME;
                commandText = updaterName + " changed the group name to " + newName;
                break;
            case UPDATE_AVATAR:
                newAvatar = updateCommand.getNewAvatar();
                batchUpdateType = GroupInfoBatchUpdateData.UpdateType.UPDATE_AVATAR;
                commandText = updaterName + " changed the group avatar";
                break;
            case UNSPECIFIED:
            default:
                logger.warn("Unknown update type in UpdateGroupInfoCommand for group {}", groupId);
                return;
        }

        List<Long> memberIds = this.storageFacade.getGroupMemberIds(groupId);

        // Write group command message
        String conversationId = ConversationUtils.generateGroupConversationId(groupId);
        this.storageFacade.saveGroupCommandMessage(
                conversationId,
                messageId,
                senderId,
                groupId,
                sendGroupCommandReq.getSendTimestamp(),
                GROUP_COMMAND_TYPE_INFO_UPDATE.getValue(),
                commandText);

        conversationStorage.batchUpdateConversation(
                Collections.emptyList(),
                conversationId,
                commandText,
                sendGroupCommandReq.getSendTimestamp(),
                String.valueOf(messageId),
                false);

        sendBatchedGroupInfoUpdates(
                groupId,
                newName,
                newAvatar,
                memberIds,
                sendGroupCommandReq.getSendTimestamp(),
                batchUpdateType,
                messageId,
                senderId);
    }

    private void sendBatchedGroupInfoUpdates(
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
}
