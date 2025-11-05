package com.fanaujie.ripple.msgdispatcher.consumer.processor;

import com.fanaujie.ripple.communication.processor.Processor;
import com.fanaujie.ripple.protobuf.msgapiserver.SelfInfoUpdateEvent;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq;
import com.fanaujie.ripple.protobuf.msgdispatcher.EventData;
import com.fanaujie.ripple.storage.exception.NotFoundRelationException;
import com.fanaujie.ripple.storage.exception.NotFoundUserProfileException;
import com.fanaujie.ripple.storage.repository.RelationRepository;
import com.fanaujie.ripple.storage.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq.EventCase.SELF_INFO_UPDATE_EVENT;

public class SelfInfoUpdateEventPayloadProcessor implements Processor<EventData, Void> {
    private final Logger logger =
            LoggerFactory.getLogger(SelfInfoUpdateEventPayloadProcessor.class);
    private final ExecutorService executor;
    private final UserRepository userRepository;
    private final RelationRepository relationRepository;

    public SelfInfoUpdateEventPayloadProcessor(
            ExecutorService executor,
            UserRepository userRepository,
            RelationRepository relationRepository) {
        this.executor = executor;
        this.userRepository = userRepository;
        this.relationRepository = relationRepository;
    }

    @Override
    public Void handle(EventData eventData) throws Exception {
        SendEventReq sendEventReq = eventData.getData();
        if (sendEventReq.getEventCase() == SELF_INFO_UPDATE_EVENT) {
            List<Future<?>> futures = new ArrayList<>();
            final int limitBatchWriteSize = 100;
            int batchSize = 0;
            for (long receiverId : eventData.getReceiveUserIdsList()) {
                ++batchSize;
                Future<?> future =
                        executor.submit(
                                () -> {
                                    if (receiverId == eventData.getSendUserId()) {
                                        try {
                                            this.updateUserStorage(sendEventReq);
                                        } catch (NotFoundUserProfileException e) {
                                            // do nothing if user not found
                                        }
                                    } else {
                                        try {
                                            this.updateFriendViewStorage(receiverId, sendEventReq);
                                        } catch (NotFoundRelationException e) {
                                            // do nothing if relation not found
                                        }
                                    }
                                });
                futures.add(future);
                if (batchSize >= limitBatchWriteSize) {
                    for (Future<?> f : futures) {
                        f.get();
                    }
                    futures.clear();
                    batchSize = 0;
                }
            }
            // wait for remaining tasks
            for (Future<?> f : futures) {
                f.get();
            }
            return null;
        }
        logger.error(
                "handle: Unknown event type {} for SelfInfoUpdateEventPayloadProcessor",
                sendEventReq.getEventCase());
        throw new IllegalArgumentException(
                "Unknown event type %s for SelfInfoUpdateEventPayloadProcessor"
                        .formatted(sendEventReq.getEventCase()));
    }

    private void updateUserStorage(SendEventReq sendEventReq) throws NotFoundUserProfileException {
        SelfInfoUpdateEvent event = sendEventReq.getSelfInfoUpdateEvent();
        switch (event.getEventType()) {
            case UPDATE_NICK_NAME:
                userRepository.updateNickNameByUserId(event.getUserId(), event.getNickName());
                break;
            case UPDATE_AVATAR:
                userRepository.updateAvatarByUserId(event.getUserId(), event.getAvatar());
                break;
            case DELETE_AVATAR:
                userRepository.updateAvatarByUserId(event.getUserId(), null);
                break;
            default:
                logger.error(
                        "updateSelfInfoInUserRepository: Unknown self info update event type {}",
                        event.getEventType());
                throw new IllegalArgumentException(
                        "Unknown self info update event type: " + event.getEventType());
        }
    }

    private void updateFriendViewStorage(long friendId, SendEventReq sendEventReq)
            throws NotFoundRelationException {
        SelfInfoUpdateEvent event = sendEventReq.getSelfInfoUpdateEvent();
        switch (event.getEventType()) {
            case UPDATE_NICK_NAME:
                relationRepository.updateFriendNickName(
                        friendId, event.getUserId(), event.getNickName());
                break;
            case UPDATE_AVATAR:
                relationRepository.updateFriendAvatar(
                        friendId, event.getUserId(), event.getAvatar());
                break;
            case DELETE_AVATAR:
                relationRepository.updateFriendAvatar(event.getUserId(), event.getUserId(), null);
                break;
            default:
                logger.error(
                        "updateFriendViewInRelationRepository: Unknown self info update event type {}",
                        event.getEventType());
                throw new IllegalArgumentException(
                        "Unknown self info update event type: " + event.getEventType());
        }
    }
}
