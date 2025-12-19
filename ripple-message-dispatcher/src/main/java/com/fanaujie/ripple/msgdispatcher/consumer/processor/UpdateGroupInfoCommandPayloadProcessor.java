package com.fanaujie.ripple.msgdispatcher.consumer.processor;

import com.fanaujie.ripple.cache.service.impl.RedisUserProfileStorage;
import com.fanaujie.ripple.communication.msgqueue.GenericProducer;
import com.fanaujie.ripple.communication.processor.Processor;
import com.fanaujie.ripple.msgdispatcher.consumer.processor.utils.GroupHelper;
import com.fanaujie.ripple.protobuf.msgapiserver.GroupUpdateInfoCommand;
import com.fanaujie.ripple.protobuf.msgapiserver.SendGroupCommandReq;
import com.fanaujie.ripple.protobuf.msgdispatcher.GroupCommandData;
import com.fanaujie.ripple.protobuf.storageupdater.GroupInfoBatchUpdateData;
import com.fanaujie.ripple.protobuf.storageupdater.StorageUpdatePayload;
import com.fanaujie.ripple.storage.model.UserProfile;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import com.fanaujie.ripple.storage.service.utils.ConversationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.fanaujie.ripple.protobuf.msgapiserver.SendGroupCommandReq.CommandContentCase.GROUP_UPDATE_INFO_COMMAND;
import static com.fanaujie.ripple.storage.model.GroupCommandType.GROUP_COMMAND_TYPE_INFO_UPDATE;

public class UpdateGroupInfoCommandPayloadProcessor implements Processor<GroupCommandData, Void> {
    private final Logger logger =
            LoggerFactory.getLogger(UpdateGroupInfoCommandPayloadProcessor.class);
    private final RippleStorageFacade storageFacade;
    private final RedisUserProfileStorage userProfileCache;
    private final GroupHelper groupHelper;

    public UpdateGroupInfoCommandPayloadProcessor(
            RippleStorageFacade storageFacade,
            RedisUserProfileStorage userProfileCache,
            GroupHelper groupHelper) {
        this.storageFacade = storageFacade;
        this.userProfileCache = userProfileCache;
        this.groupHelper = groupHelper;
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
        String conversationId = ConversationUtils.generateGroupConversationId(groupId);
        this.groupHelper.writeGroupCommandMessage(
                conversationId,
                messageId,
                senderId,
                groupId,
                sendGroupCommandReq.getSendTimestamp(),
                GROUP_COMMAND_TYPE_INFO_UPDATE.getValue(),
                commandText);
        this.groupHelper.updateGroupUnreadCount(
                senderId,
                memberIds,
                conversationId,
                commandText,
                sendGroupCommandReq.getSendTimestamp(),
                messageId);
        this.groupHelper.sendBatchedGroupInfoUpdates(
                groupId,
                newName,
                newAvatar,
                memberIds,
                sendGroupCommandReq.getSendTimestamp(),
                batchUpdateType,
                messageId,
                senderId);
    }
}
