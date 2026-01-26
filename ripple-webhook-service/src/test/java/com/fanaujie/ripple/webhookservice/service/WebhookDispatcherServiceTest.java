package com.fanaujie.ripple.webhookservice.service;

import com.fanaujie.ripple.communication.gateway.GatewayPusher;
import com.fanaujie.ripple.protobuf.msgdispatcher.BotMessageData;
import com.fanaujie.ripple.protobuf.push.SSEEventType;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import com.fanaujie.ripple.webhookservice.http.WebhookHttpClient;
import com.fanaujie.ripple.webhookservice.model.SSEEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebhookDispatcherServiceTest {

    private WebhookHttpClient mockHttpClient;
    private RippleStorageFacade mockStorageFacade;
    private GatewayPusher mockGatewayPusher;
    private WebhookDispatcherService service;

    private static final long SENDER_ID = 1001L;
    private static final long BOT_ID = 5001L;
    private static final String CONVERSATION_ID = "conv-123";
    private static final long MESSAGE_ID = 2001L;
    private static final String WEBHOOK_URL = "https://bot.example.com/webhook";
    private static final String API_KEY = "test-api-key";
    private static final String SESSION_ID = "session-456";
    private static final String MESSAGE_TEXT = "Hello bot";

    @BeforeEach
    void setUp() {
        mockHttpClient = mock(WebhookHttpClient.class);
        mockStorageFacade = mock(RippleStorageFacade.class);
        mockGatewayPusher = mock(GatewayPusher.class);

        service =
                new WebhookDispatcherService(mockHttpClient, mockStorageFacade, mockGatewayPusher);
    }

    private BotMessageData createBotMessageData() {
        return createBotMessageData("STREAMING");
    }

    private BotMessageData createBotMessageData(String responseMode) {
        return BotMessageData.newBuilder()
                .setSenderUserId(SENDER_ID)
                .setBotUserId(BOT_ID)
                .setConversationId(CONVERSATION_ID)
                .setMessageId(MESSAGE_ID)
                .setSessionId(SESSION_ID)
                .setMessageText(MESSAGE_TEXT)
                .setSendTimestamp(Instant.now().toEpochMilli())
                .setWebhookUrl(WEBHOOK_URL)
                .setApiKey(API_KEY)
                .setResponseMode(responseMode)
                .build();
    }

    @Nested
    class ResponseModeTests {

        @Test
        void streamingMode_pushesDeltaAndDone() {
            // Given
            BotMessageData botMessage = createBotMessageData("STREAMING");
            List<SSEEventType> pushedEventTypes = new ArrayList<>();

            doAnswer(
                            invocation -> {
                                pushedEventTypes.add(invocation.getArgument(3));
                                return null;
                            })
                    .when(mockGatewayPusher)
                    .pushSSE(
                            anyLong(),
                            anyLong(),
                            anyString(),
                            any(),
                            anyString(),
                            anyLong(),
                            anyLong());

            when(mockHttpClient.sendWithSSE(anyString(), anyString(), any(), any()))
                    .thenAnswer(
                            invocation -> {
                                Consumer<SSEEvent> eventHandler = invocation.getArgument(3);
                                eventHandler.accept(SSEEvent.delta("Hello "));
                                eventHandler.accept(SSEEvent.delta("World"));
                                return CompletableFuture.completedFuture("Hello World");
                            });

            // When
            service.dispatch(botMessage);

            // Wait for async processing
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }

            // Then - should have 2 deltas + 1 done
            verify(mockGatewayPusher, times(3))
                    .pushSSE(
                            anyLong(),
                            anyLong(),
                            anyString(),
                            any(),
                            anyString(),
                            anyLong(),
                            anyLong());

            assertEquals(
                    2,
                    pushedEventTypes.stream()
                            .filter(t -> t == SSEEventType.SSE_EVENT_TYPE_DELTA)
                            .count());
            assertEquals(
                    1,
                    pushedEventTypes.stream()
                            .filter(t -> t == SSEEventType.SSE_EVENT_TYPE_DONE)
                            .count());
        }

        @Test
        void batchMode_pushesDoneOnly() {
            // Given
            BotMessageData botMessage = createBotMessageData("BATCH");
            List<SSEEventType> pushedEventTypes = new ArrayList<>();

            doAnswer(
                            invocation -> {
                                pushedEventTypes.add(invocation.getArgument(3));
                                return null;
                            })
                    .when(mockGatewayPusher)
                    .pushSSE(
                            anyLong(),
                            anyLong(),
                            anyString(),
                            any(),
                            anyString(),
                            anyLong(),
                            anyLong());

            when(mockHttpClient.sendWithSSE(anyString(), anyString(), any(), any()))
                    .thenAnswer(
                            invocation -> {
                                Consumer<SSEEvent> eventHandler = invocation.getArgument(3);
                                eventHandler.accept(SSEEvent.delta("Hello "));
                                eventHandler.accept(SSEEvent.delta("World"));
                                return CompletableFuture.completedFuture("Hello World");
                            });

            // When
            service.dispatch(botMessage);

            // Wait for async processing
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }

            // Then - should have only 1 done (no deltas)
            verify(mockGatewayPusher, times(1))
                    .pushSSE(
                            anyLong(),
                            anyLong(),
                            anyString(),
                            any(),
                            anyString(),
                            anyLong(),
                            anyLong());

            assertEquals(
                    0,
                    pushedEventTypes.stream()
                            .filter(t -> t == SSEEventType.SSE_EVENT_TYPE_DELTA)
                            .count());
            assertEquals(
                    1,
                    pushedEventTypes.stream()
                            .filter(t -> t == SSEEventType.SSE_EVENT_TYPE_DONE)
                            .count());
        }

        @Test
        void defaultResponseMode_isStreaming() {
            // Given - no responseMode set (empty string)
            BotMessageData botMessage = createBotMessageData("");
            List<SSEEventType> pushedEventTypes = new ArrayList<>();

            doAnswer(
                            invocation -> {
                                pushedEventTypes.add(invocation.getArgument(3));
                                return null;
                            })
                    .when(mockGatewayPusher)
                    .pushSSE(
                            anyLong(),
                            anyLong(),
                            anyString(),
                            any(),
                            anyString(),
                            anyLong(),
                            anyLong());

            when(mockHttpClient.sendWithSSE(anyString(), anyString(), any(), any()))
                    .thenAnswer(
                            invocation -> {
                                Consumer<SSEEvent> eventHandler = invocation.getArgument(3);
                                eventHandler.accept(SSEEvent.delta("Test"));
                                return CompletableFuture.completedFuture("Test");
                            });

            // When
            service.dispatch(botMessage);

            // Wait for async processing
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }

            // Then - should push delta (streaming is default)
            assertTrue(
                    pushedEventTypes.stream()
                            .anyMatch(t -> t == SSEEventType.SSE_EVENT_TYPE_DELTA));
        }

        @Test
        void invalidResponseMode_defaultsToStreaming() {
            // Given - invalid responseMode
            BotMessageData botMessage = createBotMessageData("INVALID_MODE");
            List<SSEEventType> pushedEventTypes = new ArrayList<>();

            doAnswer(
                            invocation -> {
                                pushedEventTypes.add(invocation.getArgument(3));
                                return null;
                            })
                    .when(mockGatewayPusher)
                    .pushSSE(
                            anyLong(),
                            anyLong(),
                            anyString(),
                            any(),
                            anyString(),
                            anyLong(),
                            anyLong());

            when(mockHttpClient.sendWithSSE(anyString(), anyString(), any(), any()))
                    .thenAnswer(
                            invocation -> {
                                Consumer<SSEEvent> eventHandler = invocation.getArgument(3);
                                eventHandler.accept(SSEEvent.delta("Test"));
                                return CompletableFuture.completedFuture("Test");
                            });

            // When
            service.dispatch(botMessage);

            // Wait for async processing
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }

            // Then - should push delta (streaming is default for invalid mode)
            assertTrue(
                    pushedEventTypes.stream()
                            .anyMatch(t -> t == SSEEventType.SSE_EVENT_TYPE_DELTA));
        }
    }

    @Nested
    class DirectGatewayPushTests {

        @Test
        void deltaEvent_isPushedViaDirectGatewayPusher() {
            // Given
            BotMessageData botMessage = createBotMessageData("STREAMING");

            when(mockHttpClient.sendWithSSE(anyString(), anyString(), any(), any()))
                    .thenAnswer(
                            invocation -> {
                                Consumer<SSEEvent> eventHandler = invocation.getArgument(3);
                                eventHandler.accept(SSEEvent.delta("Test delta"));
                                return CompletableFuture.completedFuture("Test delta");
                            });

            // When
            service.dispatch(botMessage);

            // Wait for async processing
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }

            // Then
            verify(mockGatewayPusher)
                    .pushSSE(
                            eq(SENDER_ID),
                            eq(BOT_ID),
                            eq(CONVERSATION_ID),
                            eq(SSEEventType.SSE_EVENT_TYPE_DELTA),
                            eq("Test delta"),
                            eq(0L),
                            anyLong());
        }

        @Test
        void doneEvent_isPushedWithMessageIdAndContent() {
            // Given
            BotMessageData botMessage = createBotMessageData();
            String fullResponse = "Complete response";

            when(mockHttpClient.sendWithSSE(anyString(), anyString(), any(), any()))
                    .thenReturn(CompletableFuture.completedFuture(fullResponse));

            ArgumentCaptor<Long> messageIdCaptor = ArgumentCaptor.forClass(Long.class);
            ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);

            // When
            service.dispatch(botMessage);

            // Wait for async processing
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }

            // Then
            verify(mockGatewayPusher)
                    .pushSSE(
                            eq(SENDER_ID),
                            eq(BOT_ID),
                            eq(CONVERSATION_ID),
                            eq(SSEEventType.SSE_EVENT_TYPE_DONE),
                            contentCaptor.capture(),
                            messageIdCaptor.capture(),
                            anyLong());

            assertEquals(fullResponse, contentCaptor.getValue());
            assertTrue(messageIdCaptor.getValue() > 0);
        }

        @Test
        void pusherFailure_continuesProcessing() {
            // Given
            BotMessageData botMessage = createBotMessageData();

            doThrow(new RuntimeException("Push failed"))
                    .when(mockGatewayPusher)
                    .pushSSE(
                            anyLong(),
                            anyLong(),
                            anyString(),
                            eq(SSEEventType.SSE_EVENT_TYPE_DELTA),
                            anyString(),
                            anyLong(),
                            anyLong());

            when(mockHttpClient.sendWithSSE(anyString(), anyString(), any(), any()))
                    .thenAnswer(
                            invocation -> {
                                Consumer<SSEEvent> eventHandler = invocation.getArgument(3);
                                eventHandler.accept(SSEEvent.delta("Test"));
                                return CompletableFuture.completedFuture("Test");
                            });

            // When - should not throw
            assertDoesNotThrow(
                    () -> {
                        service.dispatch(botMessage);
                        Thread.sleep(100);
                    });

            // Then - storage should still be called
            verify(mockStorageFacade)
                    .saveTextMessage(
                            anyString(),
                            anyLong(),
                            anyLong(),
                            anyLong(),
                            anyLong(),
                            anyString(),
                            any(),
                            any());
        }
    }

    @Nested
    class DoneEventTests {

        @Test
        void dispatch_WithDoneEvent_SavesResponseAndPushes() {
            // Given
            BotMessageData botMessage = createBotMessageData();
            String fullResponse = "Hello World";

            when(mockHttpClient.sendWithSSE(anyString(), anyString(), any(), any()))
                    .thenAnswer(
                            invocation -> {
                                Consumer<SSEEvent> eventHandler = invocation.getArgument(3);
                                eventHandler.accept(SSEEvent.done(fullResponse));
                                return CompletableFuture.completedFuture(fullResponse);
                            });

            // When
            service.dispatch(botMessage);

            // Wait for async processing
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }

            // Then - Verify response was saved
            verify(mockStorageFacade)
                    .saveTextMessage(
                            eq(CONVERSATION_ID),
                            anyLong(), // messageId
                            eq(BOT_ID), // sender is bot
                            eq(SENDER_ID), // receiver is user
                            anyLong(), // timestamp
                            eq(fullResponse),
                            isNull(), // no file URL
                            isNull() // no file name
                            );

            // Verify DONE event was pushed via direct gateway
            verify(mockGatewayPusher)
                    .pushSSE(
                            eq(SENDER_ID),
                            eq(BOT_ID),
                            eq(CONVERSATION_ID),
                            eq(SSEEventType.SSE_EVENT_TYPE_DONE),
                            eq(fullResponse),
                            anyLong(),
                            anyLong());
        }

        @Test
        void dispatch_DoneEvent_GeneratesUniqueMessageId() {
            // Given
            BotMessageData botMessage = createBotMessageData();

            when(mockHttpClient.sendWithSSE(anyString(), anyString(), any(), any()))
                    .thenReturn(CompletableFuture.completedFuture("Response 1"));

            ArgumentCaptor<Long> messageIdCaptor = ArgumentCaptor.forClass(Long.class);

            // When - dispatch twice
            service.dispatch(botMessage);
            service.dispatch(botMessage);

            // Wait for async processing
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {
            }

            // Then - verify message IDs are different
            verify(mockStorageFacade, times(2))
                    .saveTextMessage(
                            anyString(),
                            messageIdCaptor.capture(),
                            anyLong(),
                            anyLong(),
                            anyLong(),
                            anyString(),
                            any(),
                            any());

            List<Long> messageIds = messageIdCaptor.getAllValues();
            assertEquals(2, messageIds.size());
            assertNotEquals(messageIds.get(0), messageIds.get(1));
        }
    }

    @Nested
    class ErrorHandlingTests {

        @Test
        void dispatch_WebhookError_PushesErrorEventToUser() {
            // Given
            BotMessageData botMessage = createBotMessageData();

            when(mockHttpClient.sendWithSSE(anyString(), anyString(), any(), any()))
                    .thenReturn(
                            CompletableFuture.failedFuture(
                                    new RuntimeException("Connection failed")));

            // When
            service.dispatch(botMessage);

            // Wait for async processing
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }

            // Then
            verify(mockGatewayPusher)
                    .pushSSE(
                            eq(SENDER_ID),
                            eq(BOT_ID),
                            eq(CONVERSATION_ID),
                            eq(SSEEventType.SSE_EVENT_TYPE_ERROR),
                            eq("Bot is currently unavailable"),
                            eq(0L),
                            anyLong());
        }

        @Test
        void dispatch_WebhookError_DoesNotSaveResponse() {
            // Given
            BotMessageData botMessage = createBotMessageData();

            when(mockHttpClient.sendWithSSE(anyString(), anyString(), any(), any()))
                    .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Error")));

            // When
            service.dispatch(botMessage);

            // Wait for async processing
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }

            // Then - verify no message was saved
            verify(mockStorageFacade, never())
                    .saveTextMessage(
                            anyString(),
                            anyLong(),
                            anyLong(),
                            anyLong(),
                            anyLong(),
                            anyString(),
                            any(),
                            any());
        }
    }

    @Nested
    class ResponseStorageTests {

        @Test
        void dispatch_SavesBotResponseWithCorrectMetadata() {
            // Given
            BotMessageData botMessage = createBotMessageData();
            String responseText = "I am a helpful assistant";

            when(mockHttpClient.sendWithSSE(anyString(), anyString(), any(), any()))
                    .thenReturn(CompletableFuture.completedFuture(responseText));

            // When
            service.dispatch(botMessage);

            // Wait for async processing
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }

            // Then
            verify(mockStorageFacade)
                    .saveTextMessage(
                            eq(CONVERSATION_ID),
                            anyLong(),
                            eq(BOT_ID), // sender is bot
                            eq(SENDER_ID), // receiver is user
                            anyLong(),
                            eq(responseText),
                            isNull(),
                            isNull());
        }

        @Test
        void dispatch_UsesAccumulatedResponseWhenFullTextNull() {
            // Given
            BotMessageData botMessage = createBotMessageData();

            when(mockHttpClient.sendWithSSE(anyString(), anyString(), any(), any()))
                    .thenAnswer(
                            invocation -> {
                                Consumer<SSEEvent> eventHandler = invocation.getArgument(3);
                                eventHandler.accept(SSEEvent.delta("Hello "));
                                eventHandler.accept(SSEEvent.delta("World"));
                                return CompletableFuture.completedFuture(null); // null fullText
                            });

            ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);

            // When
            service.dispatch(botMessage);

            // Wait for async processing
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }

            // Then - should use accumulated text
            verify(mockStorageFacade)
                    .saveTextMessage(
                            anyString(),
                            anyLong(),
                            anyLong(),
                            anyLong(),
                            anyLong(),
                            textCaptor.capture(),
                            any(),
                            any());

            assertEquals("Hello World", textCaptor.getValue());
        }
    }
}
