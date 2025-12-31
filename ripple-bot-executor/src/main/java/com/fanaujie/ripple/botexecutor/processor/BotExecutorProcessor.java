package com.fanaujie.ripple.botexecutor.processor;

import com.fanaujie.ripple.botexecutor.config.BotExecutorConfig;
import com.fanaujie.ripple.botexecutor.model.PendingBotMessage;
import com.fanaujie.ripple.botexecutor.model.WebhookRequest;
import com.fanaujie.ripple.botexecutor.model.WebhookResponse;
import com.fanaujie.ripple.botexecutor.service.PendingMessageService;
import com.fanaujie.ripple.botexecutor.service.WebhookClient;
import com.fanaujie.ripple.botexecutor.sse.SseEventHandler;
import com.fanaujie.ripple.communication.msgqueue.GenericProducer;
import com.fanaujie.ripple.communication.processor.Processor;
import com.fanaujie.ripple.protobuf.msgapiserver.SendMessageReq;
import com.fanaujie.ripple.protobuf.msgapiserver.SingleMessageContent;
import com.fanaujie.ripple.protobuf.msgapiserver.UserType;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessageData;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;
import com.fanaujie.ripple.protobuf.push.BotAuthRequiredEvent;
import com.fanaujie.ripple.protobuf.push.PushMessage;
import com.fanaujie.ripple.protobuf.push.StreamingMessageChunk;
import com.fanaujie.ripple.storage.model.BotConfig;
import com.fanaujie.ripple.storage.model.BotInfo;
import com.fanaujie.ripple.storage.model.BotUserToken;
import com.fanaujie.ripple.storage.model.UserProfile;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Processor for executing bot webhooks with SSE streaming and managed auth support.
 */
public class BotExecutorProcessor implements Processor<MessageData, Void> {
    private final Logger logger = LoggerFactory.getLogger(BotExecutorProcessor.class);

    private final RippleStorageFacade storageFacade;
    private final GenericProducer<String, MessagePayload> messageProducer;
    private final GenericProducer<String, PushMessage> pushProducer;
    private final String messageTopic;
    private final String pushTopic;
    private final ExecutorService workerPool;
    private final WebhookClient webhookClient;
    private final PendingMessageService pendingMessageService;
    private final ObjectMapper objectMapper;
    private final BotExecutorConfig config;
    private final AtomicLong messageIdGenerator = new AtomicLong(System.currentTimeMillis());

    public BotExecutorProcessor(
            RippleStorageFacade storageFacade,
            GenericProducer<String, MessagePayload> messageProducer,
            GenericProducer<String, PushMessage> pushProducer,
            String messageTopic,
            String pushTopic,
            ExecutorService workerPool,
            PendingMessageService pendingMessageService,
            BotExecutorConfig config) {
        this.storageFacade = storageFacade;
        this.messageProducer = messageProducer;
        this.pushProducer = pushProducer;
        this.messageTopic = messageTopic;
        this.pushTopic = pushTopic;
        this.workerPool = workerPool;
        this.pendingMessageService = pendingMessageService;
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.webhookClient = new WebhookClient(objectMapper, config);
    }

    @Override
    public Void handle(MessageData messageData) throws Exception {
        if (messageData.getReceiveUserIdsCount() == 0) return null;
        long botId = messageData.getReceiveUserIds(0);

        // Get full bot info for name/avatar and config for webhook execution
        BotInfo botInfo = storageFacade.getBot(botId);
        if (botInfo == null || !botInfo.isEnabled()) {
            logger.warn("Bot {} not found or disabled", botId);
            return null;
        }

        // Async execution
        workerPool.submit(() -> {
            try {
                processMessage(botInfo, messageData);
            } catch (Exception e) {
                logger.error("Error processing bot message for bot {}", botId, e);
            }
        });

        return null;
    }

    private void processMessage(BotInfo bot, MessageData messageData) {
        long userId = messageData.getSendUserId();

        // Check authentication requirement
        if (bot.isRequireAuth()) {
            String authToken = checkAuthAndGetToken(bot, userId, messageData);
            if (authToken == null) {
                // Auth required, message queued, notification sent
                return;
            }
            executeWebhookWithAuth(bot, messageData, authToken);
        } else {
            executeWebhook(bot, messageData, null);
        }
    }

    /**
     * Check if user has valid auth token for this bot.
     * If not, queue message and send AUTH_REQUIRED event.
     * @return auth token if valid, null if auth required
     */
    private String checkAuthAndGetToken(BotInfo bot, long userId, MessageData messageData) {
        BotUserToken tokenRecord = storageFacade.getBotUserToken(bot.getBotId(), userId);

        if (tokenRecord != null) {
            // Check if token is expired
            if (tokenRecord.getExpiresAt() != null && tokenRecord.getExpiresAt().after(new Date())) {
                return tokenRecord.getAccessToken();
            }

            // Try to refresh if we have a refresh token
            if (tokenRecord.getRefreshToken() != null && !tokenRecord.getRefreshToken().isEmpty()) {
                String refreshedToken = tryRefreshToken(bot, tokenRecord);
                if (refreshedToken != null) {
                    return refreshedToken;
                }
            }
        }

        // No valid token, queue message and send auth required event
        queuePendingMessage(bot.getBotId(), userId, messageData);
        sendAuthRequiredEvent(bot, userId);
        return null;
    }

    private String tryRefreshToken(BotInfo bot, BotUserToken tokenRecord) {
        // Token refresh would require parsing auth_config and calling the token endpoint
        // For now, return null to trigger re-auth
        // TODO: Implement token refresh
        logger.info("Token expired for bot {} user {}, refresh not implemented", bot.getBotId(), tokenRecord.getUserId());
        return null;
    }

    private void queuePendingMessage(long botId, long userId, MessageData messageData) {
        PendingBotMessage pending = new PendingBotMessage(botId, userId, messageData.toByteArray());
        pendingMessageService.queueMessage(pending);
    }

    private void sendAuthRequiredEvent(BotInfo bot, long userId) {
        String authUrl = buildAuthUrl(bot);

        BotAuthRequiredEvent event = BotAuthRequiredEvent.newBuilder()
                .setBotId(bot.getBotId())
                .setUserId(userId)
                .setBotName(bot.getName() != null ? bot.getName() : "Bot")
                .setAuthUrl(authUrl)
                .build();

        PushMessage pushMessage = PushMessage.newBuilder()
                .setAuthRequired(event)
                .build();

        pushProducer.send(pushTopic, String.valueOf(userId), pushMessage);
        logger.info("Sent AUTH_REQUIRED event to user {} for bot {}", userId, bot.getBotId());
    }

    private String buildAuthUrl(BotInfo bot) {
        // Parse auth_config JSON to get auth_url
        // For now, return a placeholder that the Gateway will handle
        try {
            if (bot.getAuthConfig() != null && !bot.getAuthConfig().isEmpty()) {
                var configNode = objectMapper.readTree(bot.getAuthConfig());
                if (configNode.has("auth_url")) {
                    return configNode.get("auth_url").asText();
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to parse auth_config for bot {}", bot.getBotId(), e);
        }
        return "/api/bots/" + bot.getBotId() + "/oauth/authorize";
    }

    private void executeWebhookWithAuth(BotInfo bot, MessageData messageData, String authToken) {
        executeWebhook(bot, messageData, authToken);
    }

    private void executeWebhook(BotInfo bot, MessageData messageData, String authToken) {
        long userId = messageData.getSendUserId();
        String conversationId = messageData.getData().getConversationId();
        long messageId = messageData.getData().getMessageId();
        String text = messageData.getData().getSingleMessageContent().getText();

        // Get user profile for user_name
        String userName = getUserName(userId);

        WebhookRequest request = WebhookRequest.builder()
                .botId(bot.getBotId())
                .userId(userId)
                .userName(userName)
                .conversationId(conversationId)
                .messageId(messageId)
                .content(text)
                .history(Collections.emptyList()) // TODO: Add history support
                .build();

        // Generate message ID for bot reply
        long replyMessageId = generateMessageId();

        SseEventHandler sseHandler = new SseEventHandler() {
            @Override
            public void onChunk(String chunk) {
                sendStreamingChunk(bot.getBotId(), userId, conversationId, replyMessageId, chunk, false, null);
            }

            @Override
            public void onComplete(String fullContent) {
                sendStreamingChunk(bot.getBotId(), userId, conversationId, replyMessageId, null, true, fullContent);
                sendBotReplyMessage(bot.getBotId(), userId, conversationId, replyMessageId, fullContent);
            }

            @Override
            public void onError(Throwable error) {
                logger.error("SSE streaming error for bot {} user {}", bot.getBotId(), userId, error);
            }
        };

        WebhookResponse response = webhookClient.callWebhook(
                bot.getEndpoint(), bot.getSecret(), authToken, request, sseHandler);

        if (response.isSuccess() && !response.isStreaming()) {
            // Non-streaming response, send as regular message
            sendBotReplyMessage(bot.getBotId(), userId, conversationId, replyMessageId, response.getContent());
        } else if (!response.isSuccess()) {
            logger.error("Webhook failed for bot {}: {} - {}", bot.getBotId(), response.getStatusCode(), response.getErrorMessage());
        }
    }

    private String getUserName(long userId) {
        try {
            UserProfile profile = storageFacade.getUserProfile(userId);
            if (profile != null && profile.getNickName() != null) {
                return profile.getNickName();
            }
        } catch (Exception e) {
            logger.warn("Failed to get user profile for user {}", userId, e);
        }
        return "User " + userId;
    }

    private void sendStreamingChunk(long botId, long userId, String conversationId, long messageId,
                                     String chunk, boolean isFinal, String fullContent) {
        StreamingMessageChunk.Builder builder = StreamingMessageChunk.newBuilder()
                .setBotId(botId)
                .setUserId(userId)
                .setConversationId(conversationId)
                .setMessageId(messageId)
                .setIsFinal(isFinal);

        if (chunk != null) {
            builder.setChunk(chunk);
        }
        if (fullContent != null) {
            builder.setFullContent(fullContent);
        }

        PushMessage pushMessage = PushMessage.newBuilder()
                .setStreamingChunk(builder.build())
                .build();

        pushProducer.send(pushTopic, String.valueOf(userId), pushMessage);
    }

    private void sendBotReplyMessage(long botId, long userId, String conversationId, long messageId, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }

        // Build the message as if the bot sent it
        SendMessageReq sendMessageReq = SendMessageReq.newBuilder()
                .setSenderId(botId)
                .setConversationId(conversationId)
                .setReceiverId(userId)
                .setMessageId(messageId)
                .setSendTimestamp(System.currentTimeMillis())
                .setSingleMessageContent(SingleMessageContent.newBuilder().setText(text).build())
                .setSenderType(UserType.BOT)
                .setReceiverType(UserType.USER)
                .build();

        MessageData messageData = MessageData.newBuilder()
                .setSendUserId(botId)
                .addReceiveUserIds(userId)
                .setData(sendMessageReq)
                .build();

        MessagePayload payload = MessagePayload.newBuilder()
                .setMessageData(messageData)
                .build();

        messageProducer.send(messageTopic, String.valueOf(userId), payload);
        logger.info("[Bot Reply] Bot {} -> User {}: {} chars", botId, userId, text.length());
    }

    private long generateMessageId() {
        // Simple snowflake-like ID generation
        return messageIdGenerator.incrementAndGet();
    }
}
