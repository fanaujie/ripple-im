package com.fanaujie.ripple.pushserver.service.batch;

import com.fanaujie.ripple.communication.batch.BatchProcessorFactory;
import com.fanaujie.ripple.communication.grpc.client.GrpcClient;
import com.fanaujie.ripple.protobuf.msggateway.BatchPushMessageRequest;
import com.fanaujie.ripple.protobuf.msggateway.BatchPushMessageResponse;
import com.fanaujie.ripple.protobuf.msggateway.MessageGatewayGrpc;
import com.fanaujie.ripple.protobuf.msggateway.PushMessageRequest;
import com.fanaujie.ripple.protobuf.msggateway.PushMessageResponse;
import com.fanaujie.ripple.protobuf.msggateway.PushMessageType;
import com.fanaujie.ripple.protobuf.push.PushEventData;
import com.fanaujie.ripple.protobuf.push.PushMessage;
import com.fanaujie.ripple.protobuf.userpresence.UserOnlineInfo;
import com.fanaujie.ripple.pushserver.service.grpc.MessageGatewayClientManager;
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

    private final MessageGatewayClientManager messageGatewayManager;

    public GatewayPushBatchProcessor(MessageGatewayClientManager messageGatewayManager) {
        this.messageGatewayManager = messageGatewayManager;
    }

    @Override
    public void process(List<GatewayPushTask> batch) {
        if (batch == null || batch.isEmpty()) {
            logger.debug("process: Batch is null or empty, skipping processing");
            return;
        }

        Map<String, List<GatewayPushTask>> tasksByServer =
                batch.stream().collect(Collectors.groupingBy(GatewayPushTask::serverAddress));
        tasksByServer.forEach(
                (server, tasks) -> {
                    this.processServerTasks(server, tasks);
                });
    }

    private void processServerTasks(String serverAddress, List<GatewayPushTask> tasks) {
        Optional<GrpcClient<MessageGatewayGrpc.MessageGatewayStub>> c =
                messageGatewayManager.getClient(serverAddress);

        if (c.isEmpty()) {
            logger.warn(
                    "processServerTasks: No client found for MessageGateway server: {}. Skipping {} tasks.",
                    serverAddress,
                    tasks.size());
            return;
        }
        try {
            for (GatewayPushTask task : tasks) {
                processSingleTask(c.get().getStub(), task);
            }
        } catch (Exception e) {
            logger.error(
                    "processServerTasks: Error processing {} tasks for gateway server: {}",
                    tasks.size(),
                    serverAddress,
                    e);
        }
    }

    private void processSingleTask(
            MessageGatewayGrpc.MessageGatewayStub stub, GatewayPushTask task) {
        // Build batch request
        BatchPushMessageRequest.Builder batchRequestBuilder = BatchPushMessageRequest.newBuilder();
        for (UserOnlineInfo userInfo : task.userInfos()) {
            PushMessageRequest request = createPushRequest(task.pushMessage(), userInfo);
            batchRequestBuilder.addRequests(request);
        }
        BatchPushMessageRequest batchRequest = batchRequestBuilder.build();
        stub.pushMessageToUser(
                batchRequest,
                new StreamObserver<BatchPushMessageResponse>() {
                    @Override
                    public void onNext(BatchPushMessageResponse batchResponse) {}

                    @Override
                    public void onError(Throwable throwable) {}

                    @Override
                    public void onCompleted() {}
                });
    }

    private PushMessageRequest createPushRequest(PushMessage pushMessage, UserOnlineInfo userInfo) {
        switch (pushMessage.getPayloadCase()) {
            case EVENT_DATA:
                {
                    PushMessageType pushType;
                    PushEventData eventData = pushMessage.getEventData();
                    switch (eventData.getEventType()) {
                        case EVENT_TYPE_SELF_INFO_UPDATE:
                            pushType = PushMessageType.PUSH_MESSAGE_TYPE_RELATION_INFO_UPDATE;
                            if (userInfo.getUserId()
                                    .equals(String.valueOf(eventData.getSendUserId()))) {
                                pushType = PushMessageType.PUSH_MESSAGE_TYPE_SELF_INFO_UPDATE;
                            }
                            break;
                        case EVENT_TYPE_RELATION_UPDATE:
                            pushType = PushMessageType.PUSH_MESSAGE_TYPE_RELATION_INFO_UPDATE;
                            break;
                        default:
                            throw new IllegalArgumentException(
                                    "Unsupported event type: " + eventData.getEventType());
                    }
                    return PushMessageRequest.newBuilder()
                            .setPushMessageType(pushType)
                            .setSendUserId(String.valueOf(eventData.getSendUserId()))
                            .setReceiveUserId(userInfo.getUserId())
                            .setRequestDeviceId(userInfo.getDeviceId()) // not set even data
                            .build();
                }
            case MESSAGE_DATA:
                {
                    return PushMessageRequest.newBuilder()
                            .setPushMessageType(PushMessageType.PUSH_MESSAGE_TYPE_SINGLE_MESSAGE)
                            .setSendUserId(
                                    String.valueOf(pushMessage.getMessageData().getSendUserId()))
                            .setReceiveUserId(userInfo.getUserId())
                            .setRequestDeviceId(userInfo.getDeviceId())
                            .setMessageData(pushMessage.getMessageData().getData())
                            .build();
                }
            default:
                logger.error(
                        "createPushRequest: Unknown payload type: {}",
                        pushMessage.getPayloadCase());
                throw new IllegalArgumentException(
                        "Unknown payload type: " + pushMessage.getPayloadCase());
        }
    }
}
