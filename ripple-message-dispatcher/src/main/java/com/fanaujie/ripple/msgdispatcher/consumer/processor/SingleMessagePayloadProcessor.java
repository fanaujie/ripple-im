package com.fanaujie.ripple.msgdispatcher.consumer.processor;

import com.fanaujie.ripple.communication.processor.Processor;
import com.fanaujie.ripple.protobuf.msgapiserver.SendMessageReq;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessageData;
import com.fanaujie.ripple.storage.exception.NotFoundUserProfileException;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static com.fanaujie.ripple.protobuf.msgapiserver.SendMessageReq.MessageCase.SINGLE_MESSAGE_CONTENT;

public class SingleMessagePayloadProcessor implements Processor<MessageData, Void> {

    private final Logger logger = LoggerFactory.getLogger(SingleMessagePayloadProcessor.class);
    private final ExecutorService executor;
    private final RippleStorageFacade storageFacade;

    public SingleMessagePayloadProcessor(
            ExecutorService executor, RippleStorageFacade storageFacade) {
        this.executor = executor;
        this.storageFacade = storageFacade;
    }

    @Override
    public Void handle(MessageData messageData) throws Exception {
        SendMessageReq sendMessageReq = messageData.getData();
        if (sendMessageReq.getMessageCase() == SINGLE_MESSAGE_CONTENT) {
            this.updateConversationStorage(sendMessageReq);
            return null;
        }
        throw new IllegalArgumentException(
                "Unknown message type for SingleMessagePayloadProcessor");
    }

    private void updateConversationStorage(SendMessageReq sendMessageReq) throws Exception {
        List<Future<?>> futures = new ArrayList<>();
        if (!this.storageFacade.existsByConversationId(
                sendMessageReq.getConversationId(), sendMessageReq.getSenderId())) {
            futures.add(
                    this.executor.submit(
                            () -> {
                                try {
                                    this.storageFacade.createSingeMessageConversation(
                                            sendMessageReq.getConversationId(),
                                            sendMessageReq.getSenderId(),
                                            sendMessageReq.getReceiverId());
                                } catch (NotFoundUserProfileException e) {
                                    logger.warn(
                                            "Failed to create single message conversation: {}",
                                            e.getMessage());
                                }
                            }));
        }
        if (!this.storageFacade.existsByConversationId(
                sendMessageReq.getConversationId(), sendMessageReq.getReceiverId())) {
            futures.add(
                    this.executor.submit(
                            () -> {
                                try {
                                    this.storageFacade.createSingeMessageConversation(
                                            sendMessageReq.getConversationId(),
                                            sendMessageReq.getReceiverId(),
                                            sendMessageReq.getSenderId());
                                } catch (NotFoundUserProfileException e) {
                                    logger.warn(
                                            "Failed to create single message conversation: {}",
                                            e.getMessage());
                                }
                            }));
        }
        futures.add(
                this.executor.submit(
                        () ->
                                this.storageFacade.saveMessage(
                                        sendMessageReq.getConversationId(),
                                        sendMessageReq.getMessageId(),
                                        sendMessageReq.getSenderId(),
                                        sendMessageReq.getReceiverId(),
                                        0,
                                        sendMessageReq.getSendTimestamp(),
                                        sendMessageReq.getSingleMessageContent())));
        for (Future<?> f : futures) {
            f.get();
        }
    }
}
