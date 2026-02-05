package com.fanaujie.ripple.webhookservice.service;

import com.fanaujie.ripple.cache.service.BotConfigStorage;
import com.fanaujie.ripple.communication.gateway.GatewayPusher;
import com.fanaujie.ripple.protobuf.msgdispatcher.BotWebhookEvent;
import com.fanaujie.ripple.protobuf.push.SSEEventType;
import com.fanaujie.ripple.snowflakeid.client.SnowflakeIdClient;
import com.fanaujie.ripple.storage.model.BotConfig;
import com.fanaujie.ripple.storage.model.BotResponseMode;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import com.fanaujie.ripple.webhookservice.http.WebhookHttpClient;
import com.fanaujie.ripple.webhookservice.model.SSEEvent;
import com.fanaujie.ripple.webhookservice.model.WebhookRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

public class WebhookDispatcherService {
    private static final Logger logger = LoggerFactory.getLogger(WebhookDispatcherService.class);

    private final WebhookHttpClient httpClient;
    private final RippleStorageFacade storageFacade;
    private final GatewayPusher gatewayPusher;
    private final BotConfigStorage botConfigStorage;
    private final SnowflakeIdClient snowflakeIdClient;

    public WebhookDispatcherService(
            WebhookHttpClient httpClient,
            RippleStorageFacade storageFacade,
            GatewayPusher gatewayPusher,
            BotConfigStorage botConfigStorage,
            SnowflakeIdClient snowflakeIdClient) {
        this.httpClient = httpClient;
        this.storageFacade = storageFacade;
        this.gatewayPusher = gatewayPusher;
        this.botConfigStorage = botConfigStorage;
        this.snowflakeIdClient = snowflakeIdClient;
    }

    public void dispatch(BotWebhookEvent botMessage) {
        long senderId = botMessage.getSenderUserId();
        long botId = botMessage.getBotUserId();
        String conversationId = botMessage.getConversationId();
        long originalMessageId = botMessage.getMessageId();

        // Generate Snowflake ID for the bot response message upfront
        // so all SSE events (delta, done, error) and storage use the same ID
        long responseMessageId;
        try {
            responseMessageId = snowflakeIdClient.requestSnowflakeId().get().getId();
        } catch (Exception e) {
            logger.error("Failed to generate Snowflake ID for bot response: {}", e.getMessage());
            pushSSEToUser(
                    SSEEventType.SSE_EVENT_TYPE_ERROR,
                    senderId,
                    botId,
                    conversationId,
                    "Bot is currently unavailable",
                    0);
            return;
        }

        // Look up bot config to get webhook_url, api_key, response_mode
        BotConfig botConfig;
        try {
            botConfig = botConfigStorage.get(botId);
        } catch (Exception e) {
            logger.error("Failed to look up bot config for botId={}: {}", botId, e.getMessage());
            pushSSEToUser(
                    SSEEventType.SSE_EVENT_TYPE_ERROR,
                    senderId,
                    botId,
                    conversationId,
                    "Bot is currently unavailable",
                    0);
            return;
        }
        if (botConfig == null) {
            logger.warn("Bot config not found for botId={}, skipping dispatch", botId);
            return;
        }

        String webhookUrl = botConfig.getWebhookUrl();
        String apiKey = botConfig.getApiKey() != null ? botConfig.getApiKey() : "";
        BotResponseMode responseMode = botConfig.getResponseModeOrDefault();

        WebhookRequest request =
                WebhookRequest.create(
                        originalMessageId,
                        botMessage.getSessionId(),
                        String.valueOf(senderId),
                        botMessage.getMessageText(),
                        botMessage.getSendTimestamp());

        logger.info(
                "Dispatching to webhook: url={}, messageId={}, responseMessageId={}, responseMode={}",
                webhookUrl,
                originalMessageId,
                responseMessageId,
                responseMode);

        CompletableFuture<String> future;

        if (responseMode == BotResponseMode.BATCH) {
            future = httpClient.sendPlain(webhookUrl, apiKey, request);
        } else {
            StringBuilder accumulatedResponse = new StringBuilder();
            future = httpClient.sendWithSSE(
                    webhookUrl,
                    apiKey,
                    request,
                    event ->
                            handleSSEEvent(
                                    event,
                                    senderId,
                                    botId,
                                    conversationId,
                                    accumulatedResponse,
                                    responseMode,
                                    responseMessageId));
            // Fallback to accumulated if fullText is null
            future = future.thenApply(
                    fullText -> fullText != null ? fullText : accumulatedResponse.toString());
        }

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
                                responseMessageId);
                    } else {
                        // Save complete bot response to storage
                        saveBotResponse(
                                conversationId,
                                senderId,
                                botId,
                                fullText,
                                responseMessageId);
                    }
                });
    }

    private void handleSSEEvent(
            SSEEvent event,
            long userId,
            long botId,
            String conversationId,
            StringBuilder accumulated,
            BotResponseMode responseMode,
            long responseMessageId) {

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
                        responseMessageId);
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
            String conversationId, long userId, long botId, String responseText,
            long messageId) {
        long timestamp = Instant.now().toEpochMilli();

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
