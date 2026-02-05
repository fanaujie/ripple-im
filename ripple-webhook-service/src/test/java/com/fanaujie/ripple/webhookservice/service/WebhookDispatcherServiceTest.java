package com.fanaujie.ripple.webhookservice.service;

import com.fanaujie.ripple.cache.service.BotConfigStorage;
import com.fanaujie.ripple.communication.gateway.GatewayPusher;
import com.fanaujie.ripple.protobuf.msgdispatcher.BotWebhookEvent;
import com.fanaujie.ripple.protobuf.push.SSEEventType;
import com.fanaujie.ripple.protobuf.snowflakeid.GenerateIdResponse;
import com.fanaujie.ripple.snowflakeid.client.SnowflakeIdClient;
import com.fanaujie.ripple.storage.model.BotConfig;
import com.fanaujie.ripple.storage.model.BotResponseMode;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebhookDispatcherServiceTest {

    private WebhookHttpClient mockHttpClient;
    private RippleStorageFacade mockStorageFacade;
    private GatewayPusher mockGatewayPusher;
    private BotConfigStorage mockBotConfigStorage;
    private SnowflakeIdClient mockSnowflakeIdClient;
    private WebhookDispatcherService service;

    private static final long SENDER_ID = 1001L;
    private static final long BOT_ID = 5001L;
    private static final String CONVERSATION_ID = "conv-123";
    private static final long MESSAGE_ID = 2001L;
    private static final String WEBHOOK_URL = "https://bot.example.com/webhook";
    private static final String API_KEY = "test-api-key";
    private static final String SESSION_ID = "session-456";
    private static final String MESSAGE_TEXT = "Hello bot";

    private final AtomicLong snowflakeIdSequence = new AtomicLong(9000L);

    @BeforeEach
    void setUp() throws Exception {
        mockHttpClient = mock(WebhookHttpClient.class);
        mockStorageFacade = mock(RippleStorageFacade.class);
        mockGatewayPusher = mock(GatewayPusher.class);
        mockBotConfigStorage = mock(BotConfigStorage.class);
        mockSnowflakeIdClient = mock(SnowflakeIdClient.class);

        // Default: each call to requestSnowflakeId returns a unique incrementing ID
        when(mockSnowflakeIdClient.requestSnowflakeId()).thenAnswer(invocation -> {
            long id = snowflakeIdSequence.incrementAndGet();
            GenerateIdResponse response = GenerateIdResponse.newBuilder().setId(id).build();
            return CompletableFuture.completedFuture(response);
        });

        service =
                new WebhookDispatcherService(
                        mockHttpClient, mockStorageFacade, mockGatewayPusher,
                        mockBotConfigStorage, mockSnowflakeIdClient);

        // Default: return a STREAMING bot config
        when(mockBotConfigStorage.get(BOT_ID)).thenReturn(createBotConfig(BotResponseMode.STREAMING));
    }

    private BotWebhookEvent createBotWebhookEvent() {
        return BotWebhookEvent.newBuilder()
                .setSenderUserId(SENDER_ID)
                .setBotUserId(BOT_ID)
                .setConversationId(CONVERSATION_ID)
                .setMessageId(MESSAGE_ID)
                .setSessionId(SESSION_ID)
                .setMessageText(MESSAGE_TEXT)
                .setSendTimestamp(Instant.now().toEpochMilli())
                .build();
    }

    private BotConfig createBotConfig(BotResponseMode responseMode) {
        BotConfig config = new BotConfig();
        config.setUserId(BOT_ID);
        config.setWebhookUrl(WEBHOOK_URL);
        config.setApiKey(API_KEY);
        config.setResponseMode(responseMode);
        return config;
    }

    @Nested
    class ResponseModeTests {

        @Test
        void streamingMode_pushesDeltaAndDone() throws Exception {
            // Given
            when(mockBotConfigStorage.get(BOT_ID))
                    .thenReturn(createBotConfig(BotResponseMode.STREAMING));
            BotWebhookEvent botMessage = createBotWebhookEvent();
            List<SSEEventType> pushedEventTypes = new ArrayList<>();
            List<Long> pushedMessageIds = new ArrayList<>();

            doAnswer(
                            invocation -> {
                                pushedEventTypes.add(invocation.getArgument(3));
                                pushedMessageIds.add(invocation.getArgument(5));
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

            // All SSE events (delta + done) should carry the same Snowflake message ID
            long expectedId = pushedMessageIds.get(0);
            assertTrue(expectedId > 0, "Message ID should be a positive Snowflake ID");
            assertTrue(
                    pushedMessageIds.stream().allMatch(id -> id == expectedId),
                    "All SSE events should share the same message ID");
        }

        @Test
        void batchMode_usesSendPlain() throws Exception {
            // Given
            when(mockBotConfigStorage.get(BOT_ID))
                    .thenReturn(createBotConfig(BotResponseMode.BATCH));
            BotWebhookEvent botMessage = createBotWebhookEvent();

            when(mockHttpClient.sendPlain(anyString(), anyString(), any()))
                    .thenReturn(CompletableFuture.completedFuture("Hello World"));

            // When
            service.dispatch(botMessage);

            // Wait for async processing
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }

            // Then - sendPlain was called, sendWithSSE was not
            verify(mockHttpClient).sendPlain(anyString(), anyString(), any());
            verify(mockHttpClient, never()).sendWithSSE(anyString(), anyString(), any(), any());
        }

        @Test
        void batchMode_pushesDoneOnly() throws Exception {
            // Given
            when(mockBotConfigStorage.get(BOT_ID))
                    .thenReturn(createBotConfig(BotResponseMode.BATCH));
            BotWebhookEvent botMessage = createBotWebhookEvent();
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

            when(mockHttpClient.sendPlain(anyString(), anyString(), any()))
                    .thenReturn(CompletableFuture.completedFuture("Hello World"));

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
        void batchMode_savesResponseAndPushesDone() throws Exception {
            // Given
            when(mockBotConfigStorage.get(BOT_ID))
                    .thenReturn(createBotConfig(BotResponseMode.BATCH));
            BotWebhookEvent botMessage = createBotWebhookEvent();
            String responseText = "Batch response";

            when(mockHttpClient.sendPlain(anyString(), anyString(), any()))
                    .thenReturn(CompletableFuture.completedFuture(responseText));

            // When
            service.dispatch(botMessage);

            // Wait for async processing
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }

            // Then - response saved to storage
            verify(mockStorageFacade)
                    .saveTextMessage(
                            eq(CONVERSATION_ID),
                            anyLong(),
                            eq(BOT_ID),
                            eq(SENDER_ID),
                            anyLong(),
                            eq(responseText),
                            isNull(),
                            isNull());

            // DONE event pushed to user
            verify(mockGatewayPusher)
                    .pushSSE(
                            eq(SENDER_ID),
                            eq(BOT_ID),
                            eq(CONVERSATION_ID),
                            eq(SSEEventType.SSE_EVENT_TYPE_DONE),
                            eq(responseText),
                            anyLong(),
                            anyLong());
        }

        @Test
        void batchMode_webhookError_pushesErrorEvent() throws Exception {
            // Given
            when(mockBotConfigStorage.get(BOT_ID))
                    .thenReturn(createBotConfig(BotResponseMode.BATCH));
            BotWebhookEvent botMessage = createBotWebhookEvent();

            when(mockHttpClient.sendPlain(anyString(), anyString(), any()))
                    .thenReturn(CompletableFuture.failedFuture(
                            new RuntimeException("Connection refused")));

            // When
            service.dispatch(botMessage);

            // Wait for async processing
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }

            // Then - error event pushed with the pre-generated Snowflake message ID
            verify(mockGatewayPusher)
                    .pushSSE(
                            eq(SENDER_ID),
                            eq(BOT_ID),
                            eq(CONVERSATION_ID),
                            eq(SSEEventType.SSE_EVENT_TYPE_ERROR),
                            eq("Bot is currently unavailable"),
                            anyLong(),
                            anyLong());
            verify(mockStorageFacade, never())
                    .saveTextMessage(
                            anyString(), anyLong(), anyLong(), anyLong(),
                            anyLong(), anyString(), any(), any());
        }

        @Test
        void defaultResponseMode_isStreaming() throws Exception {
            // Given - null responseMode defaults to STREAMING
            BotConfig config = createBotConfig(null);
            when(mockBotConfigStorage.get(BOT_ID)).thenReturn(config);
            BotWebhookEvent botMessage = createBotWebhookEvent();
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
    }

    @Nested
    class BotConfigLookupTests {

        @Test
        void botConfigNotFound_skipsDispatch() throws Exception {
            // Given
            when(mockBotConfigStorage.get(BOT_ID)).thenReturn(null);
            BotWebhookEvent botMessage = createBotWebhookEvent();

            // When
            service.dispatch(botMessage);

            // Then - no HTTP call or push
            verifyNoInteractions(mockHttpClient);
            verifyNoInteractions(mockGatewayPusher);
        }

        @Test
        void botConfigLookupFails_pushesError() throws Exception {
            // Given
            when(mockBotConfigStorage.get(BOT_ID))
                    .thenThrow(new RuntimeException("Redis down"));
            BotWebhookEvent botMessage = createBotWebhookEvent();

            // When
            service.dispatch(botMessage);

            // Then - error pushed to user
            verify(mockGatewayPusher)
                    .pushSSE(
                            eq(SENDER_ID),
                            eq(BOT_ID),
                            eq(CONVERSATION_ID),
                            eq(SSEEventType.SSE_EVENT_TYPE_ERROR),
                            eq("Bot is currently unavailable"),
                            eq(0L),
                            anyLong());
            verifyNoInteractions(mockHttpClient);
        }
    }

    @Nested
    class DirectGatewayPushTests {

        @Test
        void deltaEvent_isPushedViaDirectGatewayPusher() {
            // Given
            BotWebhookEvent botMessage = createBotWebhookEvent();

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

            // Then - delta events now carry the Snowflake message ID
            verify(mockGatewayPusher)
                    .pushSSE(
                            eq(SENDER_ID),
                            eq(BOT_ID),
                            eq(CONVERSATION_ID),
                            eq(SSEEventType.SSE_EVENT_TYPE_DELTA),
                            eq("Test delta"),
                            anyLong(),
                            anyLong());
        }

        @Test
        void doneEvent_isPushedWithMessageIdAndContent() {
            // Given
            BotWebhookEvent botMessage = createBotWebhookEvent();
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
            BotWebhookEvent botMessage = createBotWebhookEvent();

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
            BotWebhookEvent botMessage = createBotWebhookEvent();
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
            BotWebhookEvent botMessage = createBotWebhookEvent();

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
            BotWebhookEvent botMessage = createBotWebhookEvent();

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

            // Then - error event carries the pre-generated Snowflake message ID
            verify(mockGatewayPusher)
                    .pushSSE(
                            eq(SENDER_ID),
                            eq(BOT_ID),
                            eq(CONVERSATION_ID),
                            eq(SSEEventType.SSE_EVENT_TYPE_ERROR),
                            eq("Bot is currently unavailable"),
                            anyLong(),
                            anyLong());
        }

        @Test
        void dispatch_WebhookError_DoesNotSaveResponse() {
            // Given
            BotWebhookEvent botMessage = createBotWebhookEvent();

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
            BotWebhookEvent botMessage = createBotWebhookEvent();
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
            BotWebhookEvent botMessage = createBotWebhookEvent();

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
