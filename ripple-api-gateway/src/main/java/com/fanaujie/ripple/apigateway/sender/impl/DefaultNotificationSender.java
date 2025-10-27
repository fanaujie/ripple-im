package com.fanaujie.ripple.apigateway.sender.impl;

import com.fanaujie.ripple.apigateway.sender.NotificationSender;
import com.fanaujie.ripple.communication.grpc.client.GrpcClient;
import com.fanaujie.ripple.protobuf.msgapiserver.MessageAPIGrpc;
import com.fanaujie.ripple.protobuf.msgapiserver.RelationEvent;
import com.fanaujie.ripple.protobuf.msgapiserver.SelfInfoUpdateEvent;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq;
import io.grpc.Status;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;

@Component
@Slf4j
public class DefaultNotificationSender implements NotificationSender {

    private final ExecutorService executorService;
    private final GrpcClient<MessageAPIGrpc.MessageAPIBlockingStub> msgAPIClient;

    public DefaultNotificationSender(
            ExecutorService executorService,
            GrpcClient<MessageAPIGrpc.MessageAPIBlockingStub> msgAPIClientPool) {
        this.executorService = executorService;
        this.msgAPIClient = msgAPIClientPool;
    }

    @Override
    public void sendFriendNotification(
            long initiatorId, long friendId, RelationEvent.EventType eventType) {
        log.debug(
                "sendFriendNotification: Submitting friend event - initiatorId: {}, friendId: {}, eventType: {}",
                initiatorId,
                friendId,
                eventType);
        this.executorService.submit(
                () -> {
                    log.debug(
                            "sendFriendNotification: Processing friend event for initiatorId: {}, friendId: {}",
                            initiatorId,
                            friendId);
                    SendEventReq req =
                            SendEventReq.newBuilder()
                                    .setRelationEvent(
                                            RelationEvent.newBuilder()
                                                    .setUserId(initiatorId)
                                                    .setTargetUserId(friendId)
                                                    .setEventType(eventType)
                                                    .build())
                                    .build();
                    log.debug("sendFriendNotification: SendEventReq constructed successfully");
                    try {
                        this.msgAPIClient.getStub().sendEvent(req);
                        log.debug(
                                "sendFriendNotification: Friend event sent successfully for initiatorId: {}, friendId: {}",
                                initiatorId,
                                friendId);
                    } catch (Exception e) {
                        Status status = Status.fromThrowable(e);
                        log.error(
                                "sendFriendNotification: Failed to dispatch friend event from user {} to user {}, grpc code: {} description: {}",
                                initiatorId,
                                friendId,
                                status.getCode(),
                                status.getDescription(),
                                e);
                    }
                });
    }

    @Override
    public void sendBlockNotification(
            long initiatorId, long targetUserId, RelationEvent.EventType eventType) {
        log.debug(
                "sendBlockNotification: Submitting block event - initiatorId: {}, targetUserId: {}, eventType: {}",
                initiatorId,
                targetUserId,
                eventType);
        this.executorService.submit(
                () -> {
                    log.debug(
                            "sendBlockNotification: Processing block event for initiatorId: {}, targetUserId: {}",
                            initiatorId,
                            targetUserId);
                    SendEventReq req =
                            SendEventReq.newBuilder()
                                    .setRelationEvent(
                                            RelationEvent.newBuilder()
                                                    .setUserId(initiatorId)
                                                    .setTargetUserId(targetUserId)
                                                    .setEventType(eventType)
                                                    .build())
                                    .build();
                    log.debug("sendBlockNotification: SendEventReq constructed successfully");
                    try {
                        this.msgAPIClient.getStub().sendEvent(req);
                        log.debug(
                                "sendBlockNotification: Block event sent successfully for initiatorId: {}, targetUserId: {}",
                                initiatorId,
                                targetUserId);
                    } catch (Exception e) {
                        Status status = Status.fromThrowable(e);
                        log.error(
                                "sendBlockNotification: Failed to dispatch block event from user {} to user {}, grpc code: {} description: {}",
                                initiatorId,
                                targetUserId,
                                status.getCode(),
                                status.getDescription(),
                                e);
                    }
                });
    }

    @Override
    public void sendSelfInfoUpdatedNotification(long userId) {
        log.debug(
                "sendSelfInfoUpdatedNotification: Submitting self info update event for userId: {}",
                userId);
        this.executorService.submit(
                () -> {
                    log.debug(
                            "sendSelfInfoUpdatedNotification: Processing self info update for userId: {}",
                            userId);
                    SendEventReq req =
                            SendEventReq.newBuilder()
                                    .setSelfInfoUpdateEvent(
                                            SelfInfoUpdateEvent.newBuilder()
                                                    .setUserId(userId)
                                                    .build())
                                    .build();
                    log.debug(
                            "sendSelfInfoUpdatedNotification: SendEventReq constructed successfully");
                    try {
                        this.msgAPIClient.getStub().sendEvent(req);
                        log.debug(
                                "sendSelfInfoUpdatedNotification: Self info update event sent successfully for userId: {}",
                                userId);
                    } catch (Exception e) {
                        Status status = Status.fromThrowable(e);
                        log.error(
                                "sendSelfInfoUpdatedNotification: Failed to dispatch self info update event for user {}, grpc code: {} description: {}",
                                userId,
                                status.getCode(),
                                status.getDescription(),
                                e);
                    }
                });
    }
}
