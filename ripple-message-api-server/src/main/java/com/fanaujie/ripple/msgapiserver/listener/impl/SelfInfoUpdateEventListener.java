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

import java.util.concurrent.ExecutorService;

public class SelfInfoUpdateEventListener implements EventListener {

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
        UserIds friendIds =
                this.relationStorage
                        .getFriendIds(userId)
                        .orElseThrow(
                                () ->
                                        new NotFoundAnyFriendIdsException(
                                                "No friends found for userId: "
                                                        + request.getSelfInfoUpdateEvent()
                                                                .getUserId()));

        EventData.Builder b =
                EventData.newBuilder()
                        .setSendUserId(userId)
                        .setEventType(EventType.EVENT_TYPE_SELF_INFO_UPDATE)
                        .setContent(request.toByteString());
        for (long friendUserId : friendIds.getUserIdsList()) {
            b.addReceiveUserIds(friendUserId);
        }
        b.addReceiveUserIds(userId); // also notify self for multi-device sync
        MessagePayload messageData = MessagePayload.newBuilder().setEventData(b.build()).build();
        this.executorService.submit(
                () -> {
                    this.producer.send(this.topicName, String.valueOf(userId), messageData);
                });
        return SendEventResp.newBuilder().build();
    }
}
