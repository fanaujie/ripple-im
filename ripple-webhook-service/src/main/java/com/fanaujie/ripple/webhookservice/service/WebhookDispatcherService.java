package com.fanaujie.ripple.webhookservice.service;

import com.fanaujie.ripple.communication.gateway.GatewayPusher;
import com.fanaujie.ripple.protobuf.msgdispatcher.BotMessageData;
import com.fanaujie.ripple.protobuf.push.SSEEventType;
import com.fanaujie.ripple.storage.model.BotResponseMode;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import com.fanaujie.ripple.webhookservice.http.WebhookHttpClient;
import com.fanaujie.ripple.webhookservice.model.SSEEvent;
import com.fanaujie.ripple.webhookservice.model.WebhookRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

public class WebhookDispatcherService {
    private static final Logger logger = LoggerFactory.getLogger(WebhookDispatcherService.class);

    private final WebhookHttpClient httpClient;
    private final RippleStorageFacade storageFacade;
    private final GatewayPusher gatewayPusher;
    private final AtomicLong responseMessageIdCounter;

    public WebhookDispatcherService(
            WebhookHttpClient httpClient,
            RippleStorageFacade storageFacade,
            GatewayPusher gatewayPusher) {
        this.httpClient = httpClient;
        this.storageFacade = storageFacade;
        this.gatewayPusher = gatewayPusher;
        // Simple counter for generating unique message IDs for bot responses
        // In production, use Snowflake ID service
        this.responseMessageIdCounter = new AtomicLong(Instant.now().toEpochMilli());
    }

    public void dispatch(BotMessageData botMessage) {
        long senderId = botMessage.getSenderUserId();
        long botId = botMessage.getBotUserId();
        String conversationId = botMessage.getConversationId();
        long originalMessageId = botMessage.getMessageId();

        // Get response mode from message data (passed from message-dispatcher)
        BotResponseMode responseMode = parseResponseMode(botMessage.getResponseMode());

        WebhookRequest request =
                WebhookRequest.create(
                        originalMessageId,
                        botMessage.getSessionId(),
                        String.valueOf(senderId),
                        botMessage.getMessageText(),
                        botMessage.getSendTimestamp());

        logger.info(
                "Dispatching to webhook: url={}, messageId={}, responseMode={}",
                botMessage.getWebhookUrl(),
                originalMessageId,
                responseMode);

        StringBuilder accumulatedResponse = new StringBuilder();

        CompletableFuture<String> future =
                httpClient.sendWithSSE(
                        botMessage.getWebhookUrl(),
                        botMessage.getApiKey(),
                        request,
                        event ->
                                handleSSEEvent(
                                        event,
                                        senderId,
                                        botId,
                                        conversationId,
                                        accumulatedResponse,
                                        responseMode));

        future.whenComplete(
                (fullText, error) -> {
                    if (error != null) {
                        logger.error(
                                "Webhook call failed for message {}: {}",
                                originalMessageId,
                                error.getMessage());
                        pushSSEToUser(
                                SSEEventType.SSE_EVENT_TYPE_ERROR,
                                senderId,
                                botId,
                                conversationId,
                                "Bot is currently unavailable",
                                0);
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

    private BotResponseMode parseResponseMode(String value) {
        if (value == null || value.isEmpty()) {
            return BotResponseMode.STREAMING;
        }
        try {
            return BotResponseMode.valueOf(value);
        } catch (IllegalArgumentException e) {
            return BotResponseMode.STREAMING;
        }
    }

    private void handleSSEEvent(
            SSEEvent event,
            long userId,
            long botId,
            String conversationId,
            StringBuilder accumulated,
            BotResponseMode responseMode) {

        if (event.isDelta()) {
            accumulated.append(event.getContent());
            // Only push delta in STREAMING mode
            if (responseMode == BotResponseMode.STREAMING) {
                pushSSEToUser(
                        SSEEventType.SSE_EVENT_TYPE_DELTA,
                        userId,
                        botId,
                        conversationId,
                        event.getContent(),
                        0);
            }
        } else if (event.isDone()) {
            logger.debug("Bot response complete for user {} from bot {}", userId, botId);
        } else if (event.isError()) {
            logger.error("Bot error: {}", event.getErrorMessage());
        }
    }

    private void pushSSEToUser(
            SSEEventType eventType,
            long userId,
            long botId,
            String conversationId,
            String content,
            long messageId) {
        try {
            gatewayPusher.pushSSE(
                    userId,
                    botId,
                    conversationId,
                    eventType,
                    content,
                    messageId,
                    Instant.now().toEpochMilli());
        } catch (Exception e) {
            logger.error("Failed to push SSE {} to user {}: {}", eventType, userId, e.getMessage());
        }
    }

    private void saveBotResponse(
            String conversationId, long userId, long botId, String responseText) {
        long timestamp = Instant.now().toEpochMilli();
        long messageId = responseMessageIdCounter.incrementAndGet();

        try {
            // Save bot's response as a message from bot to user
            storageFacade.saveTextMessage(
                    conversationId,
                    messageId,
                    botId, // sender is the bot
                    userId, // receiver is the user
                    timestamp,
                    responseText,
                    null, // no file URL
                    null); // no file name

            logger.info(
                    "Saved bot response: conversationId={}, messageId={}, botId={}",
                    conversationId,
                    messageId,
                    botId);

            // Push SSE DONE with the final message content and message_id
            // This is pushed in both STREAMING and BATCH modes
            pushSSEToUser(
                    SSEEventType.SSE_EVENT_TYPE_DONE,
                    userId,
                    botId,
                    conversationId,
                    responseText,
                    messageId);

        } catch (Exception e) {
            logger.error("Failed to save bot response: {}", e.getMessage(), e);
        }
    }
}
