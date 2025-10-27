package com.fanaujie.ripple.msgdispatcher.processor.impl;

import com.fanaujie.ripple.communication.messaging.GenericProducer;
import com.fanaujie.ripple.msgdispatcher.processor.Processor;
import com.fanaujie.ripple.protobuf.msgdispatcher.EventData;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventDataProcessor implements Processor<MessagePayload> {
    private final Logger logger = LoggerFactory.getLogger(EventDataProcessor.class);
    private final String pushTopic;
    private final GenericProducer<String, MessagePayload> pushProducer;

    public EventDataProcessor(
            String pushTopic, GenericProducer<String, MessagePayload> pushProducer) {
        this.pushTopic = pushTopic;
        this.pushProducer = pushProducer;
    }

    @Override
    public void process(String key, MessagePayload data) {
        logger.debug("process: Processing message with key: {}", key);

        if (!data.hasEventData()) {
            logger.error("process: MessagePayload does not contain EventData for key: {}", key);
            throw new IllegalArgumentException("MessagePayload does not contain EventData");
        }

        EventData eventData = data.getEventData();
        logger.debug(
                "process: Event type: {}, Send user ID: {}, Receive users count: {}",
                eventData.getEventType(),
                eventData.getSendUserId(),
                eventData.getReceiveUserIdsCount());

        switch (eventData.getEventType()) {
            case EVENT_TYPE_SELF_INFO_UPDATE:
                logger.debug("process: Processing SELF_INFO_UPDATE event for key: {}", key);
                this.pushProducer.send(this.pushTopic, key, data);
                logger.debug(
                        "process: SELF_INFO_UPDATE message sent to topic: {} with key: {}",
                        this.pushTopic,
                        key);
                break;
            case EVENT_TYPE_RELATION_UPDATE:
                logger.debug("process: Processing RELATION_UPDATE event for key: {}", key);
                this.pushProducer.send(this.pushTopic, key, data);
                logger.debug(
                        "process: RELATION_UPDATE message sent to topic: {} with key: {}",
                        this.pushTopic,
                        key);
                break;
            default:
                logger.error(
                        "process: Unknown event type: {} for key: {}",
                        eventData.getEventType(),
                        key);
                throw new IllegalArgumentException(
                        "Unknown event type: " + eventData.getEventType());
        }
    }
}
