package com.fanaujie.ripple.msgdispatcher.consumer.processor;

import com.fanaujie.ripple.communication.processor.Processor;
import com.fanaujie.ripple.protobuf.msgapiserver.SelfInfoUpdateEvent;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq;
import com.fanaujie.ripple.protobuf.msgdispatcher.EventData;
import com.fanaujie.ripple.protobuf.push.MultiNotifications;
import com.fanaujie.ripple.protobuf.push.PushEventData;
import com.fanaujie.ripple.protobuf.push.UserNotifications;
import com.fanaujie.ripple.storage.exception.NotFoundUserProfileException;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

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
            UserNotifications userNotifications =
                    executor.submit(() -> updateUserStorage(sendEventReq)).get();
            pushEventDataBuilder.putUserNotifications(
                    userNotifications.getReceiveUserId(), userNotifications.getNotification());
            pushEventDataBuilder.setSendUserId(eventData.getSendUserId());
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
}
