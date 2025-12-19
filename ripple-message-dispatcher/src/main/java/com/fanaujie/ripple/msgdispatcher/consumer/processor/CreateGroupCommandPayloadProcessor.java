package com.fanaujie.ripple.msgdispatcher.consumer.processor;

import com.fanaujie.ripple.cache.service.impl.RedisUserProfileStorage;
import com.fanaujie.ripple.communication.processor.Processor;
import com.fanaujie.ripple.msgdispatcher.consumer.processor.utils.GroupHelper;
import com.fanaujie.ripple.protobuf.msgapiserver.GroupCreateCommand;
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

import static com.fanaujie.ripple.protobuf.msgapiserver.SendGroupCommandReq.CommandContentCase.GROUP_CREATE_COMMAND;
import static com.fanaujie.ripple.storage.model.GroupCommandType.GROUP_COMMAND_TYPE_MEMBER_JOIN;

public class CreateGroupCommandPayloadProcessor implements Processor<GroupCommandData, Void> {
    private final Logger logger = LoggerFactory.getLogger(CreateGroupCommandPayloadProcessor.class);
    private final RippleStorageFacade storageFacade;
    private final RedisUserProfileStorage userProfileCache;
    private final GroupHelper groupHelper;

    public CreateGroupCommandPayloadProcessor(
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
        if (sendGroupCommandReq.getCommandContentCase() == GROUP_CREATE_COMMAND) {
            this.updateGroupStorage(sendGroupCommandReq);
            return null;
        }
        throw new IllegalArgumentException(
                "Unknown message type for CreateGroupCommandPayloadProcessor");
    }

    private void updateGroupStorage(SendGroupCommandReq sendGroupCommandReq) throws Exception {
        GroupCreateCommand groupCreateCommand = sendGroupCommandReq.getGroupCreateCommand();
        long groupId = sendGroupCommandReq.getGroupId();
        long version = sendGroupCommandReq.getSendTimestamp();

        List<UserProfile> allMembers = new ArrayList<>();
        for (long memberId : groupCreateCommand.getMemberIdsList()) {
            UserProfile userProfile = this.userProfileCache.get(memberId);
            if (userProfile != null) {
                allMembers.add(userProfile);
            } else {
                logger.warn("User profile not found in cache for userId: {}", memberId);
            }
        }
        this.storageFacade.createGroup(groupId, allMembers, version);
        String commandText = this.groupHelper.getJoinGroupCommandMessage(allMembers);
        String conversationId = ConversationUtils.generateGroupConversationId(groupId);
        this.groupHelper.writeGroupCommandMessage(
                conversationId,
                sendGroupCommandReq.getMessageId(),
                sendGroupCommandReq.getSenderId(),
                sendGroupCommandReq.getGroupId(),
                sendGroupCommandReq.getSendTimestamp(),
                GROUP_COMMAND_TYPE_MEMBER_JOIN.getValue(),
                commandText);
        List<Long> memberIds =
                allMembers.stream().map(UserProfile::getUserId).collect(Collectors.toList());
        this.groupHelper.updateGroupUnreadCount(
                sendGroupCommandReq.getSenderId(),
                memberIds,
                conversationId,
                commandText,
                sendGroupCommandReq.getSendTimestamp(),
                sendGroupCommandReq.getMessageId());
        this.groupHelper.sendBatchedStorageUpdates(
                groupId,
                groupCreateCommand.getGroupName(),
                groupCreateCommand.getGroupAvatar(),
                allMembers,
                sendGroupCommandReq.getSenderId(),
                sendGroupCommandReq.getSendTimestamp(),
                sendGroupCommandReq.getMessageId(),
                sendGroupCommandReq.getSenderId());
    }
}
