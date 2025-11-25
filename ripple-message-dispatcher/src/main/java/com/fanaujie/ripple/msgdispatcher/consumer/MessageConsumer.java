package com.fanaujie.ripple.msgdispatcher.consumer;

import com.fanaujie.ripple.communication.msgqueue.MessageRecord;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;

import java.util.List;

public class MessageConsumer {

    private final PayloadRouter<MessagePayload> payloadRouter;

    public MessageConsumer(PayloadRouter<MessagePayload> payloadRouter) {
        this.payloadRouter = payloadRouter;
    }

    public void consumeBatch(List<MessageRecord<String, MessagePayload>> records) throws Exception {
        for (MessageRecord<String, MessagePayload> record : records) {
            consume(record.key(), record.value());
        }
    }

    public void consume(String key, MessagePayload payload) throws Exception {
        switch (payload.getPayloadCase()) {
            case EVENT_DATA:
            case MESSAGE_DATA:
                this.payloadRouter.handle(key, payload);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unsupported payload type: " + payload.getPayloadCase());
        }
    }
}
