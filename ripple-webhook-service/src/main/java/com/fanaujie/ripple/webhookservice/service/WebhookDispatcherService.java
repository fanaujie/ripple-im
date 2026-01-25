package com.fanaujie.ripple.webhookservice.service;

import com.fanaujie.ripple.communication.msgqueue.GenericProducer;
import com.fanaujie.ripple.protobuf.msgdispatcher.BotMessageData;
import com.fanaujie.ripple.protobuf.push.PushMessage;
import com.fanaujie.ripple.protobuf.push.PushSSEData;
import com.fanaujie.ripple.protobuf.push.SSEEventType;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import com.fanaujie.ripple.webhookservice.http.WebhookHttpClient;
import com.fanaujie.ripple.webhookservice.model.SSEEvent;
import com.fanaujie.ripple.webhookservice.model.WebhookRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

public class WebhookDispatcherService {
    private static final Logger logger = LoggerFactory.getLogger(WebhookDispatcherService.class);

    private final WebhookHttpClient httpClient;
    private final RippleStorageFacade storageFacade;
    private final GenericProducer<String, PushMessage> pushProducer;
    private final String pushTopic;
    private final AtomicLong responseMessageIdCounter;

    public WebhookDispatcherService(
            WebhookHttpClient httpClient,
            RippleStorageFacade storageFacade,
            GenericProducer<String, PushMessage> pushProducer,
            String pushTopic) {
        this.httpClient = httpClient;
        this.storageFacade = storageFacade;
        this.pushProducer = pushProducer;
        this.pushTopic = pushTopic;
        // Simple counter for generating unique message IDs for bot responses
        // In production, use Snowflake ID service
        this.responseMessageIdCounter = new AtomicLong(System.currentTimeMillis());
    }

    public void dispatch(BotMessageData botMessage) {
        long senderId = botMessage.getSenderUserId();
        long botId = botMessage.getBotUserId();
        String conversationId = botMessage.getConversationId();
        long originalMessageId = botMessage.getMessageId();

        WebhookRequest request = WebhookRequest.create(
                originalMessageId,
                botMessage.getSessionId(),
                String.valueOf(senderId),
                botMessage.getMessageText(),
                botMessage.getSendTimestamp());

        logger.info("Dispatching to webhook: url={}, messageId={}",
                botMessage.getWebhookUrl(), originalMessageId);

        StringBuilder accumulatedResponse = new StringBuilder();

        CompletableFuture<String> future = httpClient.sendWithSSE(
                botMessage.getWebhookUrl(),
                botMessage.getApiKey(),
                request,
                event -> handleSSEEvent(event, senderId, botId, conversationId, accumulatedResponse));

        future.whenComplete((fullText, error) -> {
            if (error != null) {
                logger.error("Webhook call failed for message {}: {}",
                        originalMessageId, error.getMessage());
                pushSSEToUser(SSEEventType.SSE_EVENT_TYPE_ERROR, senderId, botId, conversationId,
                        "Bot is currently unavailable", 0);
            } else {
                // Save complete bot response to storage
                saveBotResponse(
                        conversationId,
                        senderId,
                        botId,
                        fullText != null ? fullText : accumulatedResponse.toString());
            }
        });
    }

    private void handleSSEEvent(
            SSEEvent event,
            long userId,
            long botId,
            String conversationId,
            StringBuilder accumulated) {

        if (event.isDelta()) {
            accumulated.append(event.getContent());
            // Push delta to user for real-time streaming effect
            pushSSEToUser(SSEEventType.SSE_EVENT_TYPE_DELTA, userId, botId, conversationId,
                    event.getContent(), 0);
        } else if (event.isDone()) {
            logger.debug("Bot response complete for user {} from bot {}", userId, botId);
        } else if (event.isError()) {
            logger.error("Bot error: {}", event.getErrorMessage());
        }
    }

    private void pushSSEToUser(SSEEventType eventType, long userId, long botId,
                               String conversationId, String content, long messageId) {
        PushSSEData sseData = PushSSEData.newBuilder()
                .setEventType(eventType)
                .setSendUserId(botId)
                .addReceiveUserIds(userId)
                .setConversationId(conversationId)
                .setContent(content)
                .setMessageId(messageId)
                .setSendTimestamp(System.currentTimeMillis())
                .build();

        PushMessage pushMessage = PushMessage.newBuilder()
                .setSseData(sseData)
                .build();

        try {
            pushProducer.send(pushTopic, String.valueOf(userId), pushMessage);
        } catch (Exception e) {
            logger.error("Failed to push SSE {} to user {}: {}", eventType, userId, e.getMessage());
        }
    }

    private void saveBotResponse(String conversationId, long userId, long botId, String responseText) {
        long timestamp = System.currentTimeMillis();
        long messageId = responseMessageIdCounter.incrementAndGet();

        try {
            // Save bot's response as a message from bot to user
            storageFacade.saveTextMessage(
                    conversationId,
                    messageId,
                    botId,       // sender is the bot
                    userId,      // receiver is the user
                    timestamp,
                    responseText,
                    null,        // no file URL
                    null);       // no file name

            logger.info("Saved bot response: conversationId={}, messageId={}, botId={}",
                    conversationId, messageId, botId);

            // Push SSE DONE with the final message content and message_id
            pushSSEToUser(SSEEventType.SSE_EVENT_TYPE_DONE, userId, botId, conversationId,
                    responseText, messageId);

        } catch (Exception e) {
            logger.error("Failed to save bot response: {}", e.getMessage(), e);
        }
    }
}
