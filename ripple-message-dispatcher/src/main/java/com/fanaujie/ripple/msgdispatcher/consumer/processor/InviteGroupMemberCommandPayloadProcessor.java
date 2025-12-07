package com.fanaujie.ripple.msgdispatcher.consumer.processor;

import com.fanaujie.ripple.communication.processor.Processor;
import com.fanaujie.ripple.msgdispatcher.consumer.processor.utils.GroupNotificationHelper;
import com.fanaujie.ripple.protobuf.msgapiserver.GroupInviteCommand;
import com.fanaujie.ripple.protobuf.msgapiserver.SendGroupCommandReq;
import com.fanaujie.ripple.protobuf.msgdispatcher.GroupCommandData;
import com.fanaujie.ripple.storage.cache.UserProfileRedissonKvCache;
import com.fanaujie.ripple.storage.model.GroupInfo;
import com.fanaujie.ripple.storage.model.UserProfile;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.fanaujie.ripple.protobuf.msgapiserver.SendGroupCommandReq.CommandContentCase.GROUP_INVITE_COMMAND;

public class InviteGroupMemberCommandPayloadProcessor implements Processor<GroupCommandData, Void> {
    private final Logger logger =
            LoggerFactory.getLogger(InviteGroupMemberCommandPayloadProcessor.class);
    private final RippleStorageFacade storageFacade;
    private final UserProfileRedissonKvCache userProfileCache;
    private final GroupNotificationHelper groupNotificationHelper;

    public InviteGroupMemberCommandPayloadProcessor(
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

        GroupInfo groupInfo = this.storageFacade.getGroupInfo(groupId);
        List<UserProfile> newMembers = new ArrayList<>();
        for (long newMemberId : groupInviteCommand.getNewMemberIdsList()) {
            UserProfile userProfile = this.userProfileCache.get(newMemberId);
            if (userProfile != null) {
                newMembers.add(userProfile);
            } else {
                logger.warn("User profile not found in cache for userId: {}", newMemberId);
            }
        }

        this.storageFacade.createGroupMembersProfile(groupId, newMembers);

        List<Long> allRecipientIds =
                Stream.concat(
                                groupInfo.getMemberIds().stream(),
                                newMembers.stream().map(UserProfile::getUserId))
                        .distinct()
                        .collect(Collectors.toList());
        this.groupNotificationHelper.sendBatchedProfileUpdates(
                groupId,
                groupInfo.getGroupName(),
                groupInfo.getGroupAvatar(),
                newMembers,
                sendGroupCommandReq.getSenderId());

        this.groupNotificationHelper.writeJoinGroupCommandMessageAndPush(
                sendGroupCommandReq, groupId, newMembers, allRecipientIds);
    }
}
