package com.fanaujie.ripple.communication.gateway;

import com.fanaujie.ripple.protobuf.msgapiserver.SendMessageReq;
import com.fanaujie.ripple.protobuf.msggateway.*;
import com.fanaujie.ripple.protobuf.push.SSEEventType;
import com.fanaujie.ripple.protobuf.userpresence.UserOnlineInfo;

import java.util.List;

public final class GatewayRequestBuilder {

    private GatewayRequestBuilder() {}

    public static BatchPushMessageRequest buildSSEBatchRequest(
            List<UserOnlineInfo> userInfos,
            long sendUserId,
            String conversationId,
            SSEEventType eventType,
            String content,
            long messageId,
            long sendTimestamp) {

        BatchPushMessageRequest.Builder batchBuilder = BatchPushMessageRequest.newBuilder();

        for (UserOnlineInfo userInfo : userInfos) {
            PushSSEPayload ssePayload =
                    PushSSEPayload.newBuilder()
                            .setEventType(eventType)
                            .setConversationId(conversationId)
                            .setContent(content != null ? content : "")
                            .setMessageId(messageId)
                            .setSendTimestamp(sendTimestamp)
                            .build();

            PushMessageRequest request =
                    PushMessageRequest.newBuilder()
                            .setSendUserId(String.valueOf(sendUserId))
                            .setReceiveUserId(userInfo.getUserId())
                            .setReceiveDeviceId(userInfo.getDeviceId())
                            .setSsePayload(ssePayload)
                            .build();

            batchBuilder.addRequests(request);
        }

        return batchBuilder.build();
    }

    public static PushMessageRequest buildSSERequest(
            UserOnlineInfo userInfo,
            long sendUserId,
            String conversationId,
            SSEEventType eventType,
            String content,
            long messageId,
            long sendTimestamp) {

        PushSSEPayload ssePayload =
                PushSSEPayload.newBuilder()
                        .setEventType(eventType)
                        .setConversationId(conversationId)
                        .setContent(content != null ? content : "")
                        .setMessageId(messageId)
                        .setSendTimestamp(sendTimestamp)
                        .build();

        return PushMessageRequest.newBuilder()
                .setSendUserId(String.valueOf(sendUserId))
                .setReceiveUserId(userInfo.getUserId())
                .setReceiveDeviceId(userInfo.getDeviceId())
                .setSsePayload(ssePayload)
                .build();
    }

    public static PushMessageRequest buildMessageRequest(
            UserOnlineInfo userInfo,
            long sendUserId,
            PushMessageType messageType,
            SendMessageReq messageData,
            int unreadCount) {

        PushMessagePayload messagePayload =
                PushMessagePayload.newBuilder()
                        .setMessageType(messageType)
                        .setMessageData(messageData)
                        .setUnreadCount(unreadCount)
                        .build();

        return PushMessageRequest.newBuilder()
                .setSendUserId(String.valueOf(sendUserId))
                .setReceiveUserId(userInfo.getUserId())
                .setReceiveDeviceId(userInfo.getDeviceId())
                .setMessagePayload(messagePayload)
                .build();
    }

    public static PushMessageRequest buildEventRequest(
            UserOnlineInfo userInfo, long sendUserId, List<PushEventType> eventTypes) {

        PushEventPayload.Builder eventPayloadBuilder = PushEventPayload.newBuilder();
        for (PushEventType eventType : eventTypes) {
            eventPayloadBuilder.addEventTypes(eventType);
        }

        return PushMessageRequest.newBuilder()
                .setSendUserId(String.valueOf(sendUserId))
                .setReceiveUserId(userInfo.getUserId())
                .setReceiveDeviceId(userInfo.getDeviceId())
                .setEventPayload(eventPayloadBuilder.build())
                .build();
    }
}
