package com.fanaujie.ripple.apigateway.service;

import com.fanaujie.ripple.apigateway.dto.*;
import com.fanaujie.ripple.cache.service.ConversationSummaryStorage;
import com.fanaujie.ripple.communication.msgapi.MessageAPISender;
import com.fanaujie.ripple.protobuf.msgapiserver.SendMessageReq;
import com.fanaujie.ripple.protobuf.snowflakeid.GenerateIdResponse;
import com.fanaujie.ripple.snowflakeid.client.SnowflakeIdClient;
import com.fanaujie.ripple.storage.model.Messages;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = {MessageService.class})
class MessageServiceTest {

    @MockitoBean
    private MessageAPISender messageAPISender;

    @MockitoBean
    private SnowflakeIdClient snowflakeIdClient;

    @MockitoBean
    private RippleStorageFacade storageFacade;

    @MockitoBean
    private ConversationSummaryStorage conversationStorage;

    @Autowired
    private MessageService messageService;

    private static final long SENDER_ID = 1L;
    private static final long RECEIVER_ID = 2L;
    private static final long GROUP_ID = 100L;
    private static final long MESSAGE_ID = 12345L;
    private static final String CONVERSATION_ID = "conv1";

    @BeforeEach
    void setUp() {
        reset(messageAPISender, snowflakeIdClient, storageFacade, conversationStorage);
    }

    // ==================== sendMessage Tests ====================

    @Test
    void sendMessage_SingleChat_Success() throws Exception {
        // Given
        GenerateIdResponse idResponse = GenerateIdResponse.newBuilder().setId(MESSAGE_ID).build();
        when(snowflakeIdClient.requestSnowflakeId())
                .thenReturn(CompletableFuture.completedFuture(idResponse));
        doNothing().when(messageAPISender).seenMessage(any(SendMessageReq.class));

        SendMessageRequest request = new SendMessageRequest();
        request.setSenderId(String.valueOf(SENDER_ID));
        request.setReceiverId(String.valueOf(RECEIVER_ID));
        request.setTextContent("Hello");

        // When
        ResponseEntity<MessageResponse> response = messageService.sendMessage(request);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertEquals("success", response.getBody().getMessage());
        assertEquals(String.valueOf(MESSAGE_ID), response.getBody().getData().getMessageId());

        verify(snowflakeIdClient).requestSnowflakeId();
        verify(messageAPISender).seenMessage(any(SendMessageReq.class));
    }

    @Test
    void sendMessage_GroupChat_Success() throws Exception {
        // Given
        GenerateIdResponse idResponse = GenerateIdResponse.newBuilder().setId(MESSAGE_ID).build();
        when(snowflakeIdClient.requestSnowflakeId())
                .thenReturn(CompletableFuture.completedFuture(idResponse));
        doNothing().when(messageAPISender).seenMessage(any(SendMessageReq.class));

        SendMessageRequest request = new SendMessageRequest();
        request.setSenderId(String.valueOf(SENDER_ID));
        request.setGroupId(String.valueOf(GROUP_ID));
        request.setTextContent("Hello Group");

        // When
        ResponseEntity<MessageResponse> response = messageService.sendMessage(request);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());

        verify(snowflakeIdClient).requestSnowflakeId();
        verify(messageAPISender).seenMessage(any(SendMessageReq.class));
    }

    @Test
    void sendMessage_NullSenderId_ReturnsBadRequest() {
        // Given
        SendMessageRequest request = new SendMessageRequest();
        request.setSenderId(null);
        request.setReceiverId(String.valueOf(RECEIVER_ID));

        // When
        ResponseEntity<MessageResponse> response = messageService.sendMessage(request);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertTrue(response.getBody().getMessage().contains("SenderId cannot be null"));

        verifyNoInteractions(messageAPISender);
    }

    @Test
    void sendMessage_BothReceiverAndGroup_ReturnsBadRequest() throws Exception {
        // Given
        GenerateIdResponse idResponse = GenerateIdResponse.newBuilder().setId(MESSAGE_ID).build();
        when(snowflakeIdClient.requestSnowflakeId())
                .thenReturn(CompletableFuture.completedFuture(idResponse));

        SendMessageRequest request = new SendMessageRequest();
        request.setSenderId(String.valueOf(SENDER_ID));
        request.setReceiverId(String.valueOf(RECEIVER_ID));
        request.setGroupId(String.valueOf(GROUP_ID));

        // When
        ResponseEntity<MessageResponse> response = messageService.sendMessage(request);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertTrue(response.getBody().getMessage().contains("Only one of receiverId or groupId"));

        verify(messageAPISender, never()).seenMessage(any());
    }

    @Test
    void sendMessage_NeitherReceiverNorGroup_ReturnsBadRequest() throws Exception {
        // Given
        GenerateIdResponse idResponse = GenerateIdResponse.newBuilder().setId(MESSAGE_ID).build();
        when(snowflakeIdClient.requestSnowflakeId())
                .thenReturn(CompletableFuture.completedFuture(idResponse));

        SendMessageRequest request = new SendMessageRequest();
        request.setSenderId(String.valueOf(SENDER_ID));
        // Neither receiverId nor groupId

        // When
        ResponseEntity<MessageResponse> response = messageService.sendMessage(request);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertTrue(response.getBody().getMessage().contains("Either receiverId or groupId is required"));
    }

    @Test
    void sendMessage_GrpcError_ReturnsInternalError() throws Exception {
        // Given
        GenerateIdResponse idResponse = GenerateIdResponse.newBuilder().setId(MESSAGE_ID).build();
        when(snowflakeIdClient.requestSnowflakeId())
                .thenReturn(CompletableFuture.completedFuture(idResponse));
        doThrow(new RuntimeException("gRPC error"))
                .when(messageAPISender).seenMessage(any(SendMessageReq.class));

        SendMessageRequest request = new SendMessageRequest();
        request.setSenderId(String.valueOf(SENDER_ID));
        request.setReceiverId(String.valueOf(RECEIVER_ID));
        request.setTextContent("Hello");

        // When
        ResponseEntity<MessageResponse> response = messageService.sendMessage(request);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().getCode());
    }

    // ==================== readMessages Tests ====================

    @Test
    void readMessages_Success() {
        // Given
        Messages messages = new Messages();
        messages.setMessages(new ArrayList<>());
        when(storageFacade.getMessages(CONVERSATION_ID, MESSAGE_ID, 50)).thenReturn(messages);

        // When
        ResponseEntity<ReadMessagesResponse> response =
                messageService.readMessages(CONVERSATION_ID, MESSAGE_ID, 50, SENDER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertNotNull(response.getBody().getData());

        verify(storageFacade).getMessages(CONVERSATION_ID, MESSAGE_ID, 50);
    }

    @Test
    void readMessages_InvalidReadSize_Zero_ReturnsBadRequest() {
        // When
        ResponseEntity<ReadMessagesResponse> response =
                messageService.readMessages(CONVERSATION_ID, MESSAGE_ID, 0, SENDER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertTrue(response.getBody().getMessage().contains("Invalid read size"));

        verifyNoInteractions(storageFacade);
    }

    @Test
    void readMessages_InvalidReadSize_TooLarge_ReturnsBadRequest() {
        // When
        ResponseEntity<ReadMessagesResponse> response =
                messageService.readMessages(CONVERSATION_ID, MESSAGE_ID, 500, SENDER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertTrue(response.getBody().getMessage().contains("Invalid read size"));

        verifyNoInteractions(storageFacade);
    }

    @Test
    void readMessages_StorageError_ReturnsInternalError() {
        // Given
        when(storageFacade.getMessages(CONVERSATION_ID, MESSAGE_ID, 50))
                .thenThrow(new RuntimeException("Storage error"));

        // When
        ResponseEntity<ReadMessagesResponse> response =
                messageService.readMessages(CONVERSATION_ID, MESSAGE_ID, 50, SENDER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().getCode());
    }

    // ==================== markLastReadMessageId Tests ====================

    @Test
    void markLastReadMessageId_Success() {
        // Given
        doNothing().when(storageFacade).markLastReadMessageId(CONVERSATION_ID, SENDER_ID, MESSAGE_ID);
        doNothing().when(conversationStorage).resetUnreadCount(SENDER_ID, CONVERSATION_ID);

        // When
        ResponseEntity<CommonResponse> response =
                messageService.markLastReadMessageId(CONVERSATION_ID, MESSAGE_ID, SENDER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertEquals("success", response.getBody().getMessage());

        verify(storageFacade).markLastReadMessageId(CONVERSATION_ID, SENDER_ID, MESSAGE_ID);
        verify(conversationStorage).resetUnreadCount(SENDER_ID, CONVERSATION_ID);
    }

    @Test
    void markLastReadMessageId_Error_ReturnsInternalError() {
        // Given
        doThrow(new RuntimeException("Storage error"))
                .when(storageFacade).markLastReadMessageId(CONVERSATION_ID, SENDER_ID, MESSAGE_ID);

        // When
        ResponseEntity<CommonResponse> response =
                messageService.markLastReadMessageId(CONVERSATION_ID, MESSAGE_ID, SENDER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().getCode());

        verify(storageFacade).markLastReadMessageId(CONVERSATION_ID, SENDER_ID, MESSAGE_ID);
        verify(conversationStorage, never()).resetUnreadCount(anyLong(), anyString());
    }
}
