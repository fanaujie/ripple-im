package com.fanaujie.ripple.msgdispatcher.consumer.processor;

import com.fanaujie.ripple.communication.processor.Processor;
import com.fanaujie.ripple.protobuf.msgapiserver.SendMessageReq;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessageData;
import com.fanaujie.ripple.storage.repository.ConversationRepository;
import com.fanaujie.ripple.storage.utils.ConversationUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static com.fanaujie.ripple.protobuf.msgapiserver.SendMessageReq.MessageCase.SINGLE_MESSAGE_CONTENT;

public class SingleMessagePayloadProcessor implements Processor<MessageData, Void> {

    private final ExecutorService executor;
    private final ConversationRepository conversationRepository;

    public SingleMessagePayloadProcessor(
            ExecutorService executor, ConversationRepository conversationRepository) {
        this.executor = executor;
        this.conversationRepository = conversationRepository;
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
        if (!this.conversationRepository.existsById(
                sendMessageReq.getConversationId(), sendMessageReq.getSenderId())) {
            futures.add(
                    this.executor.submit(
                            () ->
                                    this.conversationRepository.createSingeMessageConversation(
                                            sendMessageReq.getConversationId(),
                                            sendMessageReq.getSenderId(),
                                            sendMessageReq.getReceiverId())));
        }
        if (!this.conversationRepository.existsById(
                sendMessageReq.getConversationId(), sendMessageReq.getReceiverId())) {
            futures.add(
                    this.executor.submit(
                            () ->
                                    this.conversationRepository.createSingeMessageConversation(
                                            sendMessageReq.getConversationId(),
                                            sendMessageReq.getReceiverId(),
                                            sendMessageReq.getSenderId())));
        }
        futures.add(
                this.executor.submit(
                        () ->
                                this.conversationRepository.updateSingeMessageConversation(
                                        sendMessageReq.getConversationId(),
                                        sendMessageReq.getSenderId(),
                                        sendMessageReq.getReceiverId(),
                                        sendMessageReq.getMessageId(),
                                        sendMessageReq.getSendTimestamp(),
                                        sendMessageReq.getSingleMessageContent())));
        futures.add(
                this.executor.submit(
                        () ->
                                this.conversationRepository.updateSingeMessageConversation(
                                        sendMessageReq.getConversationId(),
                                        sendMessageReq.getReceiverId(),
                                        sendMessageReq.getSenderId(),
                                        sendMessageReq.getMessageId(),
                                        sendMessageReq.getSendTimestamp(),
                                        sendMessageReq.getSingleMessageContent())));
        futures.add(
                this.executor.submit(
                        () ->
                                this.conversationRepository.saveMessage(
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
