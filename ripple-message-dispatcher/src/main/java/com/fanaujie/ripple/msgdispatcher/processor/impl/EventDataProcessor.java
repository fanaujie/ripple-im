package com.fanaujie.ripple.msgdispatcher.processor.impl;

import com.fanaujie.ripple.communication.messaging.GenericProducer;
import com.fanaujie.ripple.msgdispatcher.processor.Processor;
import com.fanaujie.ripple.protobuf.msgdispatcher.EventData;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;

public class EventDataProcessor implements Processor<MessagePayload> {
    private final String pushTopic;
    private final GenericProducer<String, MessagePayload> pushProducer;

    public EventDataProcessor(
            String pushTopic, GenericProducer<String, MessagePayload> pushProducer) {
        this.pushTopic = pushTopic;
        this.pushProducer = pushProducer;
    }

    @Override
    public void process(String key, MessagePayload data) {

        switch (EventData.getDefaultInstance().getEventType()) {
            case EVENT_TYPE_SELF_INFO_UPDATE:
            case EVENT_TYPE_RELATION:
                this.pushProducer.send(this.pushTopic, key, data);
                break;
            default:
                throw new IllegalArgumentException("Unknown event type");
        }
    }
}
