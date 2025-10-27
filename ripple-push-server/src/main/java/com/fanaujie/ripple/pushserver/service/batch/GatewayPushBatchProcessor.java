package com.fanaujie.ripple.pushserver.service.batch;

import com.fanaujie.ripple.communication.batch.BatchProcessorFactory;
import com.fanaujie.ripple.communication.grpc.client.GrpcClient;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;
import com.fanaujie.ripple.protobuf.msggateway.MessageGatewayGrpc;
import com.fanaujie.ripple.protobuf.msggateway.PushMessageRequest;
import com.fanaujie.ripple.protobuf.msggateway.PushMessageResponse;
import com.fanaujie.ripple.protobuf.msggateway.PushMessageType;
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
        logger.debug(
                "process: Starting batch processing with {} tasks",
                batch == null ? 0 : batch.size());

        if (batch == null || batch.isEmpty()) {
            logger.debug("process: Batch is null or empty, skipping processing");
            return;
        }

        Map<String, List<GatewayPushTask>> tasksByServer =
                batch.stream().collect(Collectors.groupingBy(GatewayPushTask::serverAddress));
        logger.debug("process: Tasks grouped by {} servers", tasksByServer.size());

        tasksByServer.forEach(
                (server, tasks) -> {
                    logger.debug(
                            "process: Processing {} tasks for server: {}", tasks.size(), server);
                    this.processServerTasks(server, tasks);
                });
    }

    private void processServerTasks(String serverAddress, List<GatewayPushTask> tasks) {
        logger.debug("processServerTasks: Looking up client pool for server: {}", serverAddress);

        Optional<GrpcClient<MessageGatewayGrpc.MessageGatewayStub>> c =
                messageGatewayManager.getClient(serverAddress);

        if (c.isEmpty()) {
            logger.warn(
                    "processServerTasks: No client found for MessageGateway server: {}. Skipping {} tasks.",
                    serverAddress,
                    tasks.size());
            return;
        }

        logger.debug(
                "processServerTasks: Client found for server: {}, executing {} tasks",
                serverAddress,
                tasks.size());

        try {

            logger.debug("processServerTasks: gRPC stub acquired, processing tasks");
            for (GatewayPushTask task : tasks) {
                logger.debug(
                        "processServerTasks: Processing single task with {} user infos",
                        task.userInfos().size());
                processSingleTask(c.get().getStub(), task);
            }
            logger.debug("processServerTasks: All tasks processed successfully");
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
        logger.debug(
                "processSingleTask: Starting single task processing for {} users",
                task.userInfos().size());

        StreamObserver<PushMessageResponse> streamObserver =
                new StreamObserver<PushMessageResponse>() {
                    @Override
                    public void onNext(PushMessageResponse pushMessageResponse) {
                        logger.debug(
                                "processSingleTask.onNext: Received push response - receiveUserId: {}, isSuccess: {}",
                                pushMessageResponse.getReceiveUserId(),
                                pushMessageResponse.getIsSuccess());
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        logger.error(
                                "processSingleTask.onError: Error in push response stream: {}",
                                throwable.getMessage(),
                                throwable);
                    }

                    @Override
                    public void onCompleted() {
                        logger.debug(
                                "processSingleTask.onCompleted: Push response stream completed");
                    }
                };

        logger.debug("processSingleTask: Creating request observer");
        StreamObserver<PushMessageRequest> requestObserver = stub.pushMessageToUser(streamObserver);

        logger.debug("processSingleTask: Sending {} push requests", task.userInfos().size());
        for (UserOnlineInfo userInfo : task.userInfos()) {
            PushMessageRequest request = createPushRequest(task.messagePayload(), userInfo);
            logger.debug(
                    "processSingleTask: Sending push request for userId: {}, deviceId: {}",
                    userInfo.getUserId(),
                    userInfo.getDeviceId());
            requestObserver.onNext(request);
        }

        logger.debug("processSingleTask: Completing request stream");
        requestObserver.onCompleted();
        logger.debug("processSingleTask: Single task processing completed");
    }

    private PushMessageRequest createPushRequest(
            MessagePayload messagePayload, UserOnlineInfo userInfo) {
        logger.debug(
                "createPushRequest: Creating push request for userId: {}, deviceId:{} payloadCase: {}",
                userInfo.getUserId(),
                userInfo.getDeviceId(),
                messagePayload.getPayloadCase());

        switch (messagePayload.getPayloadCase()) {
            case EVENT_DATA:
                {
                    logger.debug("createPushRequest: Processing EVENT_DATA payload");
                    PushMessageType pushType;
                    switch (messagePayload.getEventData().getEventType()) {
                        case EVENT_TYPE_SELF_INFO_UPDATE:
                            logger.debug("createPushRequest: Event type is SELF_INFO_UPDATE");
                            pushType = PushMessageType.PUSH_MESSAGE_TYPE_RELATION_INFO_UPDATE;
                            if (userInfo.getUserId()
                                    .equals(
                                            String.valueOf(
                                                    messagePayload
                                                            .getEventData()
                                                            .getSendUserId()))) {
                                logger.debug(
                                        "createPushRequest: User is sender, setting push type to SELF_INFO_UPDATE");
                                pushType = PushMessageType.PUSH_MESSAGE_TYPE_SELF_INFO_UPDATE;
                            }
                            break;
                        case EVENT_TYPE_RELATION_UPDATE:
                            logger.debug("createPushRequest: Event type is RELATION_UPDATE");
                            pushType = PushMessageType.PUSH_MESSAGE_TYPE_RELATION_INFO_UPDATE;
                            break;
                        default:
                            logger.error(
                                    "createPushRequest: Unsupported event type: {}",
                                    messagePayload.getEventData().getEventType());
                            throw new IllegalArgumentException(
                                    "Unsupported event type: "
                                            + messagePayload.getEventData().getEventType());
                    }
                    logger.debug(
                            "createPushRequest: Creating PushMessageRequest with type: {}",
                            pushType);
                    return PushMessageRequest.newBuilder()
                            .setPushMessageType(pushType)
                            .setSendUserId(
                                    String.valueOf(messagePayload.getEventData().getSendUserId()))
                            .setReceiveUserId(userInfo.getUserId())
                            .setRequestDeviceId(userInfo.getDeviceId())
                            .setContent(messagePayload.getEventData().getContent())
                            .build();
                }
            case MESSAGE_DATA:
                {
                    logger.debug("createPushRequest: Processing MESSAGE_DATA payload");
                }
            default:
                {
                    logger.error(
                            "createPushRequest: Unsupported payload type: {}",
                            messagePayload.getPayloadCase());
                    throw new IllegalArgumentException(
                            "Unsupported payload type: " + messagePayload.getPayloadCase());
                }
        }
    }
}
