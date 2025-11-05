package com.fanaujie.ripple.msgdispatcher.consumer.processor;

import com.fanaujie.ripple.communication.processor.Processor;
import com.fanaujie.ripple.protobuf.msgapiserver.RelationEvent;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq;
import com.fanaujie.ripple.protobuf.msgdispatcher.EventData;
import com.fanaujie.ripple.storage.exception.*;
import com.fanaujie.ripple.storage.model.UserProfile;
import com.fanaujie.ripple.storage.repository.RelationRepository;
import com.fanaujie.ripple.storage.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

import static com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq.EventCase.RELATION_EVENT;

public class RelationUpdateEventPayloadProcessor implements Processor<EventData, Void> {
    private final Logger logger =
            LoggerFactory.getLogger(RelationUpdateEventPayloadProcessor.class);
    private final UserRepository userRepository;
    private final RelationRepository relationRepository;

    public RelationUpdateEventPayloadProcessor(
            UserRepository userRepository, RelationRepository relationRepository) {
        this.userRepository = userRepository;
        this.relationRepository = relationRepository;
    }

    @Override
    public Void handle(EventData eventData) throws Exception {
        SendEventReq sendEventReq = eventData.getData();
        if (sendEventReq.getEventCase() == RELATION_EVENT) {
            this.updateRelationStorage(sendEventReq);
            return null;
        }
        throw new IllegalArgumentException(
                "Unknown event type for SelfInfoUpdateEventPayloadProcessor");
    }

    private void updateRelationStorage(SendEventReq sendEventReq)
            throws NotFoundUserProfileException,
                    NotFoundRelationException,
                    RelationAlreadyExistsException,
                    BlockAlreadyExistsException,
                    StrangerHasRelationshipException,
                    NotFoundBlockException {
        RelationEvent event = sendEventReq.getRelationEvent();
        switch (event.getEventType()) {
            case ADD_FRIEND:
                UserProfile userProfile =
                        this.userRepository.getUserProfile(event.getTargetUserId());
                this.relationRepository.addFriend(event.getUserId(), userProfile);
                break;
            case REMOVE_FRIEND:
                this.relationRepository.removeFriend(event.getUserId(), event.getTargetUserId());
                break;
            case UPDATE_FRIEND_REMARK_NAME:
                this.relationRepository.updateFriendRemarkName(
                        event.getUserId(),
                        event.getTargetUserId(),
                        event.getTargetUserRemarkName());
                break;
            case BLOCK_FRIEND:
                this.relationRepository.addBlock(event.getUserId(), event.getTargetUserId());
                break;
            case BLOCK_STRANGER:
                UserProfile strangerProfile =
                        this.userRepository.getUserProfile(event.getTargetUserId());
                this.relationRepository.addBlockStranger(event.getUserId(), strangerProfile);
                break;
            case UNBLOCK_USER:
                this.relationRepository.removeBlock(event.getUserId(), event.getTargetUserId());
                break;
            case HIDE_BLOCKED_USER:
                this.relationRepository.hideBlock(event.getUserId(), event.getTargetUserId());
                break;
            case UPDATE_FRIEND_INFO:
                this.relationRepository.updateFriendInfo(
                        event.getUserId(),
                        event.getTargetUserId(),
                        event.getTargetUserNickName(),
                        event.getTargetUserAvatar());
                break;
            default:
                logger.error(
                        "updateRelationRepository: Unknown relation event type: {}",
                        event.getEventType());
                throw new IllegalArgumentException(
                        "Unknown relation event type: " + event.getEventType());
        }
    }
}
