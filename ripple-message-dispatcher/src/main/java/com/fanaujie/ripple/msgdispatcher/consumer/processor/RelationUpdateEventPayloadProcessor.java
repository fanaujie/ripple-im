package com.fanaujie.ripple.msgdispatcher.consumer.processor;

import com.fanaujie.ripple.communication.processor.Processor;
import com.fanaujie.ripple.protobuf.msgapiserver.RelationEvent;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq;
import com.fanaujie.ripple.protobuf.msgdispatcher.EventData;
import com.fanaujie.ripple.protobuf.push.MultiNotifications;
import com.fanaujie.ripple.protobuf.push.PushEventData;
import com.fanaujie.ripple.protobuf.push.UserNotifications;
import com.fanaujie.ripple.storage.exception.*;
import com.fanaujie.ripple.storage.model.SyncFriendInfoResult;
import com.fanaujie.ripple.storage.model.UpdateFriendRemarkNameResult;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq.EventCase.RELATION_EVENT;
import static com.fanaujie.ripple.protobuf.push.UserNotificationType.*;

public class RelationUpdateEventPayloadProcessor implements Processor<EventData, PushEventData> {
    private final Logger logger =
            LoggerFactory.getLogger(RelationUpdateEventPayloadProcessor.class);
    private final RippleStorageFacade storageFacade;

    public RelationUpdateEventPayloadProcessor(RippleStorageFacade aggregator) {
        this.storageFacade = aggregator;
    }

    @Override
    public PushEventData handle(EventData eventData) throws Exception {
        SendEventReq sendEventReq = eventData.getData();
        if (sendEventReq.getEventCase() == RELATION_EVENT) {
            long receiverId = eventData.getReceiveUserIds(0);
            PushEventData.Builder pushEventDataBuilder = PushEventData.newBuilder();
            pushEventDataBuilder.setSendUserId(eventData.getSendUserId());
            UserNotifications userNotifications =
                    this.updateRelationStorage(receiverId, sendEventReq);
            return pushEventDataBuilder
                    .putUserNotifications(
                            userNotifications.getReceiveUserId(),
                            userNotifications.getNotification())
                    .build();
        }
        throw new IllegalArgumentException(
                "Unknown event type for SelfInfoUpdateEventPayloadProcessor");
    }

    private UserNotifications updateRelationStorage(long receiverId, SendEventReq sendEventReq)
            throws NotFoundUserProfileException,
                    NotFoundRelationException,
                    RelationAlreadyExistsException,
                    BlockAlreadyExistsException,
                    StrangerHasRelationshipException,
                    NotFoundBlockException {
        RelationEvent event = sendEventReq.getRelationEvent();
        UserNotifications.Builder userNotificationBuilder = UserNotifications.newBuilder();
        MultiNotifications.Builder multiNotificationsBuilder = MultiNotifications.newBuilder();
        userNotificationBuilder.setReceiveUserId(receiverId);
        multiNotificationsBuilder.addNotificationTypes(USER_NOTIFICATION_TYPE_RELATION_UPDATE);
        switch (event.getEventType()) {
            case ADD_FRIEND:
                storageFacade.addFriend(event);
                break;
            case REMOVE_FRIEND:
                storageFacade.removeFriend(event);
                break;
            case UPDATE_FRIEND_REMARK_NAME:
                {
                    UpdateFriendRemarkNameResult result =
                            storageFacade.updateFriendRemarkName(event);
                    if (result.isConversationUpdated()) {
                        multiNotificationsBuilder.addNotificationTypes(
                                USER_NOTIFICATION_TYPE_CONVERSATION_UPDATE);
                    }
                }
                break;
            case BLOCK_FRIEND:
                storageFacade.blockFriend(event);
                break;
            case BLOCK_STRANGER:
                storageFacade.blockStranger(event);
                break;
            case UNBLOCK_USER:
                storageFacade.unblockUser(event);
                break;
            case HIDE_BLOCKED_USER:
                storageFacade.hideBlockedUser(event);
                break;
            default:
                logger.error(
                        "updateRelationRepository: Unknown relation event type: {}",
                        event.getEventType());
                throw new IllegalArgumentException(
                        "Unknown relation event type: " + event.getEventType());
        }
        return userNotificationBuilder.setNotification(multiNotificationsBuilder.build()).build();
    }
}
