package com.fanaujie.ripple.msgapiserver.processor;

import com.fanaujie.ripple.communication.msgqueue.GenericProducer;
import com.fanaujie.ripple.protobuf.msgapiserver.SendMessageReq;
import com.fanaujie.ripple.protobuf.msgapiserver.SendMessageResp;
import com.fanaujie.ripple.protobuf.msgapiserver.SingleMessageContent;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SingleMessageContentProcessorTest {

    private static final String TOPIC_NAME = "test-topic";
    private static final long SENDER_ID = 1L;
    private static final long RECEIVER_ID = 2L;
    private static final long BOT_ID = 100L;

    @Mock
    private RippleStorageFacade storageFacade;

    @Mock
    private GenericProducer<String, MessagePayload> producer;

    @Mock
    private ExecutorService executorService;

    private SingleMessageContentProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new SingleMessageContentProcessor(
                TOPIC_NAME, storageFacade, producer, executorService);
    }

    @Test
    void handleSingleMessage_ToBot_SkipsBlockCheck() throws Exception {
        // Given: receiver is a bot
        when(storageFacade.isBot(BOT_ID)).thenReturn(true);

        SendMessageReq request = SendMessageReq.newBuilder()
                .setSenderId(SENDER_ID)
                .setReceiverId(BOT_ID)
                .setSingleMessageContent(
                        SingleMessageContent.newBuilder()
                                .setText("Hello Bot")
                                .build())
                .build();

        // When
        SendMessageResp response = processor.handle(request);

        // Then
        assertNotNull(response);
        // Verify isBot was called
        verify(storageFacade).isBot(BOT_ID);
        // Verify isBlocked was NOT called (skipped for bots)
        verify(storageFacade, never()).isBlocked(anyLong(), anyLong());
        // Verify message was sent
        verify(producer).send(eq(TOPIC_NAME), eq(String.valueOf(SENDER_ID)), any(MessagePayload.class));
    }

    @Test
    void handleSingleMessage_ToRegularUser_ChecksBlock() throws Exception {
        // Given: receiver is not a bot and has not blocked sender
        when(storageFacade.isBot(RECEIVER_ID)).thenReturn(false);
        when(storageFacade.isBlocked(RECEIVER_ID, SENDER_ID)).thenReturn(false);

        SendMessageReq request = SendMessageReq.newBuilder()
                .setSenderId(SENDER_ID)
                .setReceiverId(RECEIVER_ID)
                .setSingleMessageContent(
                        SingleMessageContent.newBuilder()
                                .setText("Hello User")
                                .build())
                .build();

        // When
        SendMessageResp response = processor.handle(request);

        // Then
        assertNotNull(response);
        verify(storageFacade).isBot(RECEIVER_ID);
        verify(storageFacade).isBlocked(RECEIVER_ID, SENDER_ID);
        verify(producer).send(eq(TOPIC_NAME), eq(String.valueOf(SENDER_ID)), any(MessagePayload.class));
    }

    @Test
    void handleSingleMessage_ToRegularUser_Blocked_MessageNotSent() throws Exception {
        // Given: receiver is not a bot and has blocked sender
        when(storageFacade.isBot(RECEIVER_ID)).thenReturn(false);
        when(storageFacade.isBlocked(RECEIVER_ID, SENDER_ID)).thenReturn(true);

        SendMessageReq request = SendMessageReq.newBuilder()
                .setSenderId(SENDER_ID)
                .setReceiverId(RECEIVER_ID)
                .setSingleMessageContent(
                        SingleMessageContent.newBuilder()
                                .setText("Hello User")
                                .build())
                .build();

        // When
        SendMessageResp response = processor.handle(request);

        // Then
        assertNotNull(response);
        verify(storageFacade).isBot(RECEIVER_ID);
        verify(storageFacade).isBlocked(RECEIVER_ID, SENDER_ID);
        // Message should NOT be sent when blocked
        verify(producer, never()).send(anyString(), anyString(), any(MessagePayload.class));
    }
}
