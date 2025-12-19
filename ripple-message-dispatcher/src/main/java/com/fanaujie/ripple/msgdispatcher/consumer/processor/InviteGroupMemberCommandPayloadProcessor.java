package com.fanaujie.ripple.msgdispatcher.consumer.processor;

import com.fanaujie.ripple.cache.service.impl.RedisUserProfileStorage;
import com.fanaujie.ripple.communication.processor.Processor;
import com.fanaujie.ripple.msgdispatcher.consumer.processor.utils.GroupHelper;
import com.fanaujie.ripple.protobuf.msgapiserver.GroupInviteCommand;
import com.fanaujie.ripple.protobuf.msgapiserver.SendGroupCommandReq;
import com.fanaujie.ripple.protobuf.msgdispatcher.GroupCommandData;
import com.fanaujie.ripple.storage.model.UserProfile;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fanaujie.ripple.storage.service.utils.ConversationUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.fanaujie.ripple.protobuf.msgapiserver.SendGroupCommandReq.CommandContentCase.GROUP_INVITE_COMMAND;
import static com.fanaujie.ripple.storage.model.GroupCommandType.GROUP_COMMAND_TYPE_MEMBER_JOIN;

public class InviteGroupMemberCommandPayloadProcessor implements Processor<GroupCommandData, Void> {
    private final Logger logger =
            LoggerFactory.getLogger(InviteGroupMemberCommandPayloadProcessor.class);
    private final RippleStorageFacade storageFacade;
    private final RedisUserProfileStorage userProfileCache;
    private final GroupHelper groupHelper;

    public InviteGroupMemberCommandPayloadProcessor(
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
        if (sendGroupCommandReq.getCommandContentCase() == GROUP_INVITE_COMMAND) {
            this.updateGroupStorage(sendGroupCommandReq);
            return null;
        }
        throw new IllegalArgumentException(
                "Unknown message type for InviteGroupMemberCommandPayloadProcessor");
    }

    private void updateGroupStorage(SendGroupCommandReq sendGroupCommandReq) throws Exception {
        GroupInviteCommand groupInviteCommand = sendGroupCommandReq.getGroupInviteCommand();
        long groupId = sendGroupCommandReq.getGroupId();
        long version = sendGroupCommandReq.getSendTimestamp();

        List<UserProfile> newMembers = new ArrayList<>();
        for (long newMemberId : groupInviteCommand.getNewMemberIdsList()) {
            UserProfile userProfile = this.userProfileCache.get(newMemberId);
            if (userProfile != null) {
                newMembers.add(userProfile);
            } else {
                logger.warn("User profile not found in cache for userId: {}", newMemberId);
            }
        }

        this.storageFacade.createGroupMembersProfile(groupId, newMembers, version);
        String commandText = this.groupHelper.getJoinGroupCommandMessage(newMembers);
        String conversationId = ConversationUtils.generateGroupConversationId(groupId);
        this.groupHelper.writeGroupCommandMessage(
                conversationId,
                sendGroupCommandReq.getMessageId(),
                sendGroupCommandReq.getSenderId(),
                sendGroupCommandReq.getGroupId(),
                sendGroupCommandReq.getSendTimestamp(),
                GROUP_COMMAND_TYPE_MEMBER_JOIN.getValue(),
                commandText);
        List<Long> allMemberIds = this.storageFacade.getGroupMemberIds(groupId);
        this.groupHelper.updateGroupUnreadCount(
                sendGroupCommandReq.getSenderId(),
                allMemberIds,
                conversationId,
                commandText,
                sendGroupCommandReq.getSendTimestamp(),
                sendGroupCommandReq.getMessageId());

        this.groupHelper.sendBatchedStorageUpdates(
                groupId,
                groupInviteCommand.getGroupName(),
                groupInviteCommand.getGroupAvatar(),
                newMembers,
                sendGroupCommandReq.getSenderId(),
                sendGroupCommandReq.getSendTimestamp(),
                sendGroupCommandReq.getMessageId(),
                sendGroupCommandReq.getSenderId());
    }
}
