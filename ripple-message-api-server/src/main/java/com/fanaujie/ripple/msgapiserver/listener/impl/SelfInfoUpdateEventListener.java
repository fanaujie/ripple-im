package com.fanaujie.ripple.msgapiserver.listener.impl;

import com.fanaujie.ripple.communication.messaging.GenericProducer;
import com.fanaujie.ripple.msgapiserver.exception.NotFoundAnyFriendIdsException;
import com.fanaujie.ripple.msgapiserver.listener.EventListener;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventResp;
import com.fanaujie.ripple.protobuf.msgdispatcher.EventData;
import com.fanaujie.ripple.protobuf.msgdispatcher.EventType;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;
import com.fanaujie.ripple.protobuf.storage.UserIds;
import com.fanaujie.ripple.storage.service.RelationStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

public class SelfInfoUpdateEventListener implements EventListener {

    private final Logger logger = LoggerFactory.getLogger(SelfInfoUpdateEventListener.class);
    private final String topicName;
    private final RelationStorage relationStorage;
    private final GenericProducer<String, MessagePayload> producer;
    private final ExecutorService executorService;

    public SelfInfoUpdateEventListener(
            String topicName,
            RelationStorage relationStorage,
            GenericProducer<String, MessagePayload> producer,
            ExecutorService executorService) {
        this.topicName = topicName;
        this.relationStorage = relationStorage;
        this.producer = producer;
        this.executorService = executorService;
    }

    @Override
    public SendEventResp handleEvent(SendEventReq request) throws Exception {
        long userId = request.getSelfInfoUpdateEvent().getUserId();
        logger.debug("handleEvent: Processing self info update for userId: {}", userId);

        UserIds friendIds =
                this.relationStorage
                        .getFriendIds(userId)
                        .orElseThrow(
                                () ->
                                        new NotFoundAnyFriendIdsException(
                                                "No friends found for userId: "
                                                        + request.getSelfInfoUpdateEvent()
                                                                .getUserId()));

        logger.debug(
                "handleEvent: Found {} friends for userId: {}",
                friendIds.getUserIdsList().size(),
                userId);

        EventData.Builder b =
                EventData.newBuilder()
                        .setSendUserId(userId)
                        .setEventType(EventType.EVENT_TYPE_SELF_INFO_UPDATE)
                        .setContent(request.toByteString());
        for (long friendUserId : friendIds.getUserIdsList()) {
            b.addReceiveUserIds(friendUserId);
        }

        b.addReceiveUserIds(userId); // also notify self for multi-device sync
        logger.debug(
                "handleEvent: Total receive users count: {}", b.getReceiveUserIdsList().size());

        MessagePayload messageData = MessagePayload.newBuilder().setEventData(b.build()).build();
        logger.debug(
                "handleEvent: Submitting message to topic: {} for userId: {}", topicName, userId);

        this.executorService.submit(
                () -> {
                    logger.debug("handleEvent: Sending message to producer for userId: {}", userId);
                    this.producer.send(this.topicName, String.valueOf(userId), messageData);
                    logger.debug("handleEvent: Message sent successfully for userId: {}", userId);
                });
        return SendEventResp.newBuilder().build();
    }
}
