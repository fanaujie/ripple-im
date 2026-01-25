package com.fanaujie.ripple.webhookservice.service;

import com.fanaujie.ripple.communication.msgqueue.GenericProducer;
import com.fanaujie.ripple.protobuf.msgdispatcher.BotMessageData;
import com.fanaujie.ripple.protobuf.push.PushMessage;
import com.fanaujie.ripple.protobuf.push.PushSSEData;
import com.fanaujie.ripple.protobuf.push.SSEEventType;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import com.fanaujie.ripple.webhookservice.http.WebhookHttpClient;
import com.fanaujie.ripple.webhookservice.model.SSEEvent;
import com.fanaujie.ripple.webhookservice.model.WebhookRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
    private GenericProducer<String, PushMessage> mockPushProducer;
    private WebhookDispatcherService service;

    private static final String PUSH_TOPIC = "ripple-push-notifications";
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
        mockPushProducer = mock(GenericProducer.class);

        service = new WebhookDispatcherService(
                mockHttpClient,
                mockStorageFacade,
                mockPushProducer,
                PUSH_TOPIC);
    }

    private BotMessageData createBotMessageData() {
        return BotMessageData.newBuilder()
                .setSenderUserId(SENDER_ID)
                .setBotUserId(BOT_ID)
                .setConversationId(CONVERSATION_ID)
                .setMessageId(MESSAGE_ID)
                .setSessionId(SESSION_ID)
                .setMessageText(MESSAGE_TEXT)
                .setSendTimestamp(System.currentTimeMillis())
                .setWebhookUrl(WEBHOOK_URL)
                .setApiKey(API_KEY)
                .build();
    }

    @Nested
    class DeltaStreamingTests {

        @Test
        void dispatch_WithDeltaEvents_PushesToUser() {
            // Given
            BotMessageData botMessage = createBotMessageData();
            List<PushMessage> capturedMessages = new ArrayList<>();

            // Mock HTTP client to simulate SSE streaming
            when(mockHttpClient.sendWithSSE(eq(WEBHOOK_URL), eq(API_KEY), any(WebhookRequest.class), any()))
                    .thenAnswer(invocation -> {
                        Consumer<SSEEvent> eventHandler = invocation.getArgument(3);
                        // Simulate streaming delta events
                        eventHandler.accept(SSEEvent.delta("Hel"));
                        eventHandler.accept(SSEEvent.delta("lo "));
                        eventHandler.accept(SSEEvent.delta("World"));
                        return CompletableFuture.completedFuture("Hello World");
                    });

            doAnswer(invocation -> {
                capturedMessages.add(invocation.getArgument(2));
                return null;
            }).when(mockPushProducer).send(eq(PUSH_TOPIC), anyString(), any(PushMessage.class));

            // When
            service.dispatch(botMessage);

            // Then - Verify 3 delta messages + 1 done message were sent
            // Wait briefly for async processing
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}

            // At minimum, verify delta pushes were called
            verify(mockPushProducer, atLeast(3)).send(eq(PUSH_TOPIC), anyString(), any(PushMessage.class));

            // Verify delta content
            List<PushMessage> deltaMessages = capturedMessages.stream()
                    .filter(m -> m.getSseData().getEventType() == SSEEventType.SSE_EVENT_TYPE_DELTA)
                    .toList();
            assertEquals(3, deltaMessages.size());
            assertEquals("Hel", deltaMessages.get(0).getSseData().getContent());
            assertEquals("lo ", deltaMessages.get(1).getSseData().getContent());
            assertEquals("World", deltaMessages.get(2).getSseData().getContent());
        }

        @Test
        void dispatch_DeltaEvents_ContainCorrectMetadata() {
            // Given
            BotMessageData botMessage = createBotMessageData();

            when(mockHttpClient.sendWithSSE(anyString(), anyString(), any(), any()))
                    .thenAnswer(invocation -> {
                        Consumer<SSEEvent> eventHandler = invocation.getArgument(3);
                        eventHandler.accept(SSEEvent.delta("Test"));
                        return CompletableFuture.completedFuture("Test");
                    });

            ArgumentCaptor<PushMessage> messageCaptor = ArgumentCaptor.forClass(PushMessage.class);

            // When
            service.dispatch(botMessage);

            // Wait for async processing
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}

            // Then
            verify(mockPushProducer, atLeast(1)).send(eq(PUSH_TOPIC), eq(String.valueOf(SENDER_ID)), messageCaptor.capture());

            PushMessage captured = messageCaptor.getAllValues().stream()
                    .filter(m -> m.getSseData().getEventType() == SSEEventType.SSE_EVENT_TYPE_DELTA)
                    .findFirst()
                    .orElse(null);

            assertNotNull(captured);
            PushSSEData sseData = captured.getSseData();
            assertEquals(SSEEventType.SSE_EVENT_TYPE_DELTA, sseData.getEventType());
            assertEquals(BOT_ID, sseData.getSendUserId());
            assertEquals(SENDER_ID, sseData.getReceiveUserIds(0));
            assertEquals(CONVERSATION_ID, sseData.getConversationId());
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
                    .thenAnswer(invocation -> {
                        Consumer<SSEEvent> eventHandler = invocation.getArgument(3);
                        eventHandler.accept(SSEEvent.done(fullResponse));
                        return CompletableFuture.completedFuture(fullResponse);
                    });

            ArgumentCaptor<PushMessage> messageCaptor = ArgumentCaptor.forClass(PushMessage.class);

            // When
            service.dispatch(botMessage);

            // Wait for async processing
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}

            // Then - Verify response was saved
            verify(mockStorageFacade).saveTextMessage(
                    eq(CONVERSATION_ID),
                    anyLong(),      // messageId
                    eq(BOT_ID),     // sender is bot
                    eq(SENDER_ID),  // receiver is user
                    anyLong(),      // timestamp
                    eq(fullResponse),
                    isNull(),       // no file URL
                    isNull()        // no file name
            );

            // Verify DONE event was pushed
            verify(mockPushProducer, atLeast(1)).send(eq(PUSH_TOPIC), anyString(), messageCaptor.capture());

            PushMessage doneMessage = messageCaptor.getAllValues().stream()
                    .filter(m -> m.getSseData().getEventType() == SSEEventType.SSE_EVENT_TYPE_DONE)
                    .findFirst()
                    .orElse(null);

            assertNotNull(doneMessage);
            assertEquals(fullResponse, doneMessage.getSseData().getContent());
            assertTrue(doneMessage.getSseData().getMessageId() > 0);
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
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}

            // Then - verify message IDs are different
            verify(mockStorageFacade, times(2)).saveTextMessage(
                    anyString(),
                    messageIdCaptor.capture(),
                    anyLong(),
                    anyLong(),
                    anyLong(),
                    anyString(),
                    any(),
                    any()
            );

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
                    .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Connection failed")));

            ArgumentCaptor<PushMessage> messageCaptor = ArgumentCaptor.forClass(PushMessage.class);

            // When
            service.dispatch(botMessage);

            // Wait for async processing
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}

            // Then
            verify(mockPushProducer, atLeast(1)).send(eq(PUSH_TOPIC), anyString(), messageCaptor.capture());

            PushMessage errorMessage = messageCaptor.getAllValues().stream()
                    .filter(m -> m.getSseData().getEventType() == SSEEventType.SSE_EVENT_TYPE_ERROR)
                    .findFirst()
                    .orElse(null);

            assertNotNull(errorMessage);
            assertEquals("Bot is currently unavailable", errorMessage.getSseData().getContent());
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
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}

            // Then - verify no message was saved
            verify(mockStorageFacade, never()).saveTextMessage(
                    anyString(), anyLong(), anyLong(), anyLong(),
                    anyLong(), anyString(), any(), any());
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
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}

            // Then
            verify(mockStorageFacade).saveTextMessage(
                    eq(CONVERSATION_ID),
                    anyLong(),
                    eq(BOT_ID),          // sender is bot
                    eq(SENDER_ID),       // receiver is user
                    anyLong(),
                    eq(responseText),
                    isNull(),
                    isNull()
            );
        }

        @Test
        void dispatch_UsesAccumulatedResponseWhenFullTextNull() {
            // Given
            BotMessageData botMessage = createBotMessageData();

            when(mockHttpClient.sendWithSSE(anyString(), anyString(), any(), any()))
                    .thenAnswer(invocation -> {
                        Consumer<SSEEvent> eventHandler = invocation.getArgument(3);
                        eventHandler.accept(SSEEvent.delta("Hello "));
                        eventHandler.accept(SSEEvent.delta("World"));
                        return CompletableFuture.completedFuture(null); // null fullText
                    });

            ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);

            // When
            service.dispatch(botMessage);

            // Wait for async processing
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}

            // Then - should use accumulated text
            verify(mockStorageFacade).saveTextMessage(
                    anyString(),
                    anyLong(),
                    anyLong(),
                    anyLong(),
                    anyLong(),
                    textCaptor.capture(),
                    any(),
                    any()
            );

            assertEquals("Hello World", textCaptor.getValue());
        }
    }
}
