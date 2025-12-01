package com.fanaujie.ripple.msgdispatcher.consumer.processor;

import com.fanaujie.ripple.communication.processor.Processor;
import com.fanaujie.ripple.protobuf.msgapiserver.SelfInfoUpdateEvent;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq;
import com.fanaujie.ripple.protobuf.msgdispatcher.EventData;
import com.fanaujie.ripple.protobuf.push.MultiNotifications;
import com.fanaujie.ripple.protobuf.push.PushEventData;
import com.fanaujie.ripple.protobuf.push.UserNotifications;
import com.fanaujie.ripple.storage.exception.NotFoundRelationException;
import com.fanaujie.ripple.storage.exception.NotFoundUserProfileException;
import com.fanaujie.ripple.storage.model.UpdateFriendAvatarResult;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq.EventCase.SELF_INFO_UPDATE_EVENT;
import static com.fanaujie.ripple.protobuf.push.UserNotificationType.*;

public class SelfInfoUpdateEventPayloadProcessor implements Processor<EventData, PushEventData> {
    private final Logger logger =
            LoggerFactory.getLogger(SelfInfoUpdateEventPayloadProcessor.class);
    private final ExecutorService executor;
    private final RippleStorageFacade storageFacade;

    public SelfInfoUpdateEventPayloadProcessor(
            ExecutorService executor, RippleStorageFacade aggregator) {
        this.executor = executor;
        this.storageFacade = aggregator;
    }

    @Override
    public PushEventData handle(EventData eventData) throws Exception {
        SendEventReq sendEventReq = eventData.getData();
        PushEventData.Builder pushEventDataBuilder = PushEventData.newBuilder();
        if (sendEventReq.getEventCase() == SELF_INFO_UPDATE_EVENT) {
            List<Future<UserNotifications>> futures = new ArrayList<>();
            final int limitBatchWriteSize = 100;
            int batchSize = 0;
            for (long receiverId : eventData.getReceiveUserIdsList()) {
                ++batchSize;
                Future<UserNotifications> future =
                        executor.submit(
                                () -> {
                                    if (receiverId == eventData.getSendUserId()) {
                                        try {
                                            return this.updateUserStorage(sendEventReq);
                                        } catch (NotFoundUserProfileException e) {
                                            logger.warn(
                                                    "NotFoundUserProfileException when updating self info for receiverId {}: {}",
                                                    receiverId,
                                                    e.getMessage());
                                        }
                                        return null;
                                    } else {
                                        try {
                                            return this.updateRelationAndConversationStorage(
                                                    receiverId, sendEventReq);
                                        } catch (NotFoundRelationException e) {
                                            logger.warn(
                                                    "NotFoundRelationException when updating relation and conversation for receiverId {}: {}",
                                                    receiverId,
                                                    e.getMessage());
                                        }
                                        return null;
                                    }
                                });
                futures.add(future);
                if (batchSize >= limitBatchWriteSize) {
                    for (Future<UserNotifications> f : futures) {
                        UserNotifications n = f.get();
                        if (n != null) {
                            pushEventDataBuilder.putUserNotifications(
                                    n.getReceiveUserId(), n.getNotification());
                        }
                    }
                    futures.clear();
                    batchSize = 0;
                }
            }
            // wait for remaining tasks
            for (Future<UserNotifications> f : futures) {
                UserNotifications n = f.get();
                if (n != null) {
                    pushEventDataBuilder.putUserNotifications(
                            n.getReceiveUserId(), n.getNotification());
                }
            }
            return pushEventDataBuilder.build();
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
        switch (event.getEventType()) {
            case UPDATE_NICK_NAME:
                storageFacade.updateProfileNickNameByUserId(event.getUserId(), event.getNickName());
                break;
            case UPDATE_AVATAR:
                storageFacade.updateProfileAvatarByUserId(event.getUserId(), event.getAvatar());
                break;
            case DELETE_AVATAR:
                storageFacade.updateProfileAvatarByUserId(event.getUserId(), null);
                break;
            default:
                logger.error(
                        "updateSelfInfoInUserRepository: Unknown self info update event type {}",
                        event.getEventType());
                throw new IllegalArgumentException(
                        "Unknown self info update event type: " + event.getEventType());
        }
        return userNotificationBuilder.setNotification(multiNotificationsBuilder.build()).build();
    }

    private UserNotifications updateRelationAndConversationStorage(
            long friendId, SendEventReq sendEventReq) throws NotFoundRelationException {
        SelfInfoUpdateEvent event = sendEventReq.getSelfInfoUpdateEvent();
        UserNotifications.Builder userNotificationBuilder = UserNotifications.newBuilder();
        MultiNotifications.Builder multiNotificationsBuilder = MultiNotifications.newBuilder();
        userNotificationBuilder.setReceiveUserId(friendId);
        multiNotificationsBuilder.addNotificationTypes(USER_NOTIFICATION_TYPE_RELATION_UPDATE);
        switch (event.getEventType()) {
            case UPDATE_NICK_NAME:
                storageFacade.updateFriendNickName(
                        friendId, event.getUserId(), event.getNickName());
                break;

            case UPDATE_AVATAR:
            case DELETE_AVATAR:
                String avatar =
                        event.getEventType() == SelfInfoUpdateEvent.EventType.UPDATE_AVATAR
                                ? event.getAvatar()
                                : null;
                UpdateFriendAvatarResult result =
                        storageFacade.updateFriendAvatar(friendId, event.getUserId(), avatar);
                if (result.isConversationUpdated()) {
                    // Update conversation avatar if needed
                    multiNotificationsBuilder.addNotificationTypes(
                            USER_NOTIFICATION_TYPE_CONVERSATION_UPDATE);
                }
                break;
            default:
                logger.error(
                        "updateFriendViewInRelationRepository: Unknown self info update event type {}",
                        event.getEventType());
                throw new IllegalArgumentException(
                        "Unknown self info update event type: " + event.getEventType());
        }
        return userNotificationBuilder.setNotification(multiNotificationsBuilder.build()).build();
    }
}
