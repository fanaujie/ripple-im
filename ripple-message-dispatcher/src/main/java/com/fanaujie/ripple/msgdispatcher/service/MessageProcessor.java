package com.fanaujie.ripple.msgdispatcher.service;

import com.fanaujie.ripple.msgdispatcher.processor.Processor;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;

public class MessageProcessor {

    private final Processor<MessagePayload> eventDataProcessor;
    private final Processor<MessagePayload> messageDataProcessor;

    public MessageProcessor(
            Processor<MessagePayload> eventDataProcessor,
            Processor<MessagePayload> messageDataProcessor) {
        this.eventDataProcessor = eventDataProcessor;
        this.messageDataProcessor = messageDataProcessor;
    }

    public void processMessage(String key, MessagePayload value) {
        switch (value.getPayloadCase()) {
            case EVENT_DATA:
                this.eventDataProcessor.process(key, value);
                break;
            case MESSAGE_DATA:
                this.messageDataProcessor.process(key, value);
                break;
            case PAYLOAD_NOT_SET:
                break;
        }
    }
}
