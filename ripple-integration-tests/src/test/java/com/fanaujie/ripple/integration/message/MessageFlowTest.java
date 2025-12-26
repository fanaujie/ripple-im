package com.fanaujie.ripple.integration.message;

import com.fanaujie.ripple.integration.base.AbstractBusinessFlowTest;
import com.fanaujie.ripple.storage.model.Message;
import com.fanaujie.ripple.storage.model.Messages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Message Business Flow Tests")
class MessageFlowTest extends AbstractBusinessFlowTest {

    // Test users
    protected static final long ALICE_ID = 1001L;
    protected static final long BOB_ID = 2001L;
    protected static final long CHARLIE_ID = 3001L;

    // Test group
    protected static final long GROUP_ID = 10001L;

    private long messageIdCounter = 100000L;

    @BeforeEach
    void setUpTestUsers() {
        createUser(ALICE_ID, "alice", "Alice", "alice-avatar.png");
        createUser(BOB_ID, "bob", "Bob", "bob-avatar.png");
        createUser(CHARLIE_ID, "charlie", "Charlie", "charlie-avatar.png");
    }

    private long nextMessageId() {
        return ++messageIdCounter;
    }

    @Nested
    @DisplayName("Scenario: Send Single Message")
    class SingleMessageScenarios {

        @Test
        @DisplayName("When Alice sends a message to Bob, the message should be stored")
        void sendMessage_shouldStoreMessage() throws Exception {
            // Given: Alice and Bob are friends
            executeAddFriendFlow(ALICE_ID, BOB_ID);
            executeAddFriendFlow(BOB_ID, ALICE_ID);

            String conversationId = generateSingleConversationId(ALICE_ID, BOB_ID);
            long messageId = nextMessageId();

            // When: Alice sends a message to Bob
            executeSendMessageFlow(ALICE_ID, BOB_ID, conversationId, messageId, "Hello Bob!");

            // Then: The message should be stored in the conversation
            Messages result = storageFacade.getMessages(conversationId, 0L, 10);
            List<Message> messages = result.getMessages();
            assertThat(messages).hasSize(1);
            assertThat(messages.get(0).getText()).isEqualTo("Hello Bob!");
            assertThat(messages.get(0).getSenderId()).isEqualTo(ALICE_ID);
        }

        @Test
        @DisplayName("When users exchange multiple messages, all should be stored in order")
        void sendMultipleMessages_shouldStoreAllInOrder() throws Exception {
            // Given: Alice and Bob are friends
            executeAddFriendFlow(ALICE_ID, BOB_ID);
            executeAddFriendFlow(BOB_ID, ALICE_ID);

            String conversationId = generateSingleConversationId(ALICE_ID, BOB_ID);

            // When: They exchange messages
            executeSendMessageFlow(ALICE_ID, BOB_ID, conversationId, nextMessageId(), "Hi Bob!");
            executeSendMessageFlow(BOB_ID, ALICE_ID, conversationId, nextMessageId(), "Hi Alice!");
            executeSendMessageFlow(
                    ALICE_ID, BOB_ID, conversationId, nextMessageId(), "How are you?");

            // Then: All messages should be stored
            List<Message> messages =
                    storageFacade.getMessages(conversationId, 0L, 10).getMessages();
            assertThat(messages).hasSize(3);
        }

        @Test
        @DisplayName("When Alice sends message to Bob, a conversation should be created for both")
        void sendMessage_shouldCreateConversation() throws Exception {
            // Given: Alice and Bob are friends
            executeAddFriendFlow(ALICE_ID, BOB_ID);
            executeAddFriendFlow(BOB_ID, ALICE_ID);

            String conversationId = generateSingleConversationId(ALICE_ID, BOB_ID);
            long messageId = nextMessageId();

            // When: Alice sends a message to Bob
            executeSendMessageFlow(ALICE_ID, BOB_ID, conversationId, messageId, "Hello!");

            // Then: Both users should have the conversation
            boolean aliceHasConversation =
                    storageFacade.existsByConversationId(conversationId, ALICE_ID);
            boolean bobHasConversation =
                    storageFacade.existsByConversationId(conversationId, BOB_ID);

            assertThat(aliceHasConversation).isTrue();
            assertThat(bobHasConversation).isTrue();
        }

        @Test
        @DisplayName("When a blocked user tries to send message, it should not be delivered")
        void sendMessage_whenBlocked_shouldNotDeliver() throws Exception {
            // Given: Alice and Bob are friends, but Bob blocks Alice
            executeAddFriendFlow(ALICE_ID, BOB_ID);
            executeAddFriendFlow(BOB_ID, ALICE_ID);
            executeBlockFriendFlow(BOB_ID, ALICE_ID);

            String conversationId = generateSingleConversationId(ALICE_ID, BOB_ID);
            long messageId = nextMessageId();

            // When: Alice tries to send a message to Bob
            executeSendMessageFlow(ALICE_ID, BOB_ID, conversationId, messageId, "Hello?");

            // Then: The message should not be stored (blocked)
            List<Message> messages =
                    storageFacade.getMessages(conversationId, 0L, 10).getMessages();
            assertThat(messages).isEmpty();
        }
    }

    @Nested
    @DisplayName("Scenario: Send Group Message")
    class GroupMessageScenarios {

        @Test
        @DisplayName("When Alice sends a group message, it should be stored")
        void sendGroupMessage_shouldStoreMessage() throws Exception {
            // Given: A group with Alice, Bob, and Charlie
            createGroup(
                    GROUP_ID, List.of(ALICE_ID, BOB_ID, CHARLIE_ID), System.currentTimeMillis());

            String conversationId = generateGroupConversationId(GROUP_ID);
            long messageId = nextMessageId();

            // When: Alice sends a message to the group
            executeSendGroupMessageFlow(
                    ALICE_ID, GROUP_ID, conversationId, messageId, "Hello everyone!");

            // Then: The message should be stored
            List<Message> messages =
                    storageFacade.getMessages(conversationId, 0L, 10).getMessages();
            assertThat(messages).hasSize(1);
            assertThat(messages.get(0).getText()).isEqualTo("Hello everyone!");
            assertThat(messages.get(0).getSenderId()).isEqualTo(ALICE_ID);
            assertThat(messages.get(0).getGroupId()).isEqualTo(GROUP_ID);
        }

        @Test
        @DisplayName("When multiple members send messages, all should be stored")
        void sendMultipleGroupMessages_shouldStoreAll() throws Exception {
            // Given: A group with Alice, Bob, and Charlie
            createGroup(
                    GROUP_ID, List.of(ALICE_ID, BOB_ID, CHARLIE_ID), System.currentTimeMillis());

            String conversationId = generateGroupConversationId(GROUP_ID);

            // When: Multiple members send messages
            executeSendGroupMessageFlow(
                    ALICE_ID, GROUP_ID, conversationId, nextMessageId(), "Hello!");
            executeSendGroupMessageFlow(
                    BOB_ID, GROUP_ID, conversationId, nextMessageId(), "Hi Alice!");
            executeSendGroupMessageFlow(
                    CHARLIE_ID, GROUP_ID, conversationId, nextMessageId(), "Hey everyone!");

            // Then: All messages should be stored
            List<Message> messages =
                    storageFacade.getMessages(conversationId, 0L, 10).getMessages();
            assertThat(messages).hasSize(3);
        }
    }

    @Nested
    @DisplayName("Scenario: Conversation Unread Count")
    class UnreadCountScenarios {

        @Test
        @DisplayName("When Alice sends message to Bob, Bob should have unread count")
        void sendMessage_shouldIncrementUnreadCount() throws Exception {
            // Given: Alice and Bob are friends
            executeAddFriendFlow(ALICE_ID, BOB_ID);
            executeAddFriendFlow(BOB_ID, ALICE_ID);

            String conversationId = generateSingleConversationId(ALICE_ID, BOB_ID);

            // When: Alice sends messages to Bob
            executeSendMessageFlow(ALICE_ID, BOB_ID, conversationId, nextMessageId(), "Hello!");
            executeSendMessageFlow(
                    ALICE_ID, BOB_ID, conversationId, nextMessageId(), "Are you there?");

            // Then: Bob should have unread count (from mock storage)
            int unreadCount = conversationSummaryStorage.getUnreadCount(BOB_ID, conversationId);
            assertThat(unreadCount).isEqualTo(2);

            // And: Alice should have no unread (sender)
            int aliceUnread = conversationSummaryStorage.getUnreadCount(ALICE_ID, conversationId);
            assertThat(aliceUnread).isEqualTo(0);
        }

        @Test
        @DisplayName("When Bob reads the conversation, unread count should reset")
        void readConversation_shouldResetUnreadCount() throws Exception {
            // Given: Alice sends messages to Bob
            executeAddFriendFlow(ALICE_ID, BOB_ID);
            executeAddFriendFlow(BOB_ID, ALICE_ID);

            String conversationId = generateSingleConversationId(ALICE_ID, BOB_ID);
            executeSendMessageFlow(ALICE_ID, BOB_ID, conversationId, nextMessageId(), "Hello!");
            executeSendMessageFlow(
                    ALICE_ID, BOB_ID, conversationId, nextMessageId(), "Hello again!");

            // Verify Bob has unread
            assertThat(conversationSummaryStorage.getUnreadCount(BOB_ID, conversationId))
                    .isEqualTo(2);

            // When: Bob reads the conversation
            conversationSummaryStorage.resetUnreadCount(BOB_ID, conversationId);

            // Then: Bob should have no unread
            assertThat(conversationSummaryStorage.getUnreadCount(BOB_ID, conversationId))
                    .isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Scenario: Complex Message Flows")
    class ComplexMessageFlowScenarios {

        @Test
        @DisplayName("User can have multiple conversations simultaneously")
        void multipleConversations() throws Exception {
            // Given: Alice is friends with both Bob and Charlie
            executeAddFriendFlow(ALICE_ID, BOB_ID);
            executeAddFriendFlow(BOB_ID, ALICE_ID);
            executeAddFriendFlow(ALICE_ID, CHARLIE_ID);
            executeAddFriendFlow(CHARLIE_ID, ALICE_ID);

            String aliceBobConversation = generateSingleConversationId(ALICE_ID, BOB_ID);
            String aliceCharlieConversation = generateSingleConversationId(ALICE_ID, CHARLIE_ID);

            // When: Alice chats with both
            executeSendMessageFlow(
                    ALICE_ID, BOB_ID, aliceBobConversation, nextMessageId(), "Hi Bob!");
            executeSendMessageFlow(
                    ALICE_ID, CHARLIE_ID, aliceCharlieConversation, nextMessageId(), "Hi Charlie!");
            executeSendMessageFlow(
                    BOB_ID, ALICE_ID, aliceBobConversation, nextMessageId(), "Hey Alice!");

            // Then: Each conversation should have its own messages
            List<Message> bobMessages =
                    storageFacade.getMessages(aliceBobConversation, 0L, 10).getMessages();
            List<Message> charlieMessages =
                    storageFacade.getMessages(aliceCharlieConversation, 0L, 10).getMessages();

            assertThat(bobMessages).hasSize(2);
            assertThat(charlieMessages).hasSize(1);
        }

        @Test
        @DisplayName("Friend and group conversations are independent")
        void friendAndGroupConversations() throws Exception {
            // Given: Alice and Bob are friends, and they're in a group with Charlie
            executeAddFriendFlow(ALICE_ID, BOB_ID);
            executeAddFriendFlow(BOB_ID, ALICE_ID);
            createGroup(
                    GROUP_ID, List.of(ALICE_ID, BOB_ID, CHARLIE_ID), System.currentTimeMillis());

            String singleConversation = generateSingleConversationId(ALICE_ID, BOB_ID);
            String groupConversation = generateGroupConversationId(GROUP_ID);

            // When: Alice sends messages in both conversations
            executeSendMessageFlow(
                    ALICE_ID,
                    BOB_ID,
                    singleConversation,
                    nextMessageId(),
                    "Private message to Bob");
            executeSendGroupMessageFlow(
                    ALICE_ID, GROUP_ID, groupConversation, nextMessageId(), "Group message");

            // Then: Messages are in separate conversations
            List<Message> singleMessages =
                    storageFacade.getMessages(singleConversation, 0L, 10).getMessages();
            List<Message> groupMessages =
                    storageFacade.getMessages(groupConversation, 0L, 10).getMessages();

            assertThat(singleMessages).hasSize(1);
            assertThat(singleMessages.get(0).getText()).isEqualTo("Private message to Bob");

            assertThat(groupMessages).hasSize(1);
            assertThat(groupMessages.get(0).getText()).isEqualTo("Group message");
        }

        @Test
        @DisplayName("Complete messaging flow with friends becoming strangers")
        void friendsToStrangersMessaging() throws Exception {
            // Step 1: Alice and Bob become friends
            executeAddFriendFlow(ALICE_ID, BOB_ID);
            executeAddFriendFlow(BOB_ID, ALICE_ID);

            String conversationId = generateSingleConversationId(ALICE_ID, BOB_ID);

            // Step 2: They chat
            executeSendMessageFlow(ALICE_ID, BOB_ID, conversationId, nextMessageId(), "Hi friend!");
            executeSendMessageFlow(BOB_ID, ALICE_ID, conversationId, nextMessageId(), "Hi!");

            // Verify messages
            List<Message> messages =
                    storageFacade.getMessages(conversationId, 0L, 10).getMessages();
            assertThat(messages).hasSize(2);

            // Step 3: Alice removes Bob
            executeRemoveFriendFlow(ALICE_ID, BOB_ID);

            // Step 4: Alice can still see old messages
            messages = storageFacade.getMessages(conversationId, 0L, 10).getMessages();
            assertThat(messages).hasSize(2);

            // The conversation history is preserved even after unfriending
        }
    }
}
