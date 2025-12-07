package com.fanaujie.ripple.msgdispatcher.consumer.processor;

import com.fanaujie.ripple.communication.msgqueue.GenericProducer;
import com.fanaujie.ripple.communication.msgqueue.uitls.MessageConverter;
import com.fanaujie.ripple.communication.processor.Processor;
import com.fanaujie.ripple.protobuf.msgapiserver.SelfInfoUpdateEvent;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq;
import com.fanaujie.ripple.protobuf.msgdispatcher.EventData;
import com.fanaujie.ripple.protobuf.profileupdater.GroupMemberBatchUpdateData;
import com.fanaujie.ripple.protobuf.profileupdater.ProfileUpdatePayload;
import com.fanaujie.ripple.protobuf.profileupdater.RelationBatchUpdateData;
import com.fanaujie.ripple.protobuf.push.MultiNotifications;
import com.fanaujie.ripple.protobuf.push.PushMessage;
import com.fanaujie.ripple.protobuf.push.UserNotifications;
import com.fanaujie.ripple.storage.exception.NotFoundUserProfileException;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq.EventCase.SELF_INFO_UPDATE_EVENT;
import static com.fanaujie.ripple.protobuf.push.UserNotificationType.*;

public class SelfInfoUpdateEventPayloadProcessor implements Processor<EventData, Void> {
    private static final Logger logger =
            LoggerFactory.getLogger(SelfInfoUpdateEventPayloadProcessor.class);
    private static final int MAX_BATCH_SIZE = 5;

    private final RippleStorageFacade storageFacade;
    private final GenericProducer<String, ProfileUpdatePayload> profileUpdateProducer;
    private final String profileUpdateTopic;
    private final GenericProducer<String, PushMessage> pushMessageGenericProducer;
    private final String pushTopic;

    public SelfInfoUpdateEventPayloadProcessor(
            RippleStorageFacade storageFacade,
            GenericProducer<String, ProfileUpdatePayload> profileUpdateProducer,
            String profileUpdateTopic,
            GenericProducer<String, PushMessage> pushMessageProducer,
            String pushTopic) {
        this.storageFacade = storageFacade;
        this.profileUpdateProducer = profileUpdateProducer;
        this.profileUpdateTopic = profileUpdateTopic;
        this.pushMessageGenericProducer = pushMessageProducer;
        this.pushTopic = pushTopic;
    }

    @Override
    public Void handle(EventData eventData) throws Exception {
        SendEventReq sendEventReq = eventData.getData();
        if (sendEventReq.getEventCase() == SELF_INFO_UPDATE_EVENT) {
            UserNotifications userNotifications = updateUserStorage(sendEventReq);
            this.pushMessageGenericProducer.send(
                    String.valueOf(eventData.getSendUserId()),
                    this.pushTopic,
                    MessageConverter.toPushMessage(eventData.getSendUserId(), userNotifications));
            return null;
        }
        logger.error(
                "handle: Unknown event type {} for SelfInfoUpdateEventPayloadProcessor",
                sendEventReq.getEventCase());
        throw new IllegalArgumentException(
                "Unknown event type %s for SelfInfoUpdateEventPayloadProcessor"
                        .formatted(sendEventReq.getEventCase()));
    }

    private UserNotifications updateUserStorage(SendEventReq sendEventReq)
            throws NotFoundUserProfileException {
        SelfInfoUpdateEvent event = sendEventReq.getSelfInfoUpdateEvent();
        UserNotifications.Builder userNotificationBuilder = UserNotifications.newBuilder();
        MultiNotifications.Builder multiNotificationsBuilder = MultiNotifications.newBuilder();
        userNotificationBuilder.setReceiveUserId(event.getUserId());
        multiNotificationsBuilder.addNotificationTypes(USER_NOTIFICATION_TYPE_SELF_INFO_UPDATE);

        long userId = event.getUserId();
        String nickname = null;
        String avatar = null;
        RelationBatchUpdateData.UpdateType relationUpdateType;
        GroupMemberBatchUpdateData.UpdateType groupUpdateType;

        // Update profile storage
        switch (event.getEventType()) {
            case UPDATE_NICK_NAME:
                nickname = event.getNickName();
                storageFacade.updateProfileNickNameByUserId(userId, nickname);
                relationUpdateType = RelationBatchUpdateData.UpdateType.UPDATE_NICKNAME;
                groupUpdateType = GroupMemberBatchUpdateData.UpdateType.UPDATE_NICKNAME;
                break;
            case UPDATE_AVATAR:
                avatar = event.getAvatar();
                storageFacade.updateProfileAvatarByUserId(userId, avatar);
                relationUpdateType = RelationBatchUpdateData.UpdateType.UPDATE_AVATAR;
                groupUpdateType = GroupMemberBatchUpdateData.UpdateType.UPDATE_AVATAR;
                break;
            case DELETE_AVATAR:
                storageFacade.updateProfileAvatarByUserId(userId, null);
                relationUpdateType = RelationBatchUpdateData.UpdateType.DELETE_AVATAR;
                groupUpdateType = GroupMemberBatchUpdateData.UpdateType.DELETE_AVATAR;
                break;
            default:
                logger.error(
                        "updateUserStorage: Unknown self info update event type {}",
                        event.getEventType());
                throw new IllegalArgumentException(
                        "Unknown self info update event type: " + event.getEventType());
        }

        // Publish batch updates asynchronously
        final String finalNickname = nickname;
        final String finalAvatar = avatar;
        final RelationBatchUpdateData.UpdateType finalRelationUpdateType = relationUpdateType;
        final GroupMemberBatchUpdateData.UpdateType finalGroupUpdateType = groupUpdateType;
        publishRelationBatchUpdates(userId, finalNickname, finalAvatar, finalRelationUpdateType);
        publishGroupMemberBatchUpdates(userId, finalNickname, finalAvatar, finalGroupUpdateType);
        return userNotificationBuilder.setNotification(multiNotificationsBuilder.build()).build();
    }

    private void publishRelationBatchUpdates(
            long userId,
            String nickname,
            String avatar,
            RelationBatchUpdateData.UpdateType updateType) {

        List<Long> friendIdList = storageFacade.getFriendIds(userId).getUserIdsList();
        if (friendIdList.isEmpty()) {
            return;
        }
        int totalBatches = (int) Math.ceil(friendIdList.size() / (float) MAX_BATCH_SIZE);
        for (int i = 0; i < totalBatches; i++) {
            int start = i * MAX_BATCH_SIZE;
            int end = Math.min((i + 1) * MAX_BATCH_SIZE, friendIdList.size());
            List<Long> batchFriendIds = friendIdList.subList(start, end);
            RelationBatchUpdateData.Builder batchBuilder =
                    RelationBatchUpdateData.newBuilder()
                            .setUserId(userId)
                            .addAllFriendIds(batchFriendIds)
                            .setBatchIndex(i)
                            .setTotalBatches(totalBatches)
                            .setUpdateType(updateType);

            if (nickname != null) {
                batchBuilder.setNickname(nickname);
            }
            if (avatar != null) {
                batchBuilder.setAvatar(avatar);
            }
            ProfileUpdatePayload payload =
                    ProfileUpdatePayload.newBuilder()
                            .setRelationBatchUpdateData(batchBuilder.build())
                            .build();
            profileUpdateProducer.send(profileUpdateTopic, String.valueOf(userId), payload);
        }
    }

    private void publishGroupMemberBatchUpdates(
            long userId,
            String nickname,
            String avatar,
            GroupMemberBatchUpdateData.UpdateType updateType) {

        List<Long> groupIds = storageFacade.getUserGroupIds(userId);

        if (groupIds.isEmpty()) {
            return;
        }
        int totalBatches = (int) Math.ceil(groupIds.size() / (float) MAX_BATCH_SIZE);
        for (int i = 0; i < totalBatches; i++) {
            int start = i * MAX_BATCH_SIZE;
            int end = Math.min((i + 1) * MAX_BATCH_SIZE, groupIds.size());
            List<Long> batchGroupIds = groupIds.subList(start, end);

            GroupMemberBatchUpdateData.Builder batchBuilder =
                    GroupMemberBatchUpdateData.newBuilder()
                            .setUserId(userId)
                            .addAllGroupIds(batchGroupIds)
                            .setBatchIndex(i)
                            .setTotalBatches(totalBatches)
                            .setUpdateType(updateType);

            if (nickname != null) {
                batchBuilder.setNickname(nickname);
            }
            if (avatar != null) {
                batchBuilder.setAvatar(avatar);
            }

            ProfileUpdatePayload payload =
                    ProfileUpdatePayload.newBuilder()
                            .setGroupMemberBatchUpdateData(batchBuilder.build())
                            .build();
            profileUpdateProducer.send(profileUpdateTopic, String.valueOf(userId), payload);
        }
    }
}
