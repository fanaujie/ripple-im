package com.fanaujie.ripple.msgdispatcher.consumer.processor;

import com.fanaujie.ripple.communication.processor.Processor;
import com.fanaujie.ripple.msgdispatcher.consumer.processor.utils.GroupNotificationHelper;
import com.fanaujie.ripple.protobuf.msgapiserver.GroupCreateCommand;
import com.fanaujie.ripple.protobuf.msgapiserver.SendGroupCommandReq;
import com.fanaujie.ripple.protobuf.msgdispatcher.GroupCommandData;
import com.fanaujie.ripple.storage.cache.UserProfileRedissonKvCache;
import com.fanaujie.ripple.storage.model.UserProfile;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.fanaujie.ripple.protobuf.msgapiserver.SendGroupCommandReq.CommandContentCase.GROUP_CREATE_COMMAND;

public class CreateGroupCommandPayloadProcessor implements Processor<GroupCommandData, Void> {
    private final Logger logger = LoggerFactory.getLogger(CreateGroupCommandPayloadProcessor.class);
    private final RippleStorageFacade storageFacade;
    private final UserProfileRedissonKvCache userProfileCache;
    private final GroupNotificationHelper groupNotificationHelper;

    public CreateGroupCommandPayloadProcessor(
            RippleStorageFacade storageFacade,
            UserProfileRedissonKvCache userProfileCache,
            GroupNotificationHelper groupNotificationHelper) {
        this.storageFacade = storageFacade;
        this.userProfileCache = userProfileCache;
        this.groupNotificationHelper = groupNotificationHelper;
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
        List<UserProfile> allMembers = new ArrayList<>();
        for (long memberId : groupCreateCommand.getMemberIdsList()) {
            UserProfile userProfile = this.userProfileCache.get(memberId);
            if (userProfile != null) {
                allMembers.add(userProfile);
            } else {
                logger.warn("User profile not found in cache for userId: {}", memberId);
            }
        }
        this.storageFacade.createGroup(
                groupId,
                groupCreateCommand.getGroupName(),
                groupCreateCommand.getGroupAvatar(),
                allMembers);

        List<Long> memberIds =
                allMembers.stream().map(UserProfile::getUserId).collect(Collectors.toList());

        this.groupNotificationHelper.sendBatchedProfileUpdates(
                groupId,
                groupCreateCommand.getGroupName(),
                groupCreateCommand.getGroupAvatar(),
                allMembers,
                sendGroupCommandReq.getSenderId());

        this.groupNotificationHelper.writeJoinGroupCommandMessageAndPush(
                sendGroupCommandReq, groupId, allMembers, memberIds);
    }
}
