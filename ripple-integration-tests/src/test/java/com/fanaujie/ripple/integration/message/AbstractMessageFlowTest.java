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
public abstract class AbstractMessageFlowTest extends AbstractBusinessFlowTest {

    protected long aliceId() {
        return testIdBase + 1;
    }

    protected long bobId() {
        return testIdBase + 2;
    }

    protected long charlieId() {
        return testIdBase + 3;
    }

    protected long groupId() {
        return testIdBase + 10001;
    }

    private long messageIdCounter = 100000L;

    @BeforeEach
    void setUpTestUsers() {
        createUser(aliceId(), "alice-" + testIdBase, "Alice", "alice-avatar.png");
        createUser(bobId(), "bob-" + testIdBase, "Bob", "bob-avatar.png");
        createUser(charlieId(), "charlie-" + testIdBase, "Charlie", "charlie-avatar.png");
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
            executeAddFriendFlow(aliceId(), bobId());
            executeAddFriendFlow(bobId(), aliceId());

            String conversationId = generateSingleConversationId(aliceId(), bobId());
            long messageId = nextMessageId();

            // When: Alice sends a message to Bob
            executeSendMessageFlow(aliceId(), bobId(), conversationId, messageId, "Hello Bob!");

            // Then: The message should be stored in the conversation
            Messages result = storageFacade.getMessages(conversationId, 0L, 10);
            List<Message> messages = result.getMessages();
            assertThat(messages).hasSize(1);
            assertThat(messages.get(0).getText()).isEqualTo("Hello Bob!");
            assertThat(messages.get(0).getSenderId()).isEqualTo(aliceId());
        }

        @Test
        @DisplayName("When users exchange multiple messages, all should be stored in order")
        void sendMultipleMessages_shouldStoreAllInOrder() throws Exception {
            // Given: Alice and Bob are friends
            executeAddFriendFlow(aliceId(), bobId());
            executeAddFriendFlow(bobId(), aliceId());

            String conversationId = generateSingleConversationId(aliceId(), bobId());

            // When: They exchange messages
            executeSendMessageFlow(aliceId(), bobId(), conversationId, nextMessageId(), "Hi Bob!");
            executeSendMessageFlow(
                    bobId(), aliceId(), conversationId, nextMessageId(), "Hi Alice!");
            executeSendMessageFlow(
                    aliceId(), bobId(), conversationId, nextMessageId(), "How are you?");

            // Then: All messages should be stored
            List<Message> messages =
                    storageFacade.getMessages(conversationId, 0L, 10).getMessages();
            assertThat(messages).hasSize(3);
        }

        @Test
        @DisplayName("When Alice sends message to Bob, a conversation should be created for both")
        void sendMessage_shouldCreateConversation() throws Exception {
            // Given: Alice and Bob are friends
            executeAddFriendFlow(aliceId(), bobId());
            executeAddFriendFlow(bobId(), aliceId());

            String conversationId = generateSingleConversationId(aliceId(), bobId());
            long messageId = nextMessageId();

            // When: Alice sends a message to Bob
            executeSendMessageFlow(aliceId(), bobId(), conversationId, messageId, "Hello!");

            // Then: Both users should have the conversation
            boolean aliceHasConversation =
                    storageFacade.existsByConversationId(conversationId, aliceId());
            boolean bobHasConversation =
                    storageFacade.existsByConversationId(conversationId, bobId());

            assertThat(aliceHasConversation).isTrue();
            assertThat(bobHasConversation).isTrue();
        }

        @Test
        @DisplayName("When a blocked user tries to send message, it should not be delivered")
        void sendMessage_whenBlocked_shouldNotDeliver() throws Exception {
            // Given: Alice and Bob are friends, but Bob blocks Alice
            executeAddFriendFlow(aliceId(), bobId());
            executeAddFriendFlow(bobId(), aliceId());
            executeBlockFriendFlow(bobId(), aliceId());

            String conversationId = generateSingleConversationId(aliceId(), bobId());
            long messageId = nextMessageId();

            // When: Alice tries to send a message to Bob
            executeSendMessageFlow(aliceId(), bobId(), conversationId, messageId, "Hello?");

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
                    groupId(),
                    List.of(aliceId(), bobId(), charlieId()),
                    System.currentTimeMillis());

            String conversationId = generateGroupConversationId(groupId());
            long messageId = nextMessageId();

            // When: Alice sends a message to the group
            executeSendGroupMessageFlow(
                    aliceId(), groupId(), conversationId, messageId, "Hello everyone!");

            // Then: The message should be stored
            List<Message> messages =
                    storageFacade.getMessages(conversationId, 0L, 10).getMessages();
            assertThat(messages).hasSize(1);
            assertThat(messages.get(0).getText()).isEqualTo("Hello everyone!");
            assertThat(messages.get(0).getSenderId()).isEqualTo(aliceId());
            assertThat(messages.get(0).getGroupId()).isEqualTo(groupId());
        }

        @Test
        @DisplayName("When multiple members send messages, all should be stored")
        void sendMultipleGroupMessages_shouldStoreAll() throws Exception {
            // Given: A group with Alice, Bob, and Charlie
            createGroup(
                    groupId(),
                    List.of(aliceId(), bobId(), charlieId()),
                    System.currentTimeMillis());

            String conversationId = generateGroupConversationId(groupId());

            // When: Multiple members send messages
            executeSendGroupMessageFlow(
                    aliceId(), groupId(), conversationId, nextMessageId(), "Hello!");
            executeSendGroupMessageFlow(
                    bobId(), groupId(), conversationId, nextMessageId(), "Hi Alice!");
            executeSendGroupMessageFlow(
                    charlieId(), groupId(), conversationId, nextMessageId(), "Hey everyone!");

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
            executeAddFriendFlow(aliceId(), bobId());
            executeAddFriendFlow(bobId(), aliceId());

            String conversationId = generateSingleConversationId(aliceId(), bobId());

            // When: Alice sends messages to Bob
            executeSendMessageFlow(aliceId(), bobId(), conversationId, nextMessageId(), "Hello!");
            executeSendMessageFlow(
                    aliceId(), bobId(), conversationId, nextMessageId(), "Are you there?");

            // Then: Bob should have unread count (from mock storage)
            int unreadCount = conversationSummaryStorage.getUnreadCount(bobId(), conversationId);
            assertThat(unreadCount).isEqualTo(2);

            // And: Alice should have no unread (sender)
            int aliceUnread = conversationSummaryStorage.getUnreadCount(aliceId(), conversationId);
            assertThat(aliceUnread).isEqualTo(0);
        }

        @Test
        @DisplayName("When Bob reads the conversation, unread count should reset")
        void readConversation_shouldResetUnreadCount() throws Exception {
            // Given: Alice sends messages to Bob
            executeAddFriendFlow(aliceId(), bobId());
            executeAddFriendFlow(bobId(), aliceId());

            String conversationId = generateSingleConversationId(aliceId(), bobId());
            executeSendMessageFlow(aliceId(), bobId(), conversationId, nextMessageId(), "Hello!");
            executeSendMessageFlow(
                    aliceId(), bobId(), conversationId, nextMessageId(), "Hello again!");

            // Verify Bob has unread
            assertThat(conversationSummaryStorage.getUnreadCount(bobId(), conversationId))
                    .isEqualTo(2);

            // When: Bob reads the conversation
            conversationSummaryStorage.resetUnreadCount(bobId(), conversationId);

            // Then: Bob should have no unread
            assertThat(conversationSummaryStorage.getUnreadCount(bobId(), conversationId))
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
            executeAddFriendFlow(aliceId(), bobId());
            executeAddFriendFlow(bobId(), aliceId());
            executeAddFriendFlow(aliceId(), charlieId());
            executeAddFriendFlow(charlieId(), aliceId());

            String aliceBobConversation = generateSingleConversationId(aliceId(), bobId());
            String aliceCharlieConversation = generateSingleConversationId(aliceId(), charlieId());

            // When: Alice chats with both
            executeSendMessageFlow(
                    aliceId(), bobId(), aliceBobConversation, nextMessageId(), "Hi Bob!");
            executeSendMessageFlow(
                    aliceId(),
                    charlieId(),
                    aliceCharlieConversation,
                    nextMessageId(),
                    "Hi Charlie!");
            executeSendMessageFlow(
                    bobId(), aliceId(), aliceBobConversation, nextMessageId(), "Hey Alice!");

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
            executeAddFriendFlow(aliceId(), bobId());
            executeAddFriendFlow(bobId(), aliceId());
            createGroup(
                    groupId(),
                    List.of(aliceId(), bobId(), charlieId()),
                    System.currentTimeMillis());

            String singleConversation = generateSingleConversationId(aliceId(), bobId());
            String groupConversation = generateGroupConversationId(groupId());

            // When: Alice sends messages in both conversations
            executeSendMessageFlow(
                    aliceId(),
                    bobId(),
                    singleConversation,
                    nextMessageId(),
                    "Private message to Bob");
            executeSendGroupMessageFlow(
                    aliceId(), groupId(), groupConversation, nextMessageId(), "Group message");

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
            executeAddFriendFlow(aliceId(), bobId());
            executeAddFriendFlow(bobId(), aliceId());

            String conversationId = generateSingleConversationId(aliceId(), bobId());

            // Step 2: They chat
            executeSendMessageFlow(
                    aliceId(), bobId(), conversationId, nextMessageId(), "Hi friend!");
            executeSendMessageFlow(bobId(), aliceId(), conversationId, nextMessageId(), "Hi!");

            // Verify messages
            List<Message> messages =
                    storageFacade.getMessages(conversationId, 0L, 10).getMessages();
            assertThat(messages).hasSize(2);

            // Step 3: Alice removes Bob
            executeRemoveFriendFlow(aliceId(), bobId());

            // Step 4: Alice can still see old messages
            messages = storageFacade.getMessages(conversationId, 0L, 10).getMessages();
            assertThat(messages).hasSize(2);

            // The conversation history is preserved even after unfriending
        }
    }
}
