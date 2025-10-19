package com.fanaujie.ripple.pushserver.service.batch;

import com.fanaujie.ripple.communication.batch.BatchProcessorFactory;
import com.fanaujie.ripple.communication.grpc.client.GrpcClientPool;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;
import com.fanaujie.ripple.protobuf.msggateway.MessageGatewayGrpc;
import com.fanaujie.ripple.protobuf.msggateway.PushMessageRequest;
import com.fanaujie.ripple.protobuf.msggateway.PushMessageResponse;
import com.fanaujie.ripple.protobuf.msggateway.PushMessageType;
import com.fanaujie.ripple.protobuf.userpresence.UserOnlineInfo;
import com.fanaujie.ripple.pushserver.service.grpc.MessageGatewayClientPoolManager;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class GatewayPushBatchProcessor
        implements BatchProcessorFactory.BatchProcessor<GatewayPushTask> {
    private static final Logger logger = LoggerFactory.getLogger(GatewayPushBatchProcessor.class);

    private final MessageGatewayClientPoolManager messageGatewayManager;

    public GatewayPushBatchProcessor(MessageGatewayClientPoolManager messageGatewayManager) {
        this.messageGatewayManager = messageGatewayManager;
    }

    @Override
    public void process(List<GatewayPushTask> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        Map<String, List<GatewayPushTask>> tasksByServer =
                batch.stream().collect(Collectors.groupingBy(GatewayPushTask::serverAddress));
        tasksByServer.forEach(this::processServerTasks);
    }

    private void processServerTasks(String serverAddress, List<GatewayPushTask> tasks) {
        Optional<GrpcClientPool<MessageGatewayGrpc.MessageGatewayStub>> poolOpt =
                messageGatewayManager.getClientPool(serverAddress);

        if (poolOpt.isEmpty()) {
            logger.warn(
                    "No client pool found for MessageGateway server: {}. Skipping {} tasks.",
                    serverAddress,
                    tasks.size());
            return;
        }

        try {
            poolOpt.get()
                    .execute(
                            stub -> {
                                for (GatewayPushTask task : tasks) {
                                    processSingleTask(stub, task);
                                }
                            });
        } catch (Exception e) {
            logger.error(
                    "Error processing {} tasks for gateway server: {}",
                    tasks.size(),
                    serverAddress,
                    e);
        }
    }

    private void processSingleTask(
            MessageGatewayGrpc.MessageGatewayStub stub, GatewayPushTask task) {
        StreamObserver<PushMessageResponse> streamObserver =
                new StreamObserver<PushMessageResponse>() {
                    @Override
                    public void onNext(PushMessageResponse pushMessageResponse) {}

                    @Override
                    public void onError(Throwable throwable) {}

                    @Override
                    public void onCompleted() {}
                };
        StreamObserver<PushMessageRequest> requestObserver = stub.pushMessageToUser(streamObserver);
        for (UserOnlineInfo userInfo : task.userInfos()) {
            requestObserver.onNext(createPushRequest(task.messagePayload(), userInfo));
        }
        requestObserver.onCompleted();
    }

    private PushMessageRequest createPushRequest(
            MessagePayload messagePayload, UserOnlineInfo userInfo) {
        switch (messagePayload.getPayloadCase()) {
            case EVENT_DATA:
                {
                    PushMessageType pushType;
                    switch (messagePayload.getEventData().getEventType()) {
                        case EVENT_TYPE_SELF_INFO_UPDATE:
                            pushType = PushMessageType.PUSH_MESSAGE_TYPE_RELATION_INFO_UPDATE;
                            if (userInfo.getUserId()
                                    == messagePayload.getEventData().getSendUserId()) {
                                pushType = PushMessageType.PUSH_MESSAGE_TYPE_SELF_INFO_UPDATE;
                            }
                            break;
                        case EVENT_TYPE_RELATION_UPDATE:
                            pushType = PushMessageType.PUSH_MESSAGE_TYPE_RELATION_INFO_UPDATE;
                            break;
                        default:
                            throw new IllegalArgumentException(
                                    "Unsupported event type: "
                                            + messagePayload.getEventData().getEventType());
                    }
                    return PushMessageRequest.newBuilder()
                            .setPushMessageType(pushType)
                            .setReceiveUserId(userInfo.getUserId())
                            .setContent(messagePayload.getEventData().getContent())
                            .build();
                }
            case MESSAGE_DATA:
                {
                }
            default:
                {
                    throw new IllegalArgumentException(
                            "Unsupported payload type: " + messagePayload.getPayloadCase());
                }
        }
    }
}
