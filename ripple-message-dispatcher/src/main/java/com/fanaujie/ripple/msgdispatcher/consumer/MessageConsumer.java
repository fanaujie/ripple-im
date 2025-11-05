package com.fanaujie.ripple.msgdispatcher.consumer;

import com.fanaujie.ripple.communication.msgqueue.MessageRecord;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;

import java.util.List;

public class MessageConsumer {

    private final PayloadRouter<MessagePayload> eventPayloadProcessor;
    private final PayloadRouter<MessagePayload> messagePayloadProcessor;

    public MessageConsumer(
            PayloadRouter<MessagePayload> eventDataConsumer,
            PayloadRouter<MessagePayload> messageDataConsumer) {
        this.eventPayloadProcessor = eventDataConsumer;
        this.messagePayloadProcessor = messageDataConsumer;
    }

    public void consumeBatch(List<MessageRecord<String, MessagePayload>> records) throws Exception {
        for (MessageRecord<String, MessagePayload> record : records) {
            consume(record.key(), record.value());
        }
    }

    public void consume(String key, MessagePayload payload) throws Exception {
        switch (payload.getPayloadCase()) {
            case EVENT_DATA:
                this.eventPayloadProcessor.handle(key, payload);
                break;
            case MESSAGE_DATA:
                this.messagePayloadProcessor.handle(key, payload);
                break;
            case PAYLOAD_NOT_SET:
                break;
        }
    }
}
