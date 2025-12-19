package com.fanaujie.ripple.msgdispatcher.consumer.processor;

import com.fanaujie.ripple.communication.msgqueue.GenericProducer;
import com.fanaujie.ripple.communication.msgqueue.uitls.MessageConverter;
import com.fanaujie.ripple.communication.processor.Processor;
import com.fanaujie.ripple.protobuf.msgapiserver.SendMessageReq;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessageData;
import com.fanaujie.ripple.protobuf.push.PushMessage;
import com.fanaujie.ripple.storage.exception.NotFoundUserProfileException;
import com.fanaujie.ripple.cache.service.ConversationSummaryStorage;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.fanaujie.ripple.protobuf.msgapiserver.SendMessageReq.MessageCase.SINGLE_MESSAGE_CONTENT;

public class SingleMessagePayloadProcessor implements Processor<MessageData, Void> {

    private final Logger logger = LoggerFactory.getLogger(SingleMessagePayloadProcessor.class);
    private final RippleStorageFacade storageFacade;
    private final ConversationSummaryStorage cachingConversationStateFacade;
    private final GenericProducer<String, PushMessage> pushMessageGenericProducer;
    private final String pushTopic;

    public SingleMessagePayloadProcessor(
            RippleStorageFacade storageFacade,
            ConversationSummaryStorage cachingConversationStateFacade,
            GenericProducer<String, PushMessage> pushMessageProducer,
            String pushTopic) {
        this.storageFacade = storageFacade;
        this.cachingConversationStateFacade = cachingConversationStateFacade;
        this.pushMessageGenericProducer = pushMessageProducer;
        this.pushTopic = pushTopic;
    }

    @Override
    public Void handle(MessageData messageData) throws Exception {
        SendMessageReq sendMessageReq = messageData.getData();
        if (sendMessageReq.getMessageCase() == SINGLE_MESSAGE_CONTENT) {
            long groupId = sendMessageReq.getGroupId();
            if (groupId > 0) {
                this.updateGroupConversationStorage(sendMessageReq, messageData);
            } else {
                this.updateSingleConversationStorage(sendMessageReq);
            }
            this.pushMessageGenericProducer.send(
                    this.pushTopic,
                    String.valueOf(messageData.getSendUserId()),
                    MessageConverter.toPushMessage(messageData));
            return null;
        }
        throw new IllegalArgumentException(
                "Unknown message type for SingleMessagePayloadProcessor {}");
    }

    private void updateSingleConversationStorage(SendMessageReq sendMessageReq) throws Exception {
        if (!this.storageFacade.existsByConversationId(
                sendMessageReq.getConversationId(), sendMessageReq.getSenderId())) {

            try {
                this.storageFacade.createSingeMessageConversation(
                        sendMessageReq.getConversationId(),
                        sendMessageReq.getSenderId(),
                        sendMessageReq.getReceiverId());
            } catch (NotFoundUserProfileException e) {
                logger.warn("Failed to create single message conversation: {}", e.getMessage());
            }
        }
        if (!this.storageFacade.existsByConversationId(
                sendMessageReq.getConversationId(), sendMessageReq.getReceiverId())) {

            try {
                this.storageFacade.createSingeMessageConversation(
                        sendMessageReq.getConversationId(),
                        sendMessageReq.getReceiverId(),
                        sendMessageReq.getSenderId());
            } catch (NotFoundUserProfileException e) {
                logger.warn("Failed to create single message conversation: {}", e.getMessage());
            }
        }
        this.storageFacade.saveTextMessage(
                sendMessageReq.getConversationId(),
                sendMessageReq.getMessageId(),
                sendMessageReq.getSenderId(),
                sendMessageReq.getReceiverId(),
                sendMessageReq.getSendTimestamp(),
                sendMessageReq.getSingleMessageContent().getText(),
                sendMessageReq.getSingleMessageContent().getFileUrl(),
                sendMessageReq.getSingleMessageContent().getFileName());

        cachingConversationStateFacade.updateConversationSummary(
                sendMessageReq.getReceiverId(),
                sendMessageReq.getConversationId(),
                sendMessageReq.getSingleMessageContent().getText(),
                sendMessageReq.getSendTimestamp(),
                sendMessageReq.getMessageId());
    }

    private void updateGroupConversationStorage(
            SendMessageReq sendMessageReq, MessageData messageData) throws Exception {
        long senderId = sendMessageReq.getSenderId();
        long groupId = sendMessageReq.getGroupId();
        String conversationId = sendMessageReq.getConversationId();
        long timestamp = sendMessageReq.getSendTimestamp();
        this.storageFacade.saveGroupTextMessage(
                conversationId,
                sendMessageReq.getMessageId(),
                senderId,
                groupId,
                timestamp,
                sendMessageReq.getSingleMessageContent().getText(),
                sendMessageReq.getSingleMessageContent().getFileUrl(),
                sendMessageReq.getSingleMessageContent().getFileName());

        List<Long> allReceiversId = messageData.getReceiveUserIdsList();
        cachingConversationStateFacade.updateGroupConversationSummary(
                senderId,
                allReceiversId,
                conversationId,
                sendMessageReq.getSingleMessageContent().getText(),
                timestamp,
                sendMessageReq.getMessageId());
    }
}
