package com.fanaujie.ripple.apigateway.service;

import com.fanaujie.ripple.apigateway.dto.*;
import com.fanaujie.ripple.cache.service.BotConfigStorage;
import com.fanaujie.ripple.snowflakeid.client.SnowflakeIdClient;
import com.fanaujie.ripple.storage.model.BotConfig;
import com.fanaujie.ripple.storage.model.Conversation;
import com.fanaujie.ripple.storage.model.User;
import com.fanaujie.ripple.storage.model.UserProfile;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import com.fanaujie.ripple.storage.service.utils.ConversationUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BotService {

    private final RippleStorageFacade storageFacade;
    private final BotConfigStorage botConfigStorage;
    private final SnowflakeIdClient snowflakeIdClient;

    public BotService(
            RippleStorageFacade storageFacade,
            BotConfigStorage botConfigStorage,
            SnowflakeIdClient snowflakeIdClient) {
        this.storageFacade = storageFacade;
        this.botConfigStorage = botConfigStorage;
        this.snowflakeIdClient = snowflakeIdClient;
    }

    // ==================== Bot Session Management ====================

    public ResponseEntity<BotSessionResponse> createNewSession(long userId, long botId) {
        try {
            // Verify bot exists
            BotConfig botConfig = storageFacade.getBotConfig(botId);
            if (botConfig == null) {
                return ResponseEntity.status(404)
                        .body(new BotSessionResponse(404, "Bot not found", null));
            }

            long snowflakeId =
                    snowflakeIdClient.requestSnowflakeId().get(5, TimeUnit.SECONDS).getId();
            String sessionId = String.valueOf(snowflakeId);
            BotSessionData data = new BotSessionData();
            data.setSessionId(sessionId);
            data.setBotId(String.valueOf(botId));
            data.setCreatedAt(System.currentTimeMillis());

            return ResponseEntity.ok(new BotSessionResponse(200, "success", data));
        } catch (Exception e) {
            log.error(
                    "Failed to create new session for user {} with bot {}: {}",
                    userId,
                    botId,
                    e.getMessage(),
                    e);
            return ResponseEntity.status(500)
                    .body(new BotSessionResponse(500, "Failed to create session", null));
        }
    }

    public ResponseEntity<BotSessionResponse> getSession(long userId, long botId) {
        try {
            // Get conversation ID for this user-bot pair
            String conversationId = ConversationUtils.generateConversationId(userId, botId);
            Conversation conversation = storageFacade.getConversation(userId, conversationId);

            if (conversation == null || conversation.getBotSessionId() == null) {
                return ResponseEntity.status(404)
                        .body(new BotSessionResponse(404, "Session not found", null));
            }

            BotSessionData data = new BotSessionData();
            data.setSessionId(conversation.getBotSessionId());
            data.setBotId(String.valueOf(botId));
            // Note: createdAt and lastActiveAt are no longer tracked in the new architecture

            return ResponseEntity.ok(new BotSessionResponse(200, "success", data));
        } catch (Exception e) {
            log.error(
                    "Failed to get session for user {} with bot {}: {}",
                    userId,
                    botId,
                    e.getMessage(),
                    e);
            return ResponseEntity.status(500)
                    .body(new BotSessionResponse(500, "Failed to get session", null));
        }
    }

    // ==================== Bot Listing ====================

    public ResponseEntity<BotsListResponse> listBots() {
        try {
            List<BotConfig> allBots = storageFacade.listAllBots();

            List<BotData> botDataList =
                    allBots.stream()
                            .map(
                                    config -> {
                                        BotData data = new BotData();
                                        data.setBotId(String.valueOf(config.getUserId()));
                                        data.setDescription(config.getDescription());
                                        try {
                                            UserProfile profile =
                                                    storageFacade.getUserProfile(
                                                            config.getUserId());
                                            data.setName(profile.getNickName());
                                            data.setAvatar(profile.getAvatar());
                                        } catch (Exception e) {
                                            data.setName("Unknown Bot");
                                            data.setAvatar(null);
                                        }
                                        return data;
                                    })
                            .collect(Collectors.toList());

            BotsListData botsListData = new BotsListData(botDataList);
            return ResponseEntity.ok(new BotsListResponse(200, "success", botsListData));
        } catch (Exception e) {
            log.error("Failed to list bots: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body(new BotsListResponse(500, "Failed to list bots", null));
        }
    }

    // ==================== Admin Bot Management ====================

    public ResponseEntity<BotRegistrationResponse> registerBot(BotRegistrationRequest request) {
        try {
            // Check if account already exists
            if (storageFacade.userExists(request.getAccount())) {
                return ResponseEntity.status(400)
                        .body(new BotRegistrationResponse(400, "Account already exists", null));
            }

            // Generate bot user ID (in production, use Snowflake ID service)
            long botUserId = System.currentTimeMillis();

            // Create bot user
            User botUser = new User();
            botUser.setUserId(botUserId);
            botUser.setAccount(request.getAccount());
            botUser.setPassword(null); // Bots don't have passwords
            botUser.setRole(User.ROLE_BOT);
            botUser.setStatus((byte) 0);

            storageFacade.insertUser(botUser, request.getDisplayName(), request.getAvatar());

            // Create bot config
            Instant now = Instant.now();
            BotConfig config = new BotConfig();
            config.setUserId(botUserId);
            config.setWebhookUrl(request.getWebhookUrl());
            config.setApiKey(request.getApiKey());
            config.setDescription(request.getDescription());
            config.setCreatedAt(now);
            config.setUpdatedAt(now);

            storageFacade.saveBotConfig(config);

            BotRegistrationData data = new BotRegistrationData();
            data.setBotId(String.valueOf(botUserId));
            data.setAccount(request.getAccount());

            return ResponseEntity.ok(new BotRegistrationResponse(200, "success", data));
        } catch (Exception e) {
            log.error("Failed to register bot: {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body(new BotRegistrationResponse(500, "Failed to register bot", null));
        }
    }

    public ResponseEntity<CommonResponse> updateBot(long botId, BotUpdateRequest request) {
        try {
            BotConfig config = storageFacade.getBotConfig(botId);
            if (config == null) {
                return ResponseEntity.status(404).body(new CommonResponse(404, "Bot not found"));
            }

            // Update config
            Instant now = Instant.now();
            config.setUpdatedAt(now);

            if (request.getWebhookUrl() != null) {
                config.setWebhookUrl(request.getWebhookUrl());
            }
            if (request.getApiKey() != null) {
                config.setApiKey(request.getApiKey());
            }
            if (request.getDescription() != null) {
                config.setDescription(request.getDescription());
            }

            storageFacade.saveBotConfig(config);

            // Invalidate cache after update
            botConfigStorage.invalidate(botId);

            return ResponseEntity.ok(new CommonResponse(200, "success"));
        } catch (Exception e) {
            log.error("Failed to update bot {}: {}", botId, e.getMessage(), e);
            return ResponseEntity.status(500).body(new CommonResponse(500, "Failed to update bot"));
        }
    }

    public ResponseEntity<CommonResponse> deleteBot(long botId) {
        try {
            BotConfig config = storageFacade.getBotConfig(botId);
            if (config == null) {
                return ResponseEntity.status(404).body(new CommonResponse(404, "Bot not found"));
            }

            storageFacade.deleteBotConfig(botId);

            // Invalidate cache after deletion
            botConfigStorage.invalidate(botId);

            return ResponseEntity.ok(new CommonResponse(200, "success"));
        } catch (Exception e) {
            log.error("Failed to delete bot {}: {}", botId, e.getMessage(), e);
            return ResponseEntity.status(500).body(new CommonResponse(500, "Failed to delete bot"));
        }
    }
}
