package com.fanaujie.ripple.msgdispatcher.consumer.processor;

import com.fanaujie.ripple.communication.msgqueue.GenericProducer;
import com.fanaujie.ripple.communication.msgqueue.uitls.MessageConverter;
import com.fanaujie.ripple.communication.processor.Processor;
import com.fanaujie.ripple.protobuf.msgapiserver.RelationEvent;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq;
import com.fanaujie.ripple.protobuf.msgdispatcher.EventData;
import com.fanaujie.ripple.protobuf.profileupdater.FriendProfileUpdateData;
import com.fanaujie.ripple.protobuf.profileupdater.ProfileUpdatePayload;
import com.fanaujie.ripple.protobuf.push.MultiNotifications;
import com.fanaujie.ripple.protobuf.push.PushEventData;
import com.fanaujie.ripple.protobuf.push.PushMessage;
import com.fanaujie.ripple.protobuf.push.UserNotifications;
import com.fanaujie.ripple.storage.exception.*;
import com.fanaujie.ripple.storage.model.Relation;
import com.fanaujie.ripple.storage.model.RelationFlags;
import com.fanaujie.ripple.storage.model.SyncFriendInfoResult;
import com.fanaujie.ripple.storage.model.UpdateFriendRemarkNameResult;
import com.fanaujie.ripple.storage.model.UserProfile;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

import static com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq.EventCase.RELATION_EVENT;
import static com.fanaujie.ripple.protobuf.push.UserNotificationType.*;

public class RelationUpdateEventPayloadProcessor implements Processor<EventData, Void> {
    private final Logger logger =
            LoggerFactory.getLogger(RelationUpdateEventPayloadProcessor.class);
    private final RippleStorageFacade storageFacade;
    private final GenericProducer<String, ProfileUpdatePayload> profileUpdateProducer;
    private final String profileUpdateTopic;
    private final GenericProducer<String, PushMessage> pushProducer;
    private final String pushTopic;

    public RelationUpdateEventPayloadProcessor(
            RippleStorageFacade storageFacade,
            GenericProducer<String, ProfileUpdatePayload> profileUpdateProducer,
            String profileUpdateTopic,
            GenericProducer<String, PushMessage> pushMessageProducer,
            String pushTopic) {
        this.storageFacade = storageFacade;
        this.profileUpdateProducer = profileUpdateProducer;
        this.profileUpdateTopic = profileUpdateTopic;
        this.pushProducer = pushMessageProducer;
        this.pushTopic = pushTopic;
    }

    @Override
    public Void handle(EventData eventData) throws Exception {
        SendEventReq sendEventReq = eventData.getData();
        if (sendEventReq.getEventCase() == RELATION_EVENT) {
            long receiverId = eventData.getReceiveUserIds(0);
            PushEventData.Builder pushEventDataBuilder = PushEventData.newBuilder();
            pushEventDataBuilder.setSendUserId(eventData.getSendUserId());
            UserNotifications userNotifications =
                    this.updateRelationStorage(receiverId, sendEventReq);
            this.pushProducer.send(
                    this.pushTopic,
                    String.valueOf(eventData.getSendUserId()),
                    MessageConverter.toPushMessage(eventData.getSendUserId(), userNotifications));
            return null;
        }
        throw new IllegalArgumentException(
                "Unknown event type for RelationUpdateEventPayloadProcessor");
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
                // Check if bidirectional friendship exists and send FriendProfileUpdateData
                Relation reverseRelation =
                        storageFacade.getRelationBetweenUser(
                                event.getTargetUserId(), event.getUserId());
                if (reverseRelation != null
                        && RelationFlags.FRIEND.isSet(reverseRelation.getRelationFlags())) {
                    UserProfile userProfile = storageFacade.getUserProfile(event.getUserId());
                    FriendProfileUpdateData friendProfileUpdateData =
                            FriendProfileUpdateData.newBuilder()
                                    .setUserId(event.getTargetUserId())
                                    .setFriendId(event.getUserId())
                                    .setFriendNickname(userProfile.getNickName())
                                    .setFriendAvatar(userProfile.getAvatar())
                                    .build();
                    ProfileUpdatePayload profileUpdatePayload =
                            ProfileUpdatePayload.newBuilder()
                                    .setFriendProfileUpdateData(friendProfileUpdateData)
                                    .build();
                    profileUpdateProducer.send(
                            profileUpdateTopic,
                            String.valueOf(event.getUserId()),
                            profileUpdatePayload);
                }
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
