package com.fanaujie.ripple.msgdispatcher.consumer;

import com.fanaujie.ripple.communication.msgqueue.GenericProducer;
import com.fanaujie.ripple.communication.msgqueue.uitls.MessageConverter;
import com.fanaujie.ripple.communication.processor.ProcessorDispatcher;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq;
import com.fanaujie.ripple.protobuf.msgdispatcher.EventData;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;
import com.fanaujie.ripple.protobuf.push.PushMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

public class DefaultEventPayloadRouter implements PayloadRouter<MessagePayload> {
    private final Logger logger = LoggerFactory.getLogger(DefaultEventPayloadRouter.class);
    private final String pushTopic;
    private final GenericProducer<String, PushMessage> pushProducer;
    private final ProcessorDispatcher<SendEventReq.EventCase, EventData, Void> dispatcher;
    private final ExecutorService executorService;

    public DefaultEventPayloadRouter(
            String pushTopic,
            GenericProducer<String, PushMessage> pushProducer,
            ProcessorDispatcher<SendEventReq.EventCase, EventData, Void> dispatcher,
            ExecutorService executorService) {
        this.pushTopic = pushTopic;
        this.pushProducer = pushProducer;
        this.dispatcher = dispatcher;
        this.executorService = executorService;
    }

    @Override
    public void handle(String key, MessagePayload data) throws Exception {
        if (!data.hasEventData()) {
            logger.error("process: MessagePayload does not contain EventData for key: {}", key);
            throw new IllegalArgumentException("MessagePayload does not contain EventData");
        }
        EventData eventData = data.getEventData();
        switch (data.getPayloadCase()) {
            case EVENT_DATA:
                this.dispatcher.dispatch(eventData);
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
                // Handle other payload types if necessary
                logger.info("process: Received MESSAGE_DATA payload for key: {}", key);
                break;
            default:
                logger.error("process: Unknown payload case: {}", data.getPayloadCase());
                throw new IllegalArgumentException(
                        "Unknown payload case: " + data.getPayloadCase());
        }
    }
}
