package com.fanaujie.ripple.msgapiserver.listener.impl;

import com.fanaujie.ripple.communication.messaging.GenericProducer;
import com.fanaujie.ripple.msgapiserver.listener.EventListener;
import com.fanaujie.ripple.protobuf.msgapiserver.RelationEvent;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventResp;
import com.fanaujie.ripple.protobuf.msgdispatcher.EventData;
import com.fanaujie.ripple.protobuf.msgdispatcher.EventType;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;
import com.fanaujie.ripple.storage.service.RelationStorage;

import java.util.concurrent.ExecutorService;

public class RelationEventListener implements EventListener {

    private final String topicName;
    private final RelationStorage relationStorage;
    private final GenericProducer<String, MessagePayload> producer;
    private final ExecutorService executorService;

    public RelationEventListener(
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
        switch (request.getRelationEvent().getEventTypeValue()) {
            case RelationEvent.EventType.ADD_FRIEND_VALUE:
            case RelationEvent.EventType.REMOVE_FRIEND_VALUE:
            case RelationEvent.EventType.UPDATE_FRIEND_VALUE:
            case RelationEvent.EventType.BLOCK_USER_VALUE:
            case RelationEvent.EventType.UNBLOCK_USER_VALUE:
                long userId = request.getRelationEvent().getUserId();
                EventData.Builder b =
                        EventData.newBuilder()
                                .setSendUserId(userId)
                                .setContent(request.toByteString())
                                .setEventType(EventType.EVENT_TYPE_RELATION);
                b.addReceiveUserIds(userId); // notify self for multi-device sync
                MessagePayload messageData =
                        MessagePayload.newBuilder().setEventData(b.build()).build();
                this.executorService.submit(
                        () -> {
                            this.producer.send(this.topicName, String.valueOf(userId), messageData);
                        });
                return SendEventResp.newBuilder().build();
            default:
                throw new IllegalArgumentException("Unknown relation event type");
        }
    }
}
