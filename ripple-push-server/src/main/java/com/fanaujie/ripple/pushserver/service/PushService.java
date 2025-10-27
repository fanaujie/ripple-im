package com.fanaujie.ripple.pushserver.service;

import com.fanaujie.ripple.communication.batch.BatchExecutorService;
import com.fanaujie.ripple.communication.batch.Config;
import com.fanaujie.ripple.communication.grpc.client.GrpcClient;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;
import com.fanaujie.ripple.protobuf.userpresence.QueryUserOnlineReq;
import com.fanaujie.ripple.protobuf.userpresence.QueryUserOnlineResp;
import com.fanaujie.ripple.protobuf.userpresence.UserOnlineInfo;
import com.fanaujie.ripple.protobuf.userpresence.UserPresenceGrpc;
import com.fanaujie.ripple.pushserver.service.batch.GatewayPushBatchProcessorFactory;
import com.fanaujie.ripple.pushserver.service.batch.GatewayPushTask;
import com.fanaujie.ripple.pushserver.service.grpc.MessageGatewayClientManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PushService {
    private static final Logger logger = LoggerFactory.getLogger(PushService.class);

    private final GrpcClient<UserPresenceGrpc.UserPresenceBlockingStub> userPresenceClient;
    private final BatchExecutorService<GatewayPushTask> batchExecutor;

    public PushService(
            GrpcClient<UserPresenceGrpc.UserPresenceBlockingStub> userPresenceClientPool,
            MessageGatewayClientManager messageGatewayManager,
            Config batchConfig) {
        this.userPresenceClient = userPresenceClientPool;
        this.batchExecutor =
                new BatchExecutorService<>(
                        batchConfig, new GatewayPushBatchProcessorFactory(messageGatewayManager));
        logger.info(
                "PushService initialized with batch config: queueSize={}, workerSize={}, "
                        + "batchMaxSize={}, timeoutMs={}",
                batchConfig.queueSize(),
                batchConfig.workerSize(),
                batchConfig.batchMaxSize(),
                batchConfig.queueTimeoutMs());
    }

    public void processMessagePayload(String key, MessagePayload value) {
        logger.debug(
                "processMessagePayload: Processing message with key: {}, payloadCase: {}",
                key,
                value.getPayloadCase());

        try {
            logger.debug("processMessagePayload: Extracting receive user IDs from payload");
            List<String> receiveUserIds = getReceiveUserIds(value);

            if (receiveUserIds.isEmpty()) {
                logger.debug("processMessagePayload: No receive user IDs found in message payload");
                return;
            }

            logger.debug("processMessagePayload: Found {} receive user IDs", receiveUserIds.size());

            QueryUserOnlineReq req =
                    QueryUserOnlineReq.newBuilder().addAllUserIds(receiveUserIds).build();
            logger.debug(
                    "processMessagePayload: Querying user online status for {} users",
                    receiveUserIds.size());
            QueryUserOnlineResp resp = userPresenceClient.getStub().queryUserOnline(req);
            logger.debug(
                    "processMessagePayload: Received {} online user infos",
                    resp.getUserOnlineInfosList().size());

            // Group users by their gateway server location
            Map<String, List<UserOnlineInfo>> usersByServer =
                    resp.getUserOnlineInfosList().stream()
                            .collect(Collectors.groupingBy(UserOnlineInfo::getServerLocation));
            logger.debug(
                    "processMessagePayload: Users grouped by {} gateway servers",
                    usersByServer.size());

            // Submit push tasks to batch executor
            for (Map.Entry<String, List<UserOnlineInfo>> entry : usersByServer.entrySet()) {
                String serverAddress = entry.getKey();
                List<UserOnlineInfo> userInfos = entry.getValue();

                if (userInfos.isEmpty()) {
                    logger.debug(
                            "processMessagePayload: Skipping empty user list for server: {}",
                            serverAddress);
                    continue;
                }

                logger.debug(
                        "processMessagePayload: Creating push task for server: {} with {} users",
                        serverAddress,
                        userInfos.size());

                GatewayPushTask task = new GatewayPushTask(serverAddress, userInfos, value);
                try {
                    batchExecutor.push(task);
                    logger.debug(
                            "processMessagePayload: Push task submitted successfully for server: {}",
                            serverAddress);
                } catch (InterruptedException e) {
                    logger.error(
                            "processMessagePayload: Interrupted while pushing task for server: {}",
                            serverAddress,
                            e);
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
            logger.debug("processMessagePayload: All push tasks submitted successfully");
        } catch (Exception e) {
            logger.error(
                    "processMessagePayload: Error processing message payload with key: {}", key, e);
        }
    }

    public void close() throws InterruptedException {
        logger.info("Shutting down PushService...");
        logger.debug("close: Initiating batch executor shutdown");
        batchExecutor.shutdown();
        logger.debug("close: Shutdown initiated, awaiting termination");
        batchExecutor.awaitTermination();
        logger.info("close: PushService shutdown complete");
    }

    private List<String> getReceiveUserIds(MessagePayload messagePayload) {
        logger.debug(
                "getReceiveUserIds: Processing payload with type: {}",
                messagePayload.getPayloadCase());

        switch (messagePayload.getPayloadCase()) {
            case EVENT_DATA:
                logger.debug("getReceiveUserIds: Extracting receive user IDs from EVENT_DATA");
                List<String> userIds =
                        messagePayload.getEventData().getReceiveUserIdsList().stream()
                                .map(id -> Long.toString(id))
                                .collect(Collectors.toList());
                logger.debug(
                        "getReceiveUserIds: Extracted {} user IDs from EVENT_DATA", userIds.size());
                return userIds;
            case MESSAGE_DATA:
                logger.debug("getReceiveUserIds: MESSAGE_DATA payload type not yet implemented");
                break;
            case PAYLOAD_NOT_SET:
            default:
                logger.warn(
                        "getReceiveUserIds: Unknown payload type: {}",
                        messagePayload.getPayloadCase());
        }
        logger.debug("getReceiveUserIds: Returning empty user ID list");
        return List.of();
    }
}
