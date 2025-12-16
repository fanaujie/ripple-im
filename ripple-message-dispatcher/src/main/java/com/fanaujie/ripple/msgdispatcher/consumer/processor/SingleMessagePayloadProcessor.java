package com.fanaujie.ripple.msgdispatcher.consumer.processor;

import com.fanaujie.ripple.communication.msgqueue.GenericProducer;
import com.fanaujie.ripple.communication.msgqueue.uitls.MessageConverter;
import com.fanaujie.ripple.communication.processor.Processor;
import com.fanaujie.ripple.protobuf.msgapiserver.SendMessageReq;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessageData;
import com.fanaujie.ripple.protobuf.push.PushMessage;
import com.fanaujie.ripple.storage.exception.NotFoundUserProfileException;
import com.fanaujie.ripple.storage.service.ConversationStateFacade;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

import static com.fanaujie.ripple.protobuf.msgapiserver.SendMessageReq.MessageCase.SINGLE_MESSAGE_CONTENT;

public class SingleMessagePayloadProcessor implements Processor<MessageData, Void> {

    private final Logger logger = LoggerFactory.getLogger(SingleMessagePayloadProcessor.class);
    private final RippleStorageFacade storageFacade;
    private final ConversationStateFacade conversationStorage;
    private final GenericProducer<String, PushMessage> pushMessageGenericProducer;
    private final String pushTopic;

    public SingleMessagePayloadProcessor(
            RippleStorageFacade storageFacade,
            ConversationStateFacade conversationStorage,
            GenericProducer<String, PushMessage> pushMessageProducer,
            String pushTopic) {
        this.storageFacade = storageFacade;
        this.conversationStorage = conversationStorage;
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
                sendMessageReq.getSingleMessageContent());

        conversationStorage.updateConversation(
                sendMessageReq.getReceiverId(),
                sendMessageReq.getConversationId(),
                sendMessageReq.getSingleMessageContent().getText(),
                sendMessageReq.getSendTimestamp(),
                String.valueOf(sendMessageReq.getMessageId()),
                sendMessageReq.getSenderId() != sendMessageReq.getReceiverId());
    }

    private void updateGroupConversationStorage(
            SendMessageReq sendMessageReq, MessageData messageData) throws Exception {
        long senderId = sendMessageReq.getSenderId();
        long groupId = sendMessageReq.getGroupId();
        String conversationId = sendMessageReq.getConversationId();
        String messageText = sendMessageReq.getSingleMessageContent().getText();
        long timestamp = sendMessageReq.getSendTimestamp();
        String messageId = String.valueOf(sendMessageReq.getMessageId());

        this.storageFacade.saveGroupTextMessage(
                conversationId,
                sendMessageReq.getMessageId(),
                senderId,
                groupId,
                timestamp,
                sendMessageReq.getSingleMessageContent());

        List<Long> allRecipients = messageData.getReceiveUserIdsList();

        List<Long> recipientsForUnread =
                allRecipients.stream()
                        .filter(userId -> userId != senderId)
                        .collect(Collectors.toList());

        conversationStorage.batchUpdateConversation(
                recipientsForUnread,
                conversationId,
                messageText,
                timestamp,
                messageId,
                true); // incrementUnread = true for recipients

        conversationStorage.updateConversation(
                senderId,
                conversationId,
                messageText,
                timestamp,
                messageId,
                false); // incrementUnread = false for sender
    }
}
