package com.fanaujie.ripple.communication.gateway;

import com.fanaujie.ripple.protobuf.msgapiserver.SendMessageReq;
import com.fanaujie.ripple.protobuf.msggateway.*;
import com.fanaujie.ripple.protobuf.push.SSEEventType;
import com.fanaujie.ripple.protobuf.userpresence.UserOnlineInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GatewayRequestBuilderTest {

    @Test
    void buildSSEBatchRequest_shouldBuildCorrectBatchRequest() {
        // Given
        List<UserOnlineInfo> userInfos = List.of(
                UserOnlineInfo.newBuilder()
                        .setUserId("123")
                        .setDeviceId("device1")
                        .setServerLocation("server1:8080")
                        .build(),
                UserOnlineInfo.newBuilder()
                        .setUserId("456")
                        .setDeviceId("device2")
                        .setServerLocation("server1:8080")
                        .build()
        );
        long sendUserId = 999L;
        String conversationId = "conv-123";
        SSEEventType eventType = SSEEventType.SSE_EVENT_TYPE_DELTA;
        String content = "Hello world";
        long messageId = 12345L;
        long sendTimestamp = System.currentTimeMillis();

        // When
        BatchPushMessageRequest result = GatewayRequestBuilder.buildSSEBatchRequest(
                userInfos, sendUserId, conversationId, eventType, content, messageId, sendTimestamp);

        // Then
        assertEquals(2, result.getRequestsCount());

        PushMessageRequest request1 = result.getRequests(0);
        assertEquals("999", request1.getSendUserId());
        assertEquals("123", request1.getReceiveUserId());
        assertEquals("device1", request1.getReceiveDeviceId());
        assertTrue(request1.hasSsePayload());
        assertEquals(eventType, request1.getSsePayload().getEventType());
        assertEquals(conversationId, request1.getSsePayload().getConversationId());
        assertEquals(content, request1.getSsePayload().getContent());
        assertEquals(messageId, request1.getSsePayload().getMessageId());
        assertEquals(sendTimestamp, request1.getSsePayload().getSendTimestamp());

        PushMessageRequest request2 = result.getRequests(1);
        assertEquals("456", request2.getReceiveUserId());
        assertEquals("device2", request2.getReceiveDeviceId());
    }

    @Test
    void buildSSEBatchRequest_shouldHandleNullContent() {
        // Given
        List<UserOnlineInfo> userInfos = List.of(
                UserOnlineInfo.newBuilder()
                        .setUserId("123")
                        .setDeviceId("device1")
                        .setServerLocation("server1:8080")
                        .build()
        );

        // When
        BatchPushMessageRequest result = GatewayRequestBuilder.buildSSEBatchRequest(
                userInfos, 999L, "conv-123", SSEEventType.SSE_EVENT_TYPE_DONE, null, 0, 0);

        // Then
        assertEquals(1, result.getRequestsCount());
        assertEquals("", result.getRequests(0).getSsePayload().getContent());
    }

    @Test
    void buildSSERequest_shouldBuildCorrectSingleRequest() {
        // Given
        UserOnlineInfo userInfo = UserOnlineInfo.newBuilder()
                .setUserId("123")
                .setDeviceId("device1")
                .setServerLocation("server1:8080")
                .build();
        long sendUserId = 999L;
        String conversationId = "conv-123";
        SSEEventType eventType = SSEEventType.SSE_EVENT_TYPE_ERROR;
        String content = "Error occurred";
        long messageId = 0L;
        long sendTimestamp = System.currentTimeMillis();

        // When
        PushMessageRequest result = GatewayRequestBuilder.buildSSERequest(
                userInfo, sendUserId, conversationId, eventType, content, messageId, sendTimestamp);

        // Then
        assertEquals("999", result.getSendUserId());
        assertEquals("123", result.getReceiveUserId());
        assertEquals("device1", result.getReceiveDeviceId());
        assertTrue(result.hasSsePayload());
        assertEquals(eventType, result.getSsePayload().getEventType());
        assertEquals(conversationId, result.getSsePayload().getConversationId());
        assertEquals(content, result.getSsePayload().getContent());
    }

    @Test
    void buildMessageRequest_shouldBuildCorrectMessageRequest() {
        // Given
        UserOnlineInfo userInfo = UserOnlineInfo.newBuilder()
                .setUserId("123")
                .setDeviceId("device1")
                .setServerLocation("server1:8080")
                .build();
        long sendUserId = 999L;
        PushMessageType messageType = PushMessageType.PUSH_MESSAGE_TYPE_SINGLE;
        SendMessageReq messageData = SendMessageReq.newBuilder()
                .setConversationId("conv-123")
                .setSenderId(sendUserId)
                .setReceiverId(123L)
                .build();
        int unreadCount = 5;

        // When
        PushMessageRequest result = GatewayRequestBuilder.buildMessageRequest(
                userInfo, sendUserId, messageType, messageData, unreadCount);

        // Then
        assertEquals("999", result.getSendUserId());
        assertEquals("123", result.getReceiveUserId());
        assertEquals("device1", result.getReceiveDeviceId());
        assertTrue(result.hasMessagePayload());
        assertEquals(messageType, result.getMessagePayload().getMessageType());
        assertEquals("conv-123", result.getMessagePayload().getMessageData().getConversationId());
        assertEquals(sendUserId, result.getMessagePayload().getMessageData().getSenderId());
        assertEquals(unreadCount, result.getMessagePayload().getUnreadCount());
    }

    @Test
    void buildEventRequest_shouldBuildCorrectEventRequest() {
        // Given
        UserOnlineInfo userInfo = UserOnlineInfo.newBuilder()
                .setUserId("123")
                .setDeviceId("device1")
                .setServerLocation("server1:8080")
                .build();
        long sendUserId = 999L;
        List<PushEventType> eventTypes = List.of(
                PushEventType.PUSH_EVENT_TYPE_SELF_INFO_UPDATE,
                PushEventType.PUSH_EVENT_TYPE_RELATION_UPDATE
        );

        // When
        PushMessageRequest result = GatewayRequestBuilder.buildEventRequest(
                userInfo, sendUserId, eventTypes);

        // Then
        assertEquals("999", result.getSendUserId());
        assertEquals("123", result.getReceiveUserId());
        assertEquals("device1", result.getReceiveDeviceId());
        assertTrue(result.hasEventPayload());
        assertEquals(2, result.getEventPayload().getEventTypesCount());
        assertEquals(PushEventType.PUSH_EVENT_TYPE_SELF_INFO_UPDATE,
                result.getEventPayload().getEventTypes(0));
        assertEquals(PushEventType.PUSH_EVENT_TYPE_RELATION_UPDATE,
                result.getEventPayload().getEventTypes(1));
    }
}
