package com.fanaujie.ripple.apigateway.sender.impl;

import com.fanaujie.ripple.apigateway.sender.NotificationSender;
import com.fanaujie.ripple.communication.grpc.client.GrpcClientPool;
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
    private final GrpcClientPool<MessageAPIGrpc.MessageAPIBlockingStub> msgAPIClientPool;

    public DefaultNotificationSender(
            ExecutorService executorService,
            GrpcClientPool<MessageAPIGrpc.MessageAPIBlockingStub> msgAPIClientPool) {
        this.executorService = executorService;
        this.msgAPIClientPool = msgAPIClientPool;
    }

    @Override
    public void sendFriendNotification(
            long initiatorId, long friendId, RelationEvent.EventType eventType) {
        this.executorService.submit(
                () -> {
                    SendEventReq req =
                            SendEventReq.newBuilder()
                                    .setRelationEvent(
                                            RelationEvent.newBuilder()
                                                    .setUserId(initiatorId)
                                                    .setTargetUserId(friendId)
                                                    .setEventType(eventType)
                                                    .build())
                                    .build();
                    try {
                        this.msgAPIClientPool.execute(stub -> stub.sendEvent(req));
                    } catch (Exception e) {
                        Status status = Status.fromThrowable(e);
                        log.error(
                                "Failed to dispatch friend event from user {} to user {}, grpc code: {} description: {}",
                                initiatorId,
                                friendId,
                                status.getCode(),
                                status.getDescription());
                    }
                });
    }

    @Override
    public void sendBlockNotification(
            long initiatorId, long targetUserId, RelationEvent.EventType eventType) {
        this.executorService.submit(
                () -> {
                    SendEventReq req =
                            SendEventReq.newBuilder()
                                    .setRelationEvent(
                                            RelationEvent.newBuilder()
                                                    .setUserId(initiatorId)
                                                    .setTargetUserId(targetUserId)
                                                    .setEventType(eventType)
                                                    .build())
                                    .build();
                    try {
                        this.msgAPIClientPool.execute(stub -> stub.sendEvent(req));
                    } catch (Exception e) {
                        Status status = Status.fromThrowable(e);
                        log.error(
                                "Failed to dispatch block event from user {} to user {}, grpc code: {} description: {}",
                                initiatorId,
                                targetUserId,
                                status.getCode(),
                                status.getDescription());
                    }
                });
    }

    @Override
    public void sendSelfInfoUpdatedNotification(long userId) {
        this.executorService.submit(
                () -> {
                    SendEventReq req =
                            SendEventReq.newBuilder()
                                    .setSelfInfoUpdateEvent(
                                            SelfInfoUpdateEvent.newBuilder()
                                                    .setUserId(userId)
                                                    .build())
                                    .build();
                    try {
                        this.msgAPIClientPool.execute(stub -> stub.sendEvent(req));
                    } catch (Exception e) {
                        Status status = Status.fromThrowable(e);
                        log.error(
                                "Failed to dispatch self info update event for user {}, grpc code: {} description: {}",
                                userId,
                                status.getCode(),
                                status.getDescription());
                    }
                });
    }
}
