package com.fanaujie.ripple.msgdispatcher.consumer;

import com.fanaujie.ripple.communication.msgqueue.KeyedPayloadHandler;
import com.fanaujie.ripple.communication.processor.ProcessorDispatcher;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq;
import com.fanaujie.ripple.protobuf.msgapiserver.SendGroupCommandReq;
import com.fanaujie.ripple.protobuf.msgapiserver.SendMessageReq;
import com.fanaujie.ripple.protobuf.msgdispatcher.EventData;
import com.fanaujie.ripple.protobuf.msgdispatcher.GroupCommandData;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessageData;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

public class DefaultKeyedPayloadHandler implements KeyedPayloadHandler<MessagePayload> {
    private final Logger logger = LoggerFactory.getLogger(DefaultKeyedPayloadHandler.class);
    private final ProcessorDispatcher<SendEventReq.EventCase, EventData, Void> eventDispatcher;
    private final ProcessorDispatcher<SendMessageReq.MessageCase, MessageData, Void>
            messageDispatcher;
    private final ProcessorDispatcher<
                    SendGroupCommandReq.CommandContentCase, GroupCommandData, Void>
            groupCommandDispatcher;

    public DefaultKeyedPayloadHandler(
            ProcessorDispatcher<SendEventReq.EventCase, EventData, Void> eventDispatcher,
            ProcessorDispatcher<SendMessageReq.MessageCase, MessageData, Void> messageDispatcher,
            ProcessorDispatcher<SendGroupCommandReq.CommandContentCase, GroupCommandData, Void>
                    groupCommandDispatcher) {

        this.eventDispatcher = eventDispatcher;
        this.messageDispatcher = messageDispatcher;
        this.groupCommandDispatcher = groupCommandDispatcher;
    }

    @Override
    public void handle(String key, MessagePayload data) throws Exception {
        switch (data.getPayloadCase()) {
            case EVENT_DATA:
                EventData eventData = data.getEventData();
                this.eventDispatcher.dispatch(eventData.getData().getEventCase(), eventData);
                break;
            case MESSAGE_DATA:
                MessageData messageData = data.getMessageData();
                this.messageDispatcher.dispatch(
                        messageData.getData().getMessageCase(), messageData);
                break;
            case GROUP_COMMAND_DATA:
                GroupCommandData groupCommandData = data.getGroupCommandData();
                this.groupCommandDispatcher.dispatch(
                        groupCommandData.getData().getCommandContentCase(), groupCommandData);
                break;
            default:
                logger.error("process: Unknown payload case: {}", data.getPayloadCase());
                throw new IllegalArgumentException(
                        "Unknown payload case: " + data.getPayloadCase());
        }
    }
}
