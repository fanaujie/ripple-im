package com.fanaujie.ripple.msgdispatcher.consumer.processor;

import com.fanaujie.ripple.communication.msgqueue.GenericProducer;
import com.fanaujie.ripple.communication.msgqueue.uitls.MessageConverter;
import com.fanaujie.ripple.communication.processor.Processor;
import com.fanaujie.ripple.protobuf.msgapiserver.SendMessageReq;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessageData;
import com.fanaujie.ripple.protobuf.push.PushMessage;
import com.fanaujie.ripple.storage.exception.NotFoundUserProfileException;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.fanaujie.ripple.protobuf.msgapiserver.SendMessageReq.MessageCase.SINGLE_MESSAGE_CONTENT;

public class SingleMessagePayloadProcessor implements Processor<MessageData, Void> {

    private final Logger logger = LoggerFactory.getLogger(SingleMessagePayloadProcessor.class);
    private final RippleStorageFacade storageFacade;
    private final GenericProducer<String, PushMessage> pushMessageGenericProducer;
    private final String pushTopic;

    public SingleMessagePayloadProcessor(
            RippleStorageFacade storageFacade,
            GenericProducer<String, PushMessage> pushMessageProducer,
            String pushTopic) {
        this.storageFacade = storageFacade;
        this.pushMessageGenericProducer = pushMessageProducer;
        this.pushTopic = pushTopic;
    }

    @Override
    public Void handle(MessageData messageData) throws Exception {
        SendMessageReq sendMessageReq = messageData.getData();
        if (sendMessageReq.getMessageCase() == SINGLE_MESSAGE_CONTENT) {
            this.updateConversationStorage(sendMessageReq);
            this.pushMessageGenericProducer.send(
                    this.pushTopic,
                    String.valueOf(messageData.getSendUserId()),
                    MessageConverter.toPushMessage(messageData));
            return null;
        }
        throw new IllegalArgumentException(
                "Unknown message type for SingleMessagePayloadProcessor {}");
    }

    private void updateConversationStorage(SendMessageReq sendMessageReq) throws Exception {
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
        this.storageFacade.saveTextSingleMessage(
                sendMessageReq.getConversationId(),
                sendMessageReq.getMessageId(),
                sendMessageReq.getSenderId(),
                sendMessageReq.getReceiverId(),
                sendMessageReq.getSendTimestamp(),
                sendMessageReq.getSingleMessageContent());
    }
}
