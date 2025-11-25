package com.fanaujie.ripple.msgdispatcher.consumer;

import com.fanaujie.ripple.communication.msgqueue.GenericProducer;
import com.fanaujie.ripple.communication.msgqueue.uitls.MessageConverter;
import com.fanaujie.ripple.communication.processor.ProcessorDispatcher;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq;
import com.fanaujie.ripple.protobuf.msgapiserver.SendMessageReq;
import com.fanaujie.ripple.protobuf.msgdispatcher.EventData;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessageData;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;
import com.fanaujie.ripple.protobuf.push.PushMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

public class DefaultPayloadRouter implements PayloadRouter<MessagePayload> {
    private final Logger logger = LoggerFactory.getLogger(DefaultPayloadRouter.class);
    private final String pushTopic;
    private final GenericProducer<String, PushMessage> pushProducer;
    private final ProcessorDispatcher<SendEventReq.EventCase, EventData, Void> eventDispatcher;
    private final ProcessorDispatcher<SendMessageReq.MessageCase, MessageData, Void>
            messageDispatcher;
    private final ExecutorService executorService;

    public DefaultPayloadRouter(
            String pushTopic,
            GenericProducer<String, PushMessage> pushProducer,
            ProcessorDispatcher<SendEventReq.EventCase, EventData, Void> eventDispatcher,
            ProcessorDispatcher<SendMessageReq.MessageCase, MessageData, Void> messageDispatcher,
            ExecutorService executorService) {
        this.pushTopic = pushTopic;
        this.pushProducer = pushProducer;
        this.eventDispatcher = eventDispatcher;
        this.messageDispatcher = messageDispatcher;
        this.executorService = executorService;
    }

    @Override
    public void handle(String key, MessagePayload data) throws Exception {
        switch (data.getPayloadCase()) {
            case EVENT_DATA:
                EventData eventData = data.getEventData();
                this.eventDispatcher.dispatch(eventData.getData().getEventCase(), eventData);
                // Submit push producer send task asynchronously
                this.executorService.submit(
                        () -> {
                            try {
                                this.pushProducer.send(
                                        this.pushTopic,
                                        key,
                                        MessageConverter.toPushMessage(eventData));
                            } catch (Exception e) {
                                logger.error(
                                        "process: Failed to send message to push topic for key: {}",
                                        key,
                                        e);
                            }
                        });
                break;
            case MESSAGE_DATA:
                MessageData messageData = data.getMessageData();
                this.messageDispatcher.dispatch(
                        messageData.getData().getMessageCase(), messageData);
                this.executorService.submit(
                        () -> {
                            try {
                                this.pushProducer.send(
                                        this.pushTopic,
                                        key,
                                        MessageConverter.toPushMessage(messageData));
                            } catch (Exception e) {
                                logger.error(
                                        "process: Failed to send message to push topic for key: {}",
                                        key,
                                        e);
                            }
                        });
                break;
            default:
                logger.error("process: Unknown payload case: {}", data.getPayloadCase());
                throw new IllegalArgumentException(
                        "Unknown payload case: " + data.getPayloadCase());
        }
    }
}
