package com.fanaujie.ripple.msgdispatcher.consumer.processor;

import com.fanaujie.ripple.communication.msgqueue.GenericProducer;
import com.fanaujie.ripple.communication.msgqueue.uitls.MessageConverter;
import com.fanaujie.ripple.communication.processor.Processor;
import com.fanaujie.ripple.protobuf.msgapiserver.SendMessageReq;
import com.fanaujie.ripple.protobuf.msgdispatcher.BotWebhookEvent;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessageData;
import com.fanaujie.ripple.protobuf.push.PushMessage;
import com.fanaujie.ripple.storage.exception.NotFoundUserProfileException;
import com.fanaujie.ripple.storage.model.Conversation;
import com.fanaujie.ripple.cache.service.BotConfigStorage;
import com.fanaujie.ripple.cache.service.ConversationSummaryStorage;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.fanaujie.ripple.protobuf.msgapiserver.SendMessageReq.MessageCase.SINGLE_MESSAGE_CONTENT;

public class SingleMessagePayloadProcessor implements Processor<MessageData, Void> {

    private final Logger logger = LoggerFactory.getLogger(SingleMessagePayloadProcessor.class);
    private final RippleStorageFacade storageFacade;
    private final BotConfigStorage botConfigStorage;
    private final ConversationSummaryStorage cachingConversationStateFacade;
    private final GenericProducer<String, PushMessage> pushMessageGenericProducer;
    private final GenericProducer<String, BotWebhookEvent> botWebhookProducer;
    private final String pushTopic;
    private final String botWebhookTopic;

    public SingleMessagePayloadProcessor(
            RippleStorageFacade storageFacade,
            BotConfigStorage botConfigStorage,
            ConversationSummaryStorage cachingConversationStateFacade,
            GenericProducer<String, PushMessage> pushMessageProducer,
            String pushTopic,
            GenericProducer<String, BotWebhookEvent> botWebhookProducer,
            String botWebhookTopic) {
        this.storageFacade = storageFacade;
        this.botConfigStorage = botConfigStorage;
        this.cachingConversationStateFacade = cachingConversationStateFacade;
        this.pushMessageGenericProducer = pushMessageProducer;
        this.pushTopic = pushTopic;
        this.botWebhookProducer = botWebhookProducer;
        this.botWebhookTopic = botWebhookTopic;
    }

    @Override
    public Void handle(MessageData messageData) throws Exception {
        SendMessageReq sendMessageReq = messageData.getData();
        if (sendMessageReq.getMessageCase() == SINGLE_MESSAGE_CONTENT) {
            long groupId = sendMessageReq.getGroupId();
            long botId = sendMessageReq.getBotId();
            if (groupId > 0) {
                this.updateGroupConversationStorage(sendMessageReq, messageData);
                this.pushMessageGenericProducer.send(
                        this.pushTopic,
                        String.valueOf(messageData.getSendUserId()),
                        MessageConverter.toPushMessage(messageData));
            } else if (botId > 0) {
                this.handleBotMessage(sendMessageReq, messageData);
            } else {
                this.updateSingleConversationStorage(sendMessageReq);
                this.pushMessageGenericProducer.send(
                        this.pushTopic,
                        String.valueOf(messageData.getSendUserId()),
                        MessageConverter.toPushMessage(messageData));
            }
            return null;
        }
        throw new IllegalArgumentException(
                "Unknown message type for SingleMessagePayloadProcessor {}");
    }

    private void handleBotMessage(SendMessageReq sendMessageReq, MessageData messageData)
            throws Exception {
        long senderId = sendMessageReq.getSenderId();
        long botId = sendMessageReq.getBotId();
        if (!this.botConfigStorage.isBot(botId)) {
            logger.warn("Bot {} not found", botId);
            return;
        }
        String providedSessionId = sendMessageReq.getSessionId();

        if (providedSessionId == null || providedSessionId.isEmpty()) {
            logger.warn(
                    "Bot message rejected: no sessionId provided. senderId={}, botId={}",
                    senderId,
                    botId);
            return;
        }

        String conversationId = sendMessageReq.getConversationId();

        // Create/update conversation and save message
        this.updateSingleConversationStorage(sendMessageReq);

        // Update bot session ID in conversation if it differs from the provided session ID
        Conversation conversation = this.storageFacade.getConversation(senderId, conversationId);
        if (conversation != null && !providedSessionId.equals(conversation.getBotSessionId())) {
            this.storageFacade.updateConversationBotSessionId(
                    senderId, conversationId, providedSessionId, sendMessageReq.getSendTimestamp());
            logger.debug(
                    "Updated bot session ID for conversation {}: {} -> {}",
                    conversationId,
                    conversation.getBotSessionId(),
                    providedSessionId);
        }

        // Push message to sender's other devices via push server
        this.pushMessageGenericProducer.send(
                this.pushTopic,
                String.valueOf(messageData.getSendUserId()),
                MessageConverter.toPushMessage(messageData));

        // Send to bot webhook topic for webhook dispatch
        if (this.botWebhookProducer != null && this.botWebhookTopic != null) {
            BotWebhookEvent botWebhookEvent =
                    BotWebhookEvent.newBuilder()
                            .setSenderUserId(senderId)
                            .setBotUserId(botId)
                            .setConversationId(conversationId)
                            .setMessageId(sendMessageReq.getMessageId())
                            .setSessionId(providedSessionId)
                            .setMessageText(sendMessageReq.getSingleMessageContent().getText())
                            .setSendTimestamp(sendMessageReq.getSendTimestamp())
                            .build();

            this.botWebhookProducer.send(
                    this.botWebhookTopic, String.valueOf(senderId), botWebhookEvent);
            logger.info(
                    "Dispatched message {} to bot webhook topic for bot {}",
                    sendMessageReq.getMessageId(),
                    botId);
        } else {
            logger.warn("Bot webhook producer not configured, cannot dispatch to bot {}", botId);
        }
    }

    private void updateSingleConversationStorage(SendMessageReq sendMessageReq) throws Exception {
        if (!this.storageFacade.existsByConversationId(
                sendMessageReq.getConversationId(), sendMessageReq.getSenderId())) {

            try {
                this.storageFacade.createSingeMessageConversation(
                        sendMessageReq.getConversationId(),
                        sendMessageReq.getSenderId(),
                        sendMessageReq.getReceiverId(),
                        sendMessageReq.getSendTimestamp());
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
                        sendMessageReq.getSenderId(),
                        sendMessageReq.getSendTimestamp());
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
