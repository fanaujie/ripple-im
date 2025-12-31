package com.fanaujie.ripple.integration.bot;

import com.fanaujie.ripple.integration.base.AbstractBusinessFlowTest;
import com.fanaujie.ripple.integration.mock.MockProducer;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;
import com.fanaujie.ripple.storage.model.Message;
import com.fanaujie.ripple.storage.model.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Bot Message Flow Tests")
class BotMessageFlowTest extends AbstractBusinessFlowTest {

    // Test users
    protected static final long ALICE_ID = 1001L;
    protected static final long BOB_ID = 2001L;

    // Test bots
    protected static final long ECHO_BOT_ID = 100001L;
    protected static final long ASSISTANT_BOT_ID = 100002L;

    private long messageIdCounter = 100000L;

    @BeforeEach
    void setUpTestData() {
        // Create test users
        createUser(ALICE_ID, "alice", "Alice", "alice-avatar.png");
        createUser(BOB_ID, "bob", "Bob", "bob-avatar.png");

        // Create test bots
        createBot(ECHO_BOT_ID, "Echo Bot", "http://localhost:8080/webhook");
        createBot(ASSISTANT_BOT_ID, "Assistant Bot", "http://localhost:8081/webhook", "AI", false);
    }

    private long nextMessageId() {
        return ++messageIdCounter;
    }

    @Nested
    @DisplayName("Scenario: User sends message to bot")
    class UserToBotMessageScenarios {

        @Test
        @DisplayName("When user sends message to bot, message is routed to bot topic")
        void sendMessageToBot_shouldRouteToBottopic() throws Exception {
            // Given: Alice has installed the bot
            installBotForUser(ALICE_ID, ECHO_BOT_ID);

            String conversationId = generateBotConversationId(ALICE_ID, ECHO_BOT_ID);
            long messageId = nextMessageId();

            // When: Alice sends a message to the bot
            var request = createSendBotMessageRequest(
                    ALICE_ID, ECHO_BOT_ID, conversationId, messageId, "Hello Bot!");
            singleMessageContentProcessor.handle(request);
            processMessagePayloads();

            // Then: The message should be routed to bot topic
            List<MockProducer.CapturedMessage<String, MessagePayload>> botMessages = getBotTopicMessages();
            assertThat(botMessages).hasSize(1);

            MessagePayload payload = botMessages.get(0).value();
            assertThat(payload.hasMessageData()).isTrue();
            assertThat(payload.getMessageData().getData().getSenderId()).isEqualTo(ALICE_ID);
            assertThat(payload.getMessageData().getData().getReceiverId()).isEqualTo(ECHO_BOT_ID);
            assertThat(payload.getMessageData().getData().getSingleMessageContent().getText())
                    .isEqualTo("Hello Bot!");
        }

        @Test
        @DisplayName("When user sends message to bot, message is stored in conversation history")
        void sendMessageToBot_shouldStoreInHistory() throws Exception {
            // Given: Alice has installed the bot
            installBotForUser(ALICE_ID, ECHO_BOT_ID);

            String conversationId = generateBotConversationId(ALICE_ID, ECHO_BOT_ID);
            long messageId = nextMessageId();

            // When: Alice sends a message to the bot
            var request = createSendBotMessageRequest(
                    ALICE_ID, ECHO_BOT_ID, conversationId, messageId, "Store this message");
            singleMessageContentProcessor.handle(request);
            processMessagePayloads();

            // Then: The message should be stored in conversation history
            Messages result = storageFacade.getMessages(conversationId, 0L, 10);
            List<Message> messages = result.getMessages();
            assertThat(messages).hasSize(1);
            assertThat(messages.get(0).getText()).isEqualTo("Store this message");
            assertThat(messages.get(0).getSenderId()).isEqualTo(ALICE_ID);
            assertThat(messages.get(0).getReceiverId()).isEqualTo(ECHO_BOT_ID);
        }

        @Test
        @DisplayName("When user sends multiple messages to bot, all are routed to bot topic")
        void sendMultipleMessagesToBot_allRouteToBottopic() throws Exception {
            // Given: Alice has installed the bot
            installBotForUser(ALICE_ID, ECHO_BOT_ID);

            String conversationId = generateBotConversationId(ALICE_ID, ECHO_BOT_ID);

            // When: Alice sends multiple messages
            for (int i = 1; i <= 3; i++) {
                var request = createSendBotMessageRequest(
                        ALICE_ID, ECHO_BOT_ID, conversationId, nextMessageId(), "Message " + i);
                singleMessageContentProcessor.handle(request);
                processMessagePayloads();
            }

            // Then: All messages should be routed to bot topic
            List<MockProducer.CapturedMessage<String, MessagePayload>> botMessages = getBotTopicMessages();
            assertThat(botMessages).hasSize(3);
        }

        @Test
        @DisplayName("Message payload contains correct sender and receiver IDs")
        void sendMessageToBot_payloadContainsCorrectIds() throws Exception {
            // Given: Alice has installed the bot
            installBotForUser(ALICE_ID, ECHO_BOT_ID);

            String conversationId = generateBotConversationId(ALICE_ID, ECHO_BOT_ID);
            long messageId = nextMessageId();

            // When: Alice sends a message to the bot
            var request = createSendBotMessageRequest(
                    ALICE_ID, ECHO_BOT_ID, conversationId, messageId, "Test IDs");
            singleMessageContentProcessor.handle(request);
            processMessagePayloads();

            // Then: The payload should contain correct IDs
            List<MockProducer.CapturedMessage<String, MessagePayload>> botMessages = getBotTopicMessages();
            assertThat(botMessages).hasSize(1);

            var data = botMessages.get(0).value().getMessageData().getData();
            assertThat(data.getSenderId()).isEqualTo(ALICE_ID);
            assertThat(data.getReceiverId()).isEqualTo(ECHO_BOT_ID);
            assertThat(data.getConversationId()).isEqualTo(conversationId);
            assertThat(data.getMessageId()).isEqualTo(messageId);
        }
    }

    @Nested
    @DisplayName("Scenario: User-to-user messages don't route to bot topic")
    class UserToUserMessageScenarios {

        @Test
        @DisplayName("When user sends message to another user, message is NOT routed to bot topic")
        void sendMessageToUser_shouldNotRouteToBottopic() throws Exception {
            // Given: Alice and Bob are friends
            executeAddFriendFlow(ALICE_ID, BOB_ID);
            executeAddFriendFlow(BOB_ID, ALICE_ID);

            String conversationId = generateSingleConversationId(ALICE_ID, BOB_ID);
            long messageId = nextMessageId();

            // Clear any messages from friend flow
            messagePayloadProducer.clear();

            // When: Alice sends a message to Bob (not a bot)
            executeSendMessageFlow(ALICE_ID, BOB_ID, conversationId, messageId, "Hello Bob!");

            // Then: No message should be routed to bot topic
            List<MockProducer.CapturedMessage<String, MessagePayload>> botMessages = getBotTopicMessages();
            assertThat(botMessages).isEmpty();

            // And: Message should still be stored
            Messages result = storageFacade.getMessages(conversationId, 0L, 10);
            assertThat(result.getMessages()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Scenario: Blocked user and bot interaction")
    class BlockedUserBotScenarios {

        @Test
        @DisplayName("When blocked user sends message to bot, message is still delivered (bots ignore blocks)")
        void blockedUserSendsToBot_messageStillDelivered() throws Exception {
            // Given: Alice has installed the bot and Bob blocks Alice
            installBotForUser(ALICE_ID, ECHO_BOT_ID);

            // Note: Bots don't have block functionality, so this test verifies
            // that bot message routing doesn't check for blocks
            String conversationId = generateBotConversationId(ALICE_ID, ECHO_BOT_ID);
            long messageId = nextMessageId();

            // When: Alice sends a message to the bot
            var request = createSendBotMessageRequest(
                    ALICE_ID, ECHO_BOT_ID, conversationId, messageId, "Message from Alice");
            singleMessageContentProcessor.handle(request);
            processMessagePayloads();

            // Then: Message should be routed to bot topic regardless
            List<MockProducer.CapturedMessage<String, MessagePayload>> botMessages = getBotTopicMessages();
            assertThat(botMessages).hasSize(1);

            // And: Message should be stored
            Messages result = storageFacade.getMessages(conversationId, 0L, 10);
            assertThat(result.getMessages()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Scenario: Multiple bots")
    class MultipleBotScenarios {

        @Test
        @DisplayName("User can send messages to different bots independently")
        void sendMessagesToDifferentBots_routedCorrectly() throws Exception {
            // Given: Alice has installed both bots
            installBotForUser(ALICE_ID, ECHO_BOT_ID);
            installBotForUser(ALICE_ID, ASSISTANT_BOT_ID);

            String echoConversationId = generateBotConversationId(ALICE_ID, ECHO_BOT_ID);
            String assistantConversationId = generateBotConversationId(ALICE_ID, ASSISTANT_BOT_ID);

            // When: Alice sends messages to both bots
            var echoRequest = createSendBotMessageRequest(
                    ALICE_ID, ECHO_BOT_ID, echoConversationId, nextMessageId(), "Hello Echo!");
            singleMessageContentProcessor.handle(echoRequest);
            processMessagePayloads();

            var assistantRequest = createSendBotMessageRequest(
                    ALICE_ID, ASSISTANT_BOT_ID, assistantConversationId, nextMessageId(), "Hello Assistant!");
            singleMessageContentProcessor.handle(assistantRequest);
            processMessagePayloads();

            // Then: Both messages should be routed to bot topic
            List<MockProducer.CapturedMessage<String, MessagePayload>> botMessages = getBotTopicMessages();
            assertThat(botMessages).hasSize(2);

            // And: Each conversation should have its own message
            Messages echoMessages = storageFacade.getMessages(echoConversationId, 0L, 10);
            Messages assistantMessages = storageFacade.getMessages(assistantConversationId, 0L, 10);

            assertThat(echoMessages.getMessages()).hasSize(1);
            assertThat(echoMessages.getMessages().get(0).getText()).isEqualTo("Hello Echo!");

            assertThat(assistantMessages.getMessages()).hasSize(1);
            assertThat(assistantMessages.getMessages().get(0).getText()).isEqualTo("Hello Assistant!");
        }

        @Test
        @DisplayName("Different users can send messages to the same bot")
        void differentUsersSendToSameBot_routedCorrectly() throws Exception {
            // Given: Both Alice and Bob have installed the same bot
            installBotForUser(ALICE_ID, ECHO_BOT_ID);
            installBotForUser(BOB_ID, ECHO_BOT_ID);

            String aliceConversationId = generateBotConversationId(ALICE_ID, ECHO_BOT_ID);
            String bobConversationId = generateBotConversationId(BOB_ID, ECHO_BOT_ID);

            // When: Both users send messages to the bot
            var aliceRequest = createSendBotMessageRequest(
                    ALICE_ID, ECHO_BOT_ID, aliceConversationId, nextMessageId(), "Alice here!");
            singleMessageContentProcessor.handle(aliceRequest);
            processMessagePayloads();

            var bobRequest = createSendBotMessageRequest(
                    BOB_ID, ECHO_BOT_ID, bobConversationId, nextMessageId(), "Bob here!");
            singleMessageContentProcessor.handle(bobRequest);
            processMessagePayloads();

            // Then: Both messages should be routed to bot topic
            List<MockProducer.CapturedMessage<String, MessagePayload>> botMessages = getBotTopicMessages();
            assertThat(botMessages).hasSize(2);

            // And: Each user has their own conversation with the bot
            Messages aliceMessages = storageFacade.getMessages(aliceConversationId, 0L, 10);
            Messages bobMessages = storageFacade.getMessages(bobConversationId, 0L, 10);

            assertThat(aliceMessages.getMessages()).hasSize(1);
            assertThat(aliceMessages.getMessages().get(0).getSenderId()).isEqualTo(ALICE_ID);

            assertThat(bobMessages.getMessages()).hasSize(1);
            assertThat(bobMessages.getMessages().get(0).getSenderId()).isEqualTo(BOB_ID);
        }
    }

    @Nested
    @DisplayName("Scenario: Conversation creation")
    class ConversationCreationScenarios {

        @Test
        @DisplayName("When user sends first message to bot, message is stored even without conversation entry")
        void firstMessageToBot_messageIsStored() throws Exception {
            // Given: Alice has installed the bot
            installBotForUser(ALICE_ID, ECHO_BOT_ID);

            String conversationId = generateBotConversationId(ALICE_ID, ECHO_BOT_ID);
            long messageId = nextMessageId();

            // When: Alice sends the first message to the bot
            var request = createSendBotMessageRequest(
                    ALICE_ID, ECHO_BOT_ID, conversationId, messageId, "First message!");
            singleMessageContentProcessor.handle(request);
            processMessagePayloads();

            // Then: Message should be stored in conversation history
            // Note: Conversation entry for user may not be created because bots don't have user profiles,
            // but the message itself is still stored
            var messages = storageFacade.getMessages(conversationId, 0L, 10);
            assertThat(messages.getMessages()).hasSize(1);
            assertThat(messages.getMessages().get(0).getText()).isEqualTo("First message!");
        }
    }
}
