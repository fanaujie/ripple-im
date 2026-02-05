package com.fanaujie.ripple.integration.message;

import com.fanaujie.ripple.integration.base.AbstractBusinessFlowTest;
import com.fanaujie.ripple.protobuf.msgdispatcher.BotWebhookEvent;
import com.fanaujie.ripple.storage.model.BotResponseMode;
import com.fanaujie.ripple.storage.model.Conversation;
import com.fanaujie.ripple.storage.model.Message;
import com.fanaujie.ripple.storage.model.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public abstract class AbstractBotMessageFlowTest extends AbstractBusinessFlowTest {

    protected static final String BOT_WEBHOOK_URL = "https://bot.example.com/webhook";
    protected static final String BOT_API_KEY = "test-bot-api-key";

    protected long aliceId() {
        return testIdBase + 1;
    }

    protected long bobId() {
        return testIdBase + 2;
    }

    protected long botId() {
        return testIdBase + 5001;
    }

    @BeforeEach
    void setUpTestData() {
        // Create test users with unique IDs
        createUser(aliceId(), "alice-" + testIdBase, "Alice");
        createUser(bobId(), "bob-" + testIdBase, "Bob");

        // Create bot user with profile
        createUser(botId(), "test-bot-" + testIdBase, "Test Bot");
    }

    // ==================== Bot Detection and Routing Tests ====================

    @Nested
    class BotDetectionAndRoutingTests {

        @Test
        void messageToBotIsRoutedToWebhookTopic() throws Exception {
            // Given
            registerBot(botId(), BOT_WEBHOOK_URL, BOT_API_KEY);
            String conversationId = generateSingleConversationId(aliceId(), botId());

            // When
            executeSendBotMessageFlow(
                    aliceId(), botId(), conversationId, 1001L, "Hello bot", "session-123");

            // Then - Message should be sent to bot webhook topic
            assertTrue(botWebhookProducer.hasMessages(), "Bot webhook should receive message");
            assertEquals(1, botWebhookProducer.messageCount());

            // And - Message should also be sent to push topic (for sender's other devices)
            assertTrue(
                    pushMessageProducer.hasMessages(),
                    "Push topic should receive bot messages for sender's other devices");
        }

        @Test
        void messageToRegularUserIsRoutedToPushTopic() throws Exception {
            // Given - Bob is NOT registered as a bot
            String conversationId = generateSingleConversationId(aliceId(), bobId());

            // First, establish friendship for conversation creation
            executeAddFriendFlow(aliceId(), bobId());
            pushMessageProducer.clear(); // Clear push messages from add friend flow

            // When
            executeSendMessageFlow(aliceId(), bobId(), conversationId, 2001L, "Hello Bob");

            // Then - Message should be sent to push topic
            assertTrue(
                    pushMessageProducer.hasMessages(),
                    "Push topic should receive regular messages");

            // And - Message should NOT be sent to bot webhook topic
            assertFalse(
                    botWebhookProducer.hasMessages(),
                    "Bot webhook should not receive regular messages");
        }

        @Test
        void messageToBotWithoutRelationIsAccepted() throws Exception {
            // Given - Bot is registered but NO Relation exists between Alice and Bot
            // (Bots don't require friendship to receive messages)
            registerBot(botId(), BOT_WEBHOOK_URL, BOT_API_KEY);
            String conversationId = generateSingleConversationId(aliceId(), botId());

            // Verify no relation exists
            assertNull(
                    storageFacade.getRelationBetweenUser(aliceId(), botId()),
                    "No relation should exist");

            // When - Alice sends message directly to bot without establishing friendship
            executeSendBotMessageFlow(
                    aliceId(),
                    botId(),
                    conversationId,
                    2002L,
                    "Hello bot without relation",
                    "session-direct");

            // Then - Message should be accepted and routed to bot webhook
            assertTrue(
                    botWebhookProducer.hasMessages(),
                    "Bot should receive message without relation");
            assertEquals(1, botWebhookProducer.messageCount());

            // And - Conversation should be created
            Conversation conv = storageFacade.getConversation(aliceId(), conversationId);
            assertNotNull(conv, "Conversation should be created without prior relation");
        }

        @Test
        void botConfigNotFoundInCacheRejectsMessage() throws Exception {
            // Given - Bot is registered in MockBotConfigStorage.isBot() but get() returns null
            // This simulates a cache inconsistency
            botConfigStorage.registerBot(botId(), null); // Register null config

            String conversationId = generateSingleConversationId(aliceId(), botId());

            // When
            executeSendBotMessageFlow(
                    aliceId(), botId(), conversationId, 1002L, "Hello bot", "session-123");

            // Then - Message should NOT be dispatched to bot webhook
            assertFalse(
                    botWebhookProducer.hasMessages(),
                    "Bot webhook should not receive message when config is null");
        }
    }

    // ==================== Session Validation Tests ====================

    @Nested
    class SessionValidationTests {

        @Test
        void messageWithValidSessionIdIsAccepted() throws Exception {
            // Given
            registerBot(botId(), BOT_WEBHOOK_URL, BOT_API_KEY);
            String conversationId = generateSingleConversationId(aliceId(), botId());

            // When
            executeSendBotMessageFlow(
                    aliceId(), botId(), conversationId, 1003L, "Hello", "valid-session-123");

            // Then
            assertTrue(
                    botWebhookProducer.hasMessages(),
                    "Message with valid session should be dispatched");

            // Verify session ID in payload
            BotWebhookEvent event = botWebhookProducer.getCapturedMessages().get(0).value();
            assertEquals("valid-session-123", event.getSessionId());
        }

        @Test
        void messageWithEmptySessionIdIsRejected() throws Exception {
            // Given
            registerBot(botId(), BOT_WEBHOOK_URL, BOT_API_KEY);
            String conversationId = generateSingleConversationId(aliceId(), botId());

            // When - Send message with empty session ID
            executeSendBotMessageFlow(aliceId(), botId(), conversationId, 1004L, "Hello", "");

            // Then - Message should NOT be dispatched
            assertFalse(
                    botWebhookProducer.hasMessages(),
                    "Message with empty session should be rejected");
        }

        @Test
        void messageWithNullSessionIdIsRejected() throws Exception {
            // Given
            registerBot(botId(), BOT_WEBHOOK_URL, BOT_API_KEY);
            String conversationId = generateSingleConversationId(aliceId(), botId());

            // When - Send message with null session ID (empty string in protobuf)
            executeSendBotMessageFlow(aliceId(), botId(), conversationId, 1005L, "Hello", "");

            // Then - Message should NOT be dispatched
            assertFalse(
                    botWebhookProducer.hasMessages(),
                    "Message with null/empty session should be rejected");
        }
    }

    // ==================== Conversation Session ID Update Tests ====================

    @Nested
    class ConversationSessionIdUpdateTests {

        @Test
        void firstMessageCreatesConversationWithSessionId() throws Exception {
            // Given
            registerBot(botId(), BOT_WEBHOOK_URL, BOT_API_KEY);
            String conversationId = generateSingleConversationId(aliceId(), botId());

            // When
            executeSendBotMessageFlow(
                    aliceId(), botId(), conversationId, 1006L, "First message", "new-session-1");

            // Then - Conversation should exist for user
            Conversation userConv = storageFacade.getConversation(aliceId(), conversationId);
            assertNotNull(userConv, "Conversation should be created for user");

            // And - Conversation should exist for bot
            Conversation botConv = storageFacade.getConversation(botId(), conversationId);
            assertNotNull(botConv, "Conversation should be created for bot");
        }

        @Test
        void sessionIdIsUpdatedWhenItDiffers() throws Exception {
            // Given - First message with old session
            registerBot(botId(), BOT_WEBHOOK_URL, BOT_API_KEY);
            String conversationId = generateSingleConversationId(aliceId(), botId());

            executeSendBotMessageFlow(
                    aliceId(), botId(), conversationId, 1007L, "First", "old-session");
            botWebhookProducer.clear();

            // When - Second message with new session
            executeSendBotMessageFlow(
                    aliceId(), botId(), conversationId, 1008L, "Second", "new-session");

            // Then - Message should be dispatched with new session
            assertTrue(botWebhookProducer.hasMessages());
            BotWebhookEvent event = botWebhookProducer.getCapturedMessages().get(0).value();
            assertEquals("new-session", event.getSessionId());

            // And - Conversation should have updated session ID
            Conversation conv = storageFacade.getConversation(aliceId(), conversationId);
            assertNotNull(conv);
            assertEquals("new-session", conv.getBotSessionId());
        }

        @Test
        void sessionIdIsNotUpdatedWhenSame() throws Exception {
            // Given - First message with session
            registerBot(botId(), BOT_WEBHOOK_URL, BOT_API_KEY);
            String conversationId = generateSingleConversationId(aliceId(), botId());

            executeSendBotMessageFlow(
                    aliceId(), botId(), conversationId, 1009L, "First", "same-session");
            botWebhookProducer.clear();

            // When - Second message with same session
            executeSendBotMessageFlow(
                    aliceId(), botId(), conversationId, 1010L, "Second", "same-session");

            // Then - Both messages should be dispatched successfully
            assertTrue(botWebhookProducer.hasMessages());

            // And - Session ID should remain the same
            Conversation conv = storageFacade.getConversation(aliceId(), conversationId);
            assertEquals("same-session", conv.getBotSessionId());
        }
    }

    // ==================== Conversation Storage Tests ====================

    @Nested
    class ConversationStorageTests {

        @Test
        void botConversationIsCreatedForBothParties() throws Exception {
            // Given
            registerBot(botId(), BOT_WEBHOOK_URL, BOT_API_KEY);
            String conversationId = generateSingleConversationId(aliceId(), botId());

            // When
            executeSendBotMessageFlow(
                    aliceId(), botId(), conversationId, 1011L, "Hello bot", "session-1");

            // Then - Conversation exists for user
            Conversation userConv = storageFacade.getConversation(aliceId(), conversationId);
            assertNotNull(userConv);
            assertEquals(botId(), userConv.getPeerId());

            // And - Conversation exists for bot
            Conversation botConv = storageFacade.getConversation(botId(), conversationId);
            assertNotNull(botConv);
            assertEquals(aliceId(), botConv.getPeerId());
        }

        @Test
        void botMessagesAreStoredCorrectly() throws Exception {
            // Given
            registerBot(botId(), BOT_WEBHOOK_URL, BOT_API_KEY);
            String conversationId = generateSingleConversationId(aliceId(), botId());

            // When - Send multiple messages
            executeSendBotMessageFlow(
                    aliceId(), botId(), conversationId, 1012L, "Message 1", "session-1");
            executeSendBotMessageFlow(
                    aliceId(), botId(), conversationId, 1013L, "Message 2", "session-1");
            executeSendBotMessageFlow(
                    aliceId(), botId(), conversationId, 1014L, "Message 3", "session-1");

            // Then - All messages should be stored
            Messages messagesResult = storageFacade.getMessages(conversationId, 0, 10);
            List<Message> messages = messagesResult.getMessages();
            assertEquals(3, messages.size());

            // Verify message content
            assertTrue(messages.stream().anyMatch(m -> m.getText().equals("Message 1")));
            assertTrue(messages.stream().anyMatch(m -> m.getText().equals("Message 2")));
            assertTrue(messages.stream().anyMatch(m -> m.getText().equals("Message 3")));
        }

        @Test
        void conversationSummaryIsUpdated() throws Exception {
            // Given
            registerBot(botId(), BOT_WEBHOOK_URL, BOT_API_KEY);
            String conversationId = generateSingleConversationId(aliceId(), botId());

            // When
            executeSendBotMessageFlow(
                    aliceId(), botId(), conversationId, 1015L, "Latest message", "session-1");

            // Then - Conversation summary should be updated for bot (receiver)
            var summary =
                    conversationSummaryStorage.getConversationSummary(botId(), conversationId);
            assertNotNull(summary);
            assertNotNull(summary.getLastMessage());
            assertEquals("Latest message", summary.getLastMessage().getText());
        }
    }

    // ==================== Bot Webhook Payload Tests ====================

    @Nested
    class BotWebhookPayloadTests {

        @Test
        void webhookPayloadContainsMessageMetadata() throws Exception {
            // Given
            registerBot(botId(), BOT_WEBHOOK_URL, BOT_API_KEY);
            String conversationId = generateSingleConversationId(aliceId(), botId());
            long messageId = 1016L;
            String messageText = "Hello bot, how are you?";
            String sessionId = "test-session-456";

            // When
            executeSendBotMessageFlow(
                    aliceId(), botId(), conversationId, messageId, messageText, sessionId);

            // Then
            assertEquals(1, botWebhookProducer.messageCount());
            BotWebhookEvent event = botWebhookProducer.getCapturedMessages().get(0).value();

            assertEquals(aliceId(), event.getSenderUserId());
            assertEquals(botId(), event.getBotUserId());
            assertEquals(conversationId, event.getConversationId());
            assertEquals(messageId, event.getMessageId());
            assertEquals(messageText, event.getMessageText());
            assertEquals(sessionId, event.getSessionId());
            assertTrue(event.getSendTimestamp() > 0);
        }

        @Test
        void webhookPayloadDoesNotContainBotConfig() throws Exception {
            // Given - Bot with specific config
            registerBot(botId(), BOT_WEBHOOK_URL, BOT_API_KEY, BotResponseMode.BATCH);
            String conversationId = generateSingleConversationId(aliceId(), botId());

            // When
            executeSendBotMessageFlow(
                    aliceId(), botId(), conversationId, 1017L, "Hello", "session-1");

            // Then - BotWebhookEvent should NOT contain webhook config fields
            BotWebhookEvent event = botWebhookProducer.getCapturedMessages().get(0).value();
            // BotWebhookEvent has no webhook_url, api_key, or response_mode fields
            // These are resolved by webhook-service at dispatch time
            assertEquals(aliceId(), event.getSenderUserId());
            assertEquals(botId(), event.getBotUserId());
        }
    }

    // ==================== Push to Sender Tests ====================

    @Nested
    class PushToSenderTests {

        @Test
        void botMessageIsPushedToSender() throws Exception {
            // Given
            registerBot(botId(), BOT_WEBHOOK_URL, BOT_API_KEY);
            String conversationId = generateSingleConversationId(aliceId(), botId());

            // When
            executeSendBotMessageFlow(
                    aliceId(), botId(), conversationId, 1021L, "Hello bot", "session-1");

            // Then - Push topic should receive message for sender's other devices
            assertTrue(
                    pushMessageProducer.hasMessages(),
                    "Push topic should receive bot messages for sender's other devices");
        }

        @Test
        void botMessageSentToBothTopics() throws Exception {
            // Given
            registerBot(botId(), BOT_WEBHOOK_URL, BOT_API_KEY);
            String conversationId = generateSingleConversationId(aliceId(), botId());

            // When
            executeSendBotMessageFlow(
                    aliceId(), botId(), conversationId, 1022L, "Hello bot", "session-1");

            // Then - Both topics should receive messages
            assertTrue(botWebhookProducer.hasMessages(), "Bot webhook should receive message");
            assertTrue(pushMessageProducer.hasMessages(), "Push topic should receive message");
        }
    }
}
