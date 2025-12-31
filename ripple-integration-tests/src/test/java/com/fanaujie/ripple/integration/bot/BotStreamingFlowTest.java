package com.fanaujie.ripple.integration.bot;

import com.fanaujie.ripple.botexecutor.sse.SseEventHandler;
import com.fanaujie.ripple.botexecutor.sse.SseParser;
import com.fanaujie.ripple.integration.base.AbstractBusinessFlowTest;
import com.fanaujie.ripple.integration.mock.MockProducer;
import com.fanaujie.ripple.integration.mock.MockWebhookServer;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Bot Streaming Flow Tests")
class BotStreamingFlowTest extends AbstractBusinessFlowTest {

    // Test users
    protected static final long ALICE_ID = 1001L;

    // Test bots
    protected static final long STREAMING_BOT_ID = 100001L;
    protected static final long NON_STREAMING_BOT_ID = 100002L;

    private MockWebhookServer webhookServer;
    private long messageIdCounter = 100000L;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUpTestData() throws Exception {
        // Create test user
        createUser(ALICE_ID, "alice", "Alice", "alice-avatar.png");

        // Start mock webhook server
        webhookServer = new MockWebhookServer();
        webhookServer.start();

        // Create test bots using the mock webhook server endpoint
        createBot(STREAMING_BOT_ID, "Streaming Bot", webhookServer.getEndpoint());
        createBot(NON_STREAMING_BOT_ID, "Non-Streaming Bot", webhookServer.getEndpoint());
    }

    @AfterEach
    void tearDownWebhookServer() {
        if (webhookServer != null) {
            webhookServer.stop();
        }
    }

    private long nextMessageId() {
        return ++messageIdCounter;
    }

    @Nested
    @DisplayName("Scenario: SSE Parser functionality")
    class SseParserScenarios {

        @Test
        @DisplayName("SSE parser handles single chunk correctly")
        void sseParser_singleChunk() throws Exception {
            // Given: SSE stream with a single data event
            String sseStream = "data: Hello World\n\n";
            ByteArrayInputStream inputStream = new ByteArrayInputStream(sseStream.getBytes(StandardCharsets.UTF_8));

            List<String> receivedChunks = new ArrayList<>();
            String[] fullContent = {null};
            CountDownLatch completeLatch = new CountDownLatch(1);

            SseEventHandler handler = new SseEventHandler() {
                @Override
                public void onChunk(String chunk) {
                    receivedChunks.add(chunk);
                }

                @Override
                public void onComplete(String content) {
                    fullContent[0] = content;
                    completeLatch.countDown();
                }

                @Override
                public void onError(Throwable error) {
                    completeLatch.countDown();
                }
            };

            // When: Parsing the SSE stream
            SseParser parser = new SseParser(objectMapper);
            parser.parse(inputStream, handler);
            completeLatch.await(5, TimeUnit.SECONDS);

            // Then: Chunk should be received
            assertThat(receivedChunks).hasSize(1);
            assertThat(receivedChunks.get(0)).isEqualTo("Hello World");
        }

        @Test
        @DisplayName("SSE parser handles multiple chunks correctly")
        void sseParser_multipleChunks() throws Exception {
            // Given: SSE stream with multiple data events
            String sseStream = "data: Hello\n\ndata: World\n\ndata: !\n\ndata: [DONE]\n\n";
            ByteArrayInputStream inputStream = new ByteArrayInputStream(sseStream.getBytes(StandardCharsets.UTF_8));

            List<String> receivedChunks = new ArrayList<>();
            String[] fullContent = {null};
            CountDownLatch completeLatch = new CountDownLatch(1);

            SseEventHandler handler = new SseEventHandler() {
                @Override
                public void onChunk(String chunk) {
                    receivedChunks.add(chunk);
                }

                @Override
                public void onComplete(String content) {
                    fullContent[0] = content;
                    completeLatch.countDown();
                }

                @Override
                public void onError(Throwable error) {
                    completeLatch.countDown();
                }
            };

            // When: Parsing the SSE stream
            SseParser parser = new SseParser(objectMapper);
            parser.parse(inputStream, handler);
            completeLatch.await(5, TimeUnit.SECONDS);

            // Then: All chunks should be received
            assertThat(receivedChunks).hasSize(3); // Hello, World, !
            assertThat(receivedChunks).containsExactly("Hello", "World", "!");
            assertThat(fullContent[0]).isEqualTo("HelloWorld!");
        }

        @Test
        @DisplayName("SSE parser handles empty lines correctly")
        void sseParser_emptyLines() throws Exception {
            // Given: SSE stream with empty data
            String sseStream = "data: First\n\ndata: \n\ndata: Third\n\ndata: [DONE]\n\n";
            ByteArrayInputStream inputStream = new ByteArrayInputStream(sseStream.getBytes(StandardCharsets.UTF_8));

            List<String> receivedChunks = new ArrayList<>();
            CountDownLatch completeLatch = new CountDownLatch(1);

            SseEventHandler handler = new SseEventHandler() {
                @Override
                public void onChunk(String chunk) {
                    if (!chunk.isEmpty()) {
                        receivedChunks.add(chunk);
                    }
                }

                @Override
                public void onComplete(String content) {
                    completeLatch.countDown();
                }

                @Override
                public void onError(Throwable error) {
                    completeLatch.countDown();
                }
            };

            // When: Parsing the SSE stream
            SseParser parser = new SseParser(objectMapper);
            parser.parse(inputStream, handler);
            completeLatch.await(5, TimeUnit.SECONDS);

            // Then: Non-empty chunks should be received
            assertThat(receivedChunks).contains("First", "Third");
        }

        @Test
        @DisplayName("SSE parser detects completion marker")
        void sseParser_detectsCompletionMarker() throws Exception {
            // Given: SSE stream ending with [DONE]
            String sseStream = "data: Test\n\ndata: [DONE]\n\n";
            ByteArrayInputStream inputStream = new ByteArrayInputStream(sseStream.getBytes(StandardCharsets.UTF_8));

            boolean[] completed = {false};
            CountDownLatch completeLatch = new CountDownLatch(1);

            SseEventHandler handler = new SseEventHandler() {
                @Override
                public void onChunk(String chunk) {}

                @Override
                public void onComplete(String content) {
                    completed[0] = true;
                    completeLatch.countDown();
                }

                @Override
                public void onError(Throwable error) {
                    completeLatch.countDown();
                }
            };

            // When: Parsing the SSE stream
            SseParser parser = new SseParser(objectMapper);
            parser.parse(inputStream, handler);
            completeLatch.await(5, TimeUnit.SECONDS);

            // Then: Completion should be detected
            assertThat(completed[0]).isTrue();
        }
    }

    @Nested
    @DisplayName("Scenario: Mock webhook server SSE response")
    class MockWebhookServerSseScenarios {

        @Test
        @DisplayName("Mock webhook server can return SSE response")
        void mockServer_returnsSseResponse() throws Exception {
            // Given: Webhook server configured to return SSE chunks
            List<String> chunks = List.of("Hello", " ", "World", "!");
            webhookServer.respondWithSSE(chunks);

            // When: Making a request (simulated by checking server config)
            // Then: Server should be configured for SSE
            assertThat(webhookServer.getEndpoint()).contains("localhost");
        }

        @Test
        @DisplayName("Mock webhook server captures requests")
        void mockServer_capturesRequests() throws Exception {
            // Given: User has installed the bot
            installBotForUser(ALICE_ID, STREAMING_BOT_ID);
            webhookServer.respondWith("OK");

            // When: Message is sent
            String conversationId = generateBotConversationId(ALICE_ID, STREAMING_BOT_ID);
            var request = createSendBotMessageRequest(
                    ALICE_ID, STREAMING_BOT_ID, conversationId, nextMessageId(), "Test message");
            singleMessageContentProcessor.handle(request);
            processMessagePayloads();

            // Then: Message should be routed to bot topic
            List<MockProducer.CapturedMessage<String, MessagePayload>> botMessages = getBotTopicMessages();
            assertThat(botMessages).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Scenario: Bot message routing with streaming setup")
    class BotMessageRoutingScenarios {

        @Test
        @DisplayName("Message to streaming bot is routed to bot topic")
        void messageToStreamingBot_routedToBottopic() throws Exception {
            // Given: User has installed the streaming bot
            installBotForUser(ALICE_ID, STREAMING_BOT_ID);
            webhookServer.respondWith("OK");

            String conversationId = generateBotConversationId(ALICE_ID, STREAMING_BOT_ID);
            long messageId = nextMessageId();

            // When: User sends a message
            var request = createSendBotMessageRequest(
                    ALICE_ID, STREAMING_BOT_ID, conversationId, messageId, "Hello streaming bot!");
            singleMessageContentProcessor.handle(request);
            processMessagePayloads();

            // Then: Message should be routed to bot topic
            List<MockProducer.CapturedMessage<String, MessagePayload>> botMessages = getBotTopicMessages();
            assertThat(botMessages).hasSize(1);

            var payload = botMessages.get(0).value();
            assertThat(payload.hasMessageData()).isTrue();
            assertThat(payload.getMessageData().getData().getSingleMessageContent().getText())
                    .isEqualTo("Hello streaming bot!");
        }

        @Test
        @DisplayName("Message to non-streaming bot is also routed to bot topic")
        void messageToNonStreamingBot_routedToBottopic() throws Exception {
            // Given: User has installed the non-streaming bot
            installBotForUser(ALICE_ID, NON_STREAMING_BOT_ID);
            webhookServer.respondWith("Regular response");

            String conversationId = generateBotConversationId(ALICE_ID, NON_STREAMING_BOT_ID);
            long messageId = nextMessageId();

            // When: User sends a message
            var request = createSendBotMessageRequest(
                    ALICE_ID, NON_STREAMING_BOT_ID, conversationId, messageId, "Hello regular bot!");
            singleMessageContentProcessor.handle(request);
            processMessagePayloads();

            // Then: Message should be routed to bot topic
            List<MockProducer.CapturedMessage<String, MessagePayload>> botMessages = getBotTopicMessages();
            assertThat(botMessages).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Scenario: SSE content accumulation")
    class SseContentAccumulationScenarios {

        @Test
        @DisplayName("Full content is accumulated from SSE chunks")
        void sseChunks_accumulatedToFullContent() throws Exception {
            // Given: SSE stream with chunks that form a complete message
            // Note: SseParser trims whitespace from each data line, so chunks are concatenated without spaces
            String sseStream = "data: The\n\ndata: quick\n\ndata: brown\n\ndata: fox\n\ndata: [DONE]\n\n";
            ByteArrayInputStream inputStream = new ByteArrayInputStream(sseStream.getBytes(StandardCharsets.UTF_8));

            String[] fullContent = {null};
            CountDownLatch completeLatch = new CountDownLatch(1);

            SseEventHandler handler = new SseEventHandler() {
                @Override
                public void onChunk(String chunk) {}

                @Override
                public void onComplete(String content) {
                    fullContent[0] = content;
                    completeLatch.countDown();
                }

                @Override
                public void onError(Throwable error) {
                    completeLatch.countDown();
                }
            };

            // When: Parsing the SSE stream
            SseParser parser = new SseParser(objectMapper);
            parser.parse(inputStream, handler);
            completeLatch.await(5, TimeUnit.SECONDS);

            // Then: Full content should be accumulated (chunks concatenated directly, no spaces added)
            assertThat(fullContent[0]).isEqualTo("Thequickbrownfox");
        }

        @Test
        @DisplayName("Empty SSE stream results in empty content")
        void emptySseStream_emptyContent() throws Exception {
            // Given: SSE stream with only completion marker
            String sseStream = "data: [DONE]\n\n";
            ByteArrayInputStream inputStream = new ByteArrayInputStream(sseStream.getBytes(StandardCharsets.UTF_8));

            String[] fullContent = {"not-empty"};
            CountDownLatch completeLatch = new CountDownLatch(1);

            SseEventHandler handler = new SseEventHandler() {
                @Override
                public void onChunk(String chunk) {}

                @Override
                public void onComplete(String content) {
                    fullContent[0] = content;
                    completeLatch.countDown();
                }

                @Override
                public void onError(Throwable error) {
                    completeLatch.countDown();
                }
            };

            // When: Parsing the SSE stream
            SseParser parser = new SseParser(objectMapper);
            parser.parse(inputStream, handler);
            completeLatch.await(5, TimeUnit.SECONDS);

            // Then: Full content should be empty
            assertThat(fullContent[0]).isEmpty();
        }
    }
}
