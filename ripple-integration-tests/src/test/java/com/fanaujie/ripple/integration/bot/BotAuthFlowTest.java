package com.fanaujie.ripple.integration.bot;

import com.fanaujie.ripple.integration.base.AbstractBusinessFlowTest;
import com.fanaujie.ripple.integration.mock.MockProducer;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;
import com.fanaujie.ripple.protobuf.push.PushMessage;
import com.fanaujie.ripple.storage.model.BotUserToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Bot Auth Flow Tests")
class BotAuthFlowTest extends AbstractBusinessFlowTest {

    // Test users
    protected static final long ALICE_ID = 1001L;
    protected static final long BOB_ID = 2001L;

    // Test bots
    protected static final long AUTH_BOT_ID = 100001L;
    protected static final long NO_AUTH_BOT_ID = 100002L;

    private long messageIdCounter = 100000L;

    @BeforeEach
    void setUpTestData() {
        // Create test users
        createUser(ALICE_ID, "alice", "Alice", "alice-avatar.png");
        createUser(BOB_ID, "bob", "Bob", "bob-avatar.png");

        // Create test bots
        String authConfig = "{\"auth_url\":\"https://example.com/oauth/authorize\",\"token_url\":\"https://example.com/oauth/token\",\"client_id\":\"test-client\"}";
        createAuthRequiredBot(AUTH_BOT_ID, "Auth Bot", "http://localhost:8080/webhook", authConfig);
        createBot(NO_AUTH_BOT_ID, "No Auth Bot", "http://localhost:8081/webhook");
    }

    private long nextMessageId() {
        return ++messageIdCounter;
    }

    @Nested
    @DisplayName("Scenario: Token storage")
    class TokenStorageScenarios {

        @Test
        @DisplayName("Token is stored after OAuth callback")
        void storeToken_afterOAuthCallback() throws Exception {
            // Given: User completes OAuth flow
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.HOUR, 1);
            Date expiresAt = cal.getTime();

            // When: Token is stored
            storeBotUserToken(AUTH_BOT_ID, ALICE_ID, "test-access-token", expiresAt);

            // Then: Token can be retrieved
            BotUserToken token = storageFacade.getBotUserToken(AUTH_BOT_ID, ALICE_ID);
            assertThat(token).isNotNull();
            assertThat(token.getAccessToken()).isEqualTo("test-access-token");
            assertThat(token.getBotId()).isEqualTo(AUTH_BOT_ID);
            assertThat(token.getUserId()).isEqualTo(ALICE_ID);
        }

        @Test
        @DisplayName("Token expiration is stored correctly")
        void storeToken_expirationStored() throws Exception {
            // Given: Token with specific expiration
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.HOUR, 2);
            Date expiresAt = cal.getTime();

            // When: Token is stored
            storeBotUserToken(AUTH_BOT_ID, ALICE_ID, "expiring-token", expiresAt);

            // Then: Expiration is correctly stored
            BotUserToken token = storageFacade.getBotUserToken(AUTH_BOT_ID, ALICE_ID);
            assertThat(token.getExpiresAt()).isNotNull();
            assertThat(token.getExpiresAt()).isAfter(new Date());
        }

        @Test
        @DisplayName("Different users have separate tokens for same bot")
        void separateTokensPerUser() throws Exception {
            // Given: Two users with tokens for the same bot
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.HOUR, 1);
            Date expiresAt = cal.getTime();

            storeBotUserToken(AUTH_BOT_ID, ALICE_ID, "alice-token", expiresAt);
            storeBotUserToken(AUTH_BOT_ID, BOB_ID, "bob-token", expiresAt);

            // When: Retrieving tokens
            BotUserToken aliceToken = storageFacade.getBotUserToken(AUTH_BOT_ID, ALICE_ID);
            BotUserToken bobToken = storageFacade.getBotUserToken(AUTH_BOT_ID, BOB_ID);

            // Then: Each user has their own token
            assertThat(aliceToken.getAccessToken()).isEqualTo("alice-token");
            assertThat(bobToken.getAccessToken()).isEqualTo("bob-token");
        }

        @Test
        @DisplayName("Same user has separate tokens for different bots")
        void separateTokensPerBot() throws Exception {
            // Given: Create another auth bot
            String authConfig = "{\"auth_url\":\"https://other.com/oauth/authorize\"}";
            createAuthRequiredBot(100099L, "Other Auth Bot", "http://localhost:9999/webhook", authConfig);

            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.HOUR, 1);
            Date expiresAt = cal.getTime();

            storeBotUserToken(AUTH_BOT_ID, ALICE_ID, "token-for-auth-bot", expiresAt);
            storeBotUserToken(100099L, ALICE_ID, "token-for-other-bot", expiresAt);

            // When: Retrieving tokens
            BotUserToken authBotToken = storageFacade.getBotUserToken(AUTH_BOT_ID, ALICE_ID);
            BotUserToken otherBotToken = storageFacade.getBotUserToken(100099L, ALICE_ID);

            // Then: Each bot has its own token
            assertThat(authBotToken.getAccessToken()).isEqualTo("token-for-auth-bot");
            assertThat(otherBotToken.getAccessToken()).isEqualTo("token-for-other-bot");
        }

        @Test
        @DisplayName("Token update overwrites previous token")
        void updateToken_overwritesPrevious() throws Exception {
            // Given: Initial token
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.HOUR, 1);
            Date expiresAt = cal.getTime();

            storeBotUserToken(AUTH_BOT_ID, ALICE_ID, "initial-token", expiresAt);

            // When: Token is updated
            cal.add(Calendar.HOUR, 2);
            Date newExpiresAt = cal.getTime();
            storeBotUserToken(AUTH_BOT_ID, ALICE_ID, "updated-token", newExpiresAt);

            // Then: Only the new token exists
            BotUserToken token = storageFacade.getBotUserToken(AUTH_BOT_ID, ALICE_ID);
            assertThat(token.getAccessToken()).isEqualTo("updated-token");
        }
    }

    @Nested
    @DisplayName("Scenario: Token retrieval")
    class TokenRetrievalScenarios {

        @Test
        @DisplayName("Get non-existent token returns null")
        void getNonExistentToken_returnsNull() throws Exception {
            // When: Getting a token that doesn't exist
            BotUserToken token = storageFacade.getBotUserToken(AUTH_BOT_ID, ALICE_ID);

            // Then: Null should be returned
            assertThat(token).isNull();
        }

        @Test
        @DisplayName("Get token for wrong bot returns null")
        void getTokenForWrongBot_returnsNull() throws Exception {
            // Given: Token stored for one bot
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.HOUR, 1);
            storeBotUserToken(AUTH_BOT_ID, ALICE_ID, "test-token", cal.getTime());

            // When: Getting token for a different bot
            BotUserToken token = storageFacade.getBotUserToken(NO_AUTH_BOT_ID, ALICE_ID);

            // Then: Null should be returned
            assertThat(token).isNull();
        }

        @Test
        @DisplayName("Get token for wrong user returns null")
        void getTokenForWrongUser_returnsNull() throws Exception {
            // Given: Token stored for one user
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.HOUR, 1);
            storeBotUserToken(AUTH_BOT_ID, ALICE_ID, "alice-token", cal.getTime());

            // When: Getting token for a different user
            BotUserToken token = storageFacade.getBotUserToken(AUTH_BOT_ID, BOB_ID);

            // Then: Null should be returned
            assertThat(token).isNull();
        }
    }

    @Nested
    @DisplayName("Scenario: Auth required bot configuration")
    class AuthRequiredConfigScenarios {

        @Test
        @DisplayName("Bot with require_auth stores auth config")
        void authRequiredBot_storesAuthConfig() throws Exception {
            // When: Getting the auth required bot
            var bot = storageFacade.getBot(AUTH_BOT_ID);

            // Then: Auth config should be present
            assertThat(bot.isRequireAuth()).isTrue();
            assertThat(bot.getAuthConfig()).isNotNull();
            assertThat(bot.getAuthConfig()).contains("auth_url");
            assertThat(bot.getAuthConfig()).contains("token_url");
            assertThat(bot.getAuthConfig()).contains("client_id");
        }

        @Test
        @DisplayName("Bot without require_auth has no auth config")
        void noAuthBot_noAuthConfig() throws Exception {
            // When: Getting the no-auth bot
            var bot = storageFacade.getBot(NO_AUTH_BOT_ID);

            // Then: Auth config should be null or empty
            assertThat(bot.isRequireAuth()).isFalse();
        }
    }

    @Nested
    @DisplayName("Scenario: Message to auth-required bot without token")
    class MessageWithoutTokenScenarios {

        @Test
        @DisplayName("User sends message to bot, message is routed to bot topic")
        void sendMessageToAuthBot_routedToBottopic() throws Exception {
            // Given: Alice has installed the auth bot (but has no token)
            installBotForUser(ALICE_ID, AUTH_BOT_ID);

            String conversationId = generateBotConversationId(ALICE_ID, AUTH_BOT_ID);
            long messageId = nextMessageId();

            // When: Alice sends a message to the auth bot
            var request = createSendBotMessageRequest(
                    ALICE_ID, AUTH_BOT_ID, conversationId, messageId, "Hello Auth Bot!");
            singleMessageContentProcessor.handle(request);
            processMessagePayloads();

            // Then: The message should be routed to bot topic
            // (The BotExecutorProcessor will handle auth checking)
            List<MockProducer.CapturedMessage<String, MessagePayload>> botMessages = getBotTopicMessages();
            assertThat(botMessages).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Scenario: Message to auth-required bot with valid token")
    class MessageWithValidTokenScenarios {

        @Test
        @DisplayName("When user has valid token, message is routed to bot topic")
        void sendMessageWithValidToken_routedToBottopic() throws Exception {
            // Given: Alice has a valid token for the auth bot
            installBotForUser(ALICE_ID, AUTH_BOT_ID);
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.HOUR, 1);
            storeBotUserToken(AUTH_BOT_ID, ALICE_ID, "valid-access-token", cal.getTime());

            String conversationId = generateBotConversationId(ALICE_ID, AUTH_BOT_ID);
            long messageId = nextMessageId();

            // When: Alice sends a message to the auth bot
            var request = createSendBotMessageRequest(
                    ALICE_ID, AUTH_BOT_ID, conversationId, messageId, "Hello with token!");
            singleMessageContentProcessor.handle(request);
            processMessagePayloads();

            // Then: Message should be routed to bot topic
            List<MockProducer.CapturedMessage<String, MessagePayload>> botMessages = getBotTopicMessages();
            assertThat(botMessages).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Scenario: Token expiration handling")
    class TokenExpirationScenarios {

        @Test
        @DisplayName("Expired token can be detected")
        void expiredToken_canBeDetected() throws Exception {
            // Given: Token with past expiration
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.HOUR, -1); // 1 hour ago
            Date expiredAt = cal.getTime();

            storeBotUserToken(AUTH_BOT_ID, ALICE_ID, "expired-token", expiredAt);

            // When: Retrieving the token
            BotUserToken token = storageFacade.getBotUserToken(AUTH_BOT_ID, ALICE_ID);

            // Then: Token is present but expired
            assertThat(token).isNotNull();
            assertThat(token.getExpiresAt()).isBefore(new Date());
        }

        @Test
        @DisplayName("Valid token can be detected")
        void validToken_canBeDetected() throws Exception {
            // Given: Token with future expiration
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.HOUR, 1); // 1 hour from now
            Date validUntil = cal.getTime();

            storeBotUserToken(AUTH_BOT_ID, ALICE_ID, "valid-token", validUntil);

            // When: Retrieving the token
            BotUserToken token = storageFacade.getBotUserToken(AUTH_BOT_ID, ALICE_ID);

            // Then: Token is present and not expired
            assertThat(token).isNotNull();
            assertThat(token.getExpiresAt()).isAfter(new Date());
        }
    }
}
