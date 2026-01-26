package com.fanaujie.ripple.apigateway.service;

import com.fanaujie.ripple.apigateway.dto.*;
import com.fanaujie.ripple.cache.service.BotConfigStorage;
import com.fanaujie.ripple.protobuf.snowflakeid.GenerateIdResponse;
import com.fanaujie.ripple.snowflakeid.client.SnowflakeIdClient;
import com.fanaujie.ripple.storage.model.BotConfig;
import com.fanaujie.ripple.storage.model.BotResponseMode;
import com.fanaujie.ripple.storage.exception.NotFoundUserProfileException;
import com.fanaujie.ripple.storage.model.Conversation;
import com.fanaujie.ripple.storage.model.User;
import com.fanaujie.ripple.storage.model.UserProfile;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = {BotService.class})
class BotServiceTest {

    @MockitoBean
    private RippleStorageFacade storageFacade;

    @MockitoBean
    private BotConfigStorage botConfigStorage;

    @MockitoBean
    private SnowflakeIdClient snowflakeIdClient;

    @Autowired
    private BotService botService;

    private static final long USER_ID = 1001L;
    private static final long BOT_ID = 5001L;
    private static final String WEBHOOK_URL = "https://example.com/webhook";
    private static final String API_KEY = "test-api-key";

    @BeforeEach
    void setUp() {
        reset(storageFacade, botConfigStorage, snowflakeIdClient);
    }

    // ==================== Register Bot Tests ====================

    @Nested
    class RegisterBotTests {

        @Test
        void registerBot_Success_CreatesBotUserWithRoleBot() {
            // Given
            BotRegistrationRequest request = new BotRegistrationRequest();
            request.setAccount("test-bot");
            request.setDisplayName("Test Bot");
            request.setWebhookUrl(WEBHOOK_URL);
            request.setApiKey(API_KEY);
            request.setDescription("A test bot");

            when(storageFacade.userExists("test-bot")).thenReturn(false);

            // When
            ResponseEntity<BotRegistrationResponse> response = botService.registerBot(request);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(200, response.getBody().getCode());
            assertEquals("success", response.getBody().getMessage());
            assertNotNull(response.getBody().getData());
            assertNotNull(response.getBody().getData().getBotId());
            assertEquals("test-bot", response.getBody().getData().getAccount());

            // Verify user was created with ROLE_BOT
            verify(storageFacade).insertUser(argThat(user ->
                    user.getRole() == User.ROLE_BOT &&
                            user.getAccount().equals("test-bot") &&
                            user.getPassword() == null
            ), eq("Test Bot"), isNull());
        }

        @Test
        void registerBot_Success_CreatesBotConfig() {
            // Given
            BotRegistrationRequest request = new BotRegistrationRequest();
            request.setAccount("test-bot");
            request.setDisplayName("Test Bot");
            request.setWebhookUrl(WEBHOOK_URL);
            request.setApiKey(API_KEY);
            request.setDescription("A test bot");
            request.setAvatar("https://example.com/avatar.png");

            when(storageFacade.userExists("test-bot")).thenReturn(false);

            // When
            ResponseEntity<BotRegistrationResponse> response = botService.registerBot(request);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());

            // Verify bot config was saved
            verify(storageFacade).saveBotConfig(argThat(config ->
                    config.getWebhookUrl().equals(WEBHOOK_URL) &&
                            config.getApiKey().equals(API_KEY) &&
                            config.getDescription().equals("A test bot") &&
                            config.getCreatedAt() != null &&
                            config.getUpdatedAt() != null
            ));
        }

        @Test
        void registerBot_DuplicateAccount_Returns400() {
            // Given
            BotRegistrationRequest request = new BotRegistrationRequest();
            request.setAccount("existing-bot");
            request.setDisplayName("Existing Bot");
            request.setWebhookUrl(WEBHOOK_URL);

            when(storageFacade.userExists("existing-bot")).thenReturn(true);

            // When
            ResponseEntity<BotRegistrationResponse> response = botService.registerBot(request);

            // Then
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(400, response.getBody().getCode());
            assertEquals("Account already exists", response.getBody().getMessage());

            // Verify no user or config was created
            verify(storageFacade, never()).insertUser(any(), any(), any());
            verify(storageFacade, never()).saveBotConfig(any());
        }

        @Test
        void registerBot_Success_ReturnsBotIdAndAccount() {
            // Given
            BotRegistrationRequest request = new BotRegistrationRequest();
            request.setAccount("new-bot");
            request.setDisplayName("New Bot");
            request.setWebhookUrl(WEBHOOK_URL);

            when(storageFacade.userExists("new-bot")).thenReturn(false);

            // When
            ResponseEntity<BotRegistrationResponse> response = botService.registerBot(request);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody().getData());
            assertNotNull(response.getBody().getData().getBotId());
            assertFalse(response.getBody().getData().getBotId().isEmpty());
            assertEquals("new-bot", response.getBody().getData().getAccount());
        }

        @Test
        void registerBot_withStreamingMode_succeeds() {
            // Given
            BotRegistrationRequest request = new BotRegistrationRequest();
            request.setAccount("streaming-bot");
            request.setDisplayName("Streaming Bot");
            request.setWebhookUrl(WEBHOOK_URL);
            request.setResponseMode("STREAMING");

            when(storageFacade.userExists("streaming-bot")).thenReturn(false);

            // When
            ResponseEntity<BotRegistrationResponse> response = botService.registerBot(request);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(storageFacade).saveBotConfig(argThat(config ->
                    config.getResponseMode() == BotResponseMode.STREAMING
            ));
        }

        @Test
        void registerBot_withBatchMode_succeeds() {
            // Given
            BotRegistrationRequest request = new BotRegistrationRequest();
            request.setAccount("batch-bot");
            request.setDisplayName("Batch Bot");
            request.setWebhookUrl(WEBHOOK_URL);
            request.setResponseMode("BATCH");

            when(storageFacade.userExists("batch-bot")).thenReturn(false);

            // When
            ResponseEntity<BotRegistrationResponse> response = botService.registerBot(request);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(storageFacade).saveBotConfig(argThat(config ->
                    config.getResponseMode() == BotResponseMode.BATCH
            ));
        }

        @Test
        void registerBot_withoutResponseMode_defaultsToStreaming() {
            // Given
            BotRegistrationRequest request = new BotRegistrationRequest();
            request.setAccount("default-mode-bot");
            request.setDisplayName("Default Mode Bot");
            request.setWebhookUrl(WEBHOOK_URL);
            // responseMode not set

            when(storageFacade.userExists("default-mode-bot")).thenReturn(false);

            // When
            ResponseEntity<BotRegistrationResponse> response = botService.registerBot(request);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            verify(storageFacade).saveBotConfig(argThat(config ->
                    config.getResponseMode() == BotResponseMode.STREAMING
            ));
        }

        @Test
        void registerBot_withInvalidResponseMode_returns400() {
            // Given
            BotRegistrationRequest request = new BotRegistrationRequest();
            request.setAccount("invalid-mode-bot");
            request.setDisplayName("Invalid Mode Bot");
            request.setWebhookUrl(WEBHOOK_URL);
            request.setResponseMode("INVALID_MODE");

            when(storageFacade.userExists("invalid-mode-bot")).thenReturn(false);

            // When
            ResponseEntity<BotRegistrationResponse> response = botService.registerBot(request);

            // Then
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(400, response.getBody().getCode());
            assertTrue(response.getBody().getMessage().contains("Invalid responseMode"));

            verify(storageFacade, never()).saveBotConfig(any());
        }
    }

    // ==================== Update Bot Tests ====================

    @Nested
    class UpdateBotTests {

        @Test
        void updateBot_Success_UpdatesWebhookUrl() {
            // Given
            BotConfig existingConfig = createBotConfig(BOT_ID);
            when(storageFacade.getBotConfig(BOT_ID)).thenReturn(existingConfig);

            BotUpdateRequest request = new BotUpdateRequest();
            request.setWebhookUrl("https://new-url.com/webhook");

            // When
            ResponseEntity<CommonResponse> response = botService.updateBot(BOT_ID, request);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(200, response.getBody().getCode());

            verify(storageFacade).saveBotConfig(argThat(config ->
                    config.getWebhookUrl().equals("https://new-url.com/webhook")
            ));
        }

        @Test
        void updateBot_Success_UpdatesApiKey() {
            // Given
            BotConfig existingConfig = createBotConfig(BOT_ID);
            when(storageFacade.getBotConfig(BOT_ID)).thenReturn(existingConfig);

            BotUpdateRequest request = new BotUpdateRequest();
            request.setApiKey("new-api-key");

            // When
            ResponseEntity<CommonResponse> response = botService.updateBot(BOT_ID, request);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());

            verify(storageFacade).saveBotConfig(argThat(config ->
                    config.getApiKey().equals("new-api-key")
            ));
        }

        @Test
        void updateBot_Success_UpdatesDescription() {
            // Given
            BotConfig existingConfig = createBotConfig(BOT_ID);
            when(storageFacade.getBotConfig(BOT_ID)).thenReturn(existingConfig);

            BotUpdateRequest request = new BotUpdateRequest();
            request.setDescription("Updated description");

            // When
            ResponseEntity<CommonResponse> response = botService.updateBot(BOT_ID, request);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());

            verify(storageFacade).saveBotConfig(argThat(config ->
                    config.getDescription().equals("Updated description")
            ));
        }

        @Test
        void updateBot_Success_InvalidatesCacheAfterUpdate() {
            // Given
            BotConfig existingConfig = createBotConfig(BOT_ID);
            when(storageFacade.getBotConfig(BOT_ID)).thenReturn(existingConfig);

            BotUpdateRequest request = new BotUpdateRequest();
            request.setWebhookUrl("https://new-url.com");

            // When
            ResponseEntity<CommonResponse> response = botService.updateBot(BOT_ID, request);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());

            // Verify cache invalidation was called after save
            verify(storageFacade).saveBotConfig(any());
            verify(botConfigStorage).invalidate(BOT_ID);
        }

        @Test
        void updateBot_BotNotFound_Returns404() {
            // Given
            when(storageFacade.getBotConfig(9999L)).thenReturn(null);

            BotUpdateRequest request = new BotUpdateRequest();
            request.setWebhookUrl("https://new-url.com");

            // When
            ResponseEntity<CommonResponse> response = botService.updateBot(9999L, request);

            // Then
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(404, response.getBody().getCode());
            assertEquals("Bot not found", response.getBody().getMessage());

            verify(storageFacade, never()).saveBotConfig(any());
            verify(botConfigStorage, never()).invalidate(anyLong());
        }

        @Test
        void updateBot_changesResponseMode() {
            // Given
            BotConfig existingConfig = createBotConfig(BOT_ID);
            existingConfig.setResponseMode(BotResponseMode.STREAMING);
            when(storageFacade.getBotConfig(BOT_ID)).thenReturn(existingConfig);

            BotUpdateRequest request = new BotUpdateRequest();
            request.setResponseMode("BATCH");

            // When
            ResponseEntity<CommonResponse> response = botService.updateBot(BOT_ID, request);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());

            verify(storageFacade).saveBotConfig(argThat(config ->
                    config.getResponseMode() == BotResponseMode.BATCH
            ));
        }

        @Test
        void updateBot_withoutResponseMode_preservesExisting() {
            // Given
            BotConfig existingConfig = createBotConfig(BOT_ID);
            existingConfig.setResponseMode(BotResponseMode.BATCH);
            when(storageFacade.getBotConfig(BOT_ID)).thenReturn(existingConfig);

            BotUpdateRequest request = new BotUpdateRequest();
            request.setWebhookUrl("https://new-url.com");
            // responseMode not set

            // When
            ResponseEntity<CommonResponse> response = botService.updateBot(BOT_ID, request);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());

            verify(storageFacade).saveBotConfig(argThat(config ->
                    config.getResponseMode() == BotResponseMode.BATCH
            ));
        }

        @Test
        void updateBot_withInvalidResponseMode_returns400() {
            // Given
            BotConfig existingConfig = createBotConfig(BOT_ID);
            when(storageFacade.getBotConfig(BOT_ID)).thenReturn(existingConfig);

            BotUpdateRequest request = new BotUpdateRequest();
            request.setResponseMode("INVALID");

            // When
            ResponseEntity<CommonResponse> response = botService.updateBot(BOT_ID, request);

            // Then
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals(400, response.getBody().getCode());
            assertTrue(response.getBody().getMessage().contains("Invalid responseMode"));

            verify(storageFacade, never()).saveBotConfig(any());
        }
    }

    // ==================== Delete Bot Tests ====================

    @Nested
    class DeleteBotTests {

        @Test
        void deleteBot_Success_RemovesBotConfigFromStorage() {
            // Given
            BotConfig existingConfig = createBotConfig(BOT_ID);
            when(storageFacade.getBotConfig(BOT_ID)).thenReturn(existingConfig);

            // When
            ResponseEntity<CommonResponse> response = botService.deleteBot(BOT_ID);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(200, response.getBody().getCode());

            verify(storageFacade).deleteBotConfig(BOT_ID);
        }

        @Test
        void deleteBot_Success_InvalidatesCacheAfterDeletion() {
            // Given
            BotConfig existingConfig = createBotConfig(BOT_ID);
            when(storageFacade.getBotConfig(BOT_ID)).thenReturn(existingConfig);

            // When
            ResponseEntity<CommonResponse> response = botService.deleteBot(BOT_ID);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());

            verify(storageFacade).deleteBotConfig(BOT_ID);
            verify(botConfigStorage).invalidate(BOT_ID);
        }

        @Test
        void deleteBot_BotNotFound_Returns404() {
            // Given
            when(storageFacade.getBotConfig(9999L)).thenReturn(null);

            // When
            ResponseEntity<CommonResponse> response = botService.deleteBot(9999L);

            // Then
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertEquals(404, response.getBody().getCode());
            assertEquals("Bot not found", response.getBody().getMessage());

            verify(storageFacade, never()).deleteBotConfig(anyLong());
            verify(botConfigStorage, never()).invalidate(anyLong());
        }
    }

    // ==================== List Bots Tests ====================

    @Nested
    class ListBotsTests {

        @Test
        void listBots_Success_ReturnsAllRegisteredBots() throws Exception {
            // Given
            BotConfig bot1 = createBotConfig(5001L);
            BotConfig bot2 = createBotConfig(5002L);
            bot2.setDescription("Bot 2");

            when(storageFacade.listAllBots()).thenReturn(Arrays.asList(bot1, bot2));
            when(storageFacade.getUserProfile(5001L)).thenReturn(createUserProfile(5001L, "Bot 1"));
            when(storageFacade.getUserProfile(5002L)).thenReturn(createUserProfile(5002L, "Bot 2"));

            // When
            ResponseEntity<BotsListResponse> response = botService.listBots();

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(200, response.getBody().getCode());
            assertNotNull(response.getBody().getData());
            assertEquals(2, response.getBody().getData().getBots().size());
        }

        @Test
        void listBots_Success_IncludesBotProfile() throws Exception {
            // Given
            BotConfig botConfig = createBotConfig(BOT_ID);
            botConfig.setDescription("Test bot description");

            UserProfile profile = new UserProfile();
            profile.setUserId(BOT_ID);
            profile.setNickName("Test Bot");
            profile.setAvatar("https://example.com/avatar.png");

            when(storageFacade.listAllBots()).thenReturn(Collections.singletonList(botConfig));
            when(storageFacade.getUserProfile(BOT_ID)).thenReturn(profile);

            // When
            ResponseEntity<BotsListResponse> response = botService.listBots();

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            List<BotData> bots = response.getBody().getData().getBots();
            assertEquals(1, bots.size());

            BotData botData = bots.get(0);
            assertEquals(String.valueOf(BOT_ID), botData.getBotId());
            assertEquals("Test Bot", botData.getName());
            assertEquals("https://example.com/avatar.png", botData.getAvatar());
            assertEquals("Test bot description", botData.getDescription());
        }

        @Test
        void listBots_EmptyList_ReturnsEmptyBotsList() {
            // Given
            when(storageFacade.listAllBots()).thenReturn(Collections.emptyList());

            // When
            ResponseEntity<BotsListResponse> response = botService.listBots();

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(200, response.getBody().getCode());
            assertNotNull(response.getBody().getData());
            assertTrue(response.getBody().getData().getBots().isEmpty());
        }

        @Test
        void listBots_ProfileNotFound_UsesDefaultName() throws Exception {
            // Given
            BotConfig botConfig = createBotConfig(BOT_ID);

            when(storageFacade.listAllBots()).thenReturn(Collections.singletonList(botConfig));
            when(storageFacade.getUserProfile(BOT_ID)).thenThrow(new NotFoundUserProfileException("Profile not found"));

            // When
            ResponseEntity<BotsListResponse> response = botService.listBots();

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            List<BotData> bots = response.getBody().getData().getBots();
            assertEquals(1, bots.size());
            assertEquals("Unknown Bot", bots.get(0).getName());
            assertNull(bots.get(0).getAvatar());
        }
    }

    // ==================== Create Session Tests ====================

    @Nested
    class CreateSessionTests {

        @Test
        void createNewSession_Success_GeneratesSnowflakeIdForSessionId() throws Exception {
            // Given
            BotConfig botConfig = createBotConfig(BOT_ID);
            when(storageFacade.getBotConfig(BOT_ID)).thenReturn(botConfig);

            GenerateIdResponse idResponse = GenerateIdResponse.newBuilder()
                    .setRequestId("req-1")
                    .setId(123456789L)
                    .build();
            when(snowflakeIdClient.requestSnowflakeId())
                    .thenReturn(CompletableFuture.completedFuture(idResponse));

            // When
            ResponseEntity<BotSessionResponse> response = botService.createNewSession(USER_ID, BOT_ID);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(200, response.getBody().getCode());
            assertNotNull(response.getBody().getData());
            assertEquals("123456789", response.getBody().getData().getSessionId());

            verify(snowflakeIdClient).requestSnowflakeId();
        }

        @Test
        void createNewSession_Success_DoesNotCreateConversation() throws Exception {
            // Given - conversation creation is deferred to message-dispatcher
            BotConfig botConfig = createBotConfig(BOT_ID);
            when(storageFacade.getBotConfig(BOT_ID)).thenReturn(botConfig);

            GenerateIdResponse idResponse = GenerateIdResponse.newBuilder()
                    .setRequestId("req-1")
                    .setId(123456789L)
                    .build();
            when(snowflakeIdClient.requestSnowflakeId())
                    .thenReturn(CompletableFuture.completedFuture(idResponse));

            // When
            ResponseEntity<BotSessionResponse> response = botService.createNewSession(USER_ID, BOT_ID);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());

            // Verify no conversation-related storage operations
            verify(storageFacade, never()).getConversation(anyLong(), anyString());
            verify(storageFacade, never()).createSingeMessageConversation(
                    anyString(), anyLong(), anyLong(), anyLong());
        }

        @Test
        void createNewSession_BotNotFound_Returns404() {
            // Given
            when(storageFacade.getBotConfig(9999L)).thenReturn(null);

            // When
            ResponseEntity<BotSessionResponse> response = botService.createNewSession(USER_ID, 9999L);

            // Then
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertEquals(404, response.getBody().getCode());
            assertEquals("Bot not found", response.getBody().getMessage());

            verify(snowflakeIdClient, never()).requestSnowflakeId();
        }

        @Test
        void createNewSession_Success_ReturnsSessionIdBotIdCreatedAt() throws Exception {
            // Given
            BotConfig botConfig = createBotConfig(BOT_ID);
            when(storageFacade.getBotConfig(BOT_ID)).thenReturn(botConfig);

            GenerateIdResponse idResponse = GenerateIdResponse.newBuilder()
                    .setRequestId("req-1")
                    .setId(987654321L)
                    .build();
            when(snowflakeIdClient.requestSnowflakeId())
                    .thenReturn(CompletableFuture.completedFuture(idResponse));

            // When
            ResponseEntity<BotSessionResponse> response = botService.createNewSession(USER_ID, BOT_ID);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            BotSessionData data = response.getBody().getData();
            assertNotNull(data);
            assertEquals("987654321", data.getSessionId());
            assertEquals(String.valueOf(BOT_ID), data.getBotId());
            assertTrue(data.getCreatedAt() > 0);
        }
    }

    // ==================== Get Session Tests ====================

    @Nested
    class GetSessionTests {

        @Test
        void getSession_Success_ReturnsExistingSessionFromConversation() {
            // Given
            Conversation conversation = new Conversation();
            conversation.setConversationId("conv-123");
            conversation.setBotSessionId("session-456");

            when(storageFacade.getConversation(eq(USER_ID), anyString())).thenReturn(conversation);

            // When
            ResponseEntity<BotSessionResponse> response = botService.getSession(USER_ID, BOT_ID);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(200, response.getBody().getCode());
            assertEquals("session-456", response.getBody().getData().getSessionId());
            assertEquals(String.valueOf(BOT_ID), response.getBody().getData().getBotId());
        }

        @Test
        void getSession_ConversationNotFound_Returns404() {
            // Given
            when(storageFacade.getConversation(eq(USER_ID), anyString())).thenReturn(null);

            // When
            ResponseEntity<BotSessionResponse> response = botService.getSession(USER_ID, BOT_ID);

            // Then
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertEquals(404, response.getBody().getCode());
            assertEquals("Session not found", response.getBody().getMessage());
        }

        @Test
        void getSession_BotSessionIdIsNull_Returns404() {
            // Given
            Conversation conversation = new Conversation();
            conversation.setConversationId("conv-123");
            conversation.setBotSessionId(null);

            when(storageFacade.getConversation(eq(USER_ID), anyString())).thenReturn(conversation);

            // When
            ResponseEntity<BotSessionResponse> response = botService.getSession(USER_ID, BOT_ID);

            // Then
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertEquals(404, response.getBody().getCode());
            assertEquals("Session not found", response.getBody().getMessage());
        }
    }

    // ==================== Helper Methods ====================

    private BotConfig createBotConfig(long botId) {
        BotConfig config = new BotConfig();
        config.setUserId(botId);
        config.setWebhookUrl(WEBHOOK_URL);
        config.setApiKey(API_KEY);
        config.setDescription("Test bot");
        config.setCreatedAt(Instant.now());
        config.setUpdatedAt(Instant.now());
        return config;
    }

    private UserProfile createUserProfile(long userId, String nickName) {
        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        profile.setNickName(nickName);
        profile.setAvatar("https://example.com/avatar.png");
        return profile;
    }
}
