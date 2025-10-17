package com.fanaujie.ripple.pushserver.service;

import com.fanaujie.ripple.communication.batch.BatchExecutorService;
import com.fanaujie.ripple.communication.batch.Config;
import com.fanaujie.ripple.communication.grpc.client.GrpcClientPool;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;
import com.fanaujie.ripple.protobuf.userpresence.QueryUserOnlineReq;
import com.fanaujie.ripple.protobuf.userpresence.QueryUserOnlineResp;
import com.fanaujie.ripple.protobuf.userpresence.UserOnlineInfo;
import com.fanaujie.ripple.protobuf.userpresence.UserPresenceGrpc;
import com.fanaujie.ripple.pushserver.service.batch.GatewayPushBatchProcessorFactory;
import com.fanaujie.ripple.pushserver.service.batch.GatewayPushTask;
import com.fanaujie.ripple.pushserver.service.grpc.MessageGatewayClientPoolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PushService {
    private static final Logger logger = LoggerFactory.getLogger(PushService.class);

    private final GrpcClientPool<UserPresenceGrpc.UserPresenceBlockingStub> userPresenceClientPool;
    private final BatchExecutorService<GatewayPushTask> batchExecutor;

    public PushService(
            GrpcClientPool<UserPresenceGrpc.UserPresenceBlockingStub> userPresenceClientPool,
            MessageGatewayClientPoolManager messageGatewayManager,
            Config batchConfig) {
        this.userPresenceClientPool = userPresenceClientPool;
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
        try {
            userPresenceClientPool.execute(
                    c -> {
                        List<Long> receiveUserIds = getReceiveUserIds(value);
                        if (receiveUserIds.isEmpty()) {
                            logger.debug("No receive user IDs found in message payload");
                            return;
                        }

                        QueryUserOnlineReq req =
                                QueryUserOnlineReq.newBuilder()
                                        .addAllUserIds(receiveUserIds)
                                        .build();
                        QueryUserOnlineResp resp = c.queryUserOnline(req);

                        // Group users by their gateway server location
                        Map<String, List<UserOnlineInfo>> usersByServer =
                                resp.getUserOnlineInfosList().stream()
                                        .collect(
                                                Collectors.groupingBy(
                                                        UserOnlineInfo::getServerLocation));

                        // Submit push tasks to batch executor
                        for (Map.Entry<String, List<UserOnlineInfo>> entry :
                                usersByServer.entrySet()) {
                            String serverAddress = entry.getKey();
                            List<UserOnlineInfo> userInfos = entry.getValue();
                            if (userInfos.isEmpty()) {
                                continue;
                            }
                            GatewayPushTask task =
                                    new GatewayPushTask(serverAddress, userInfos, value);
                            try {
                                batchExecutor.push(task);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException(e);
                            }
                        }
                    });
        } catch (Exception e) {
            logger.error("Error processing message payload", e);
        }
    }

    public void close() throws InterruptedException {
        logger.info("Shutting down PushService...");
        batchExecutor.shutdown();
        batchExecutor.awaitTermination();
        logger.info("PushService shutdown complete");
    }

    private List<Long> getReceiveUserIds(MessagePayload messagePayload) {
        switch (messagePayload.getPayloadCase()) {
            case EVENT_DATA:
                return messagePayload.getEventData().getReceiveUserIdsList();
            case MESSAGE_DATA:
                break;
            case PAYLOAD_NOT_SET:
            default:
                logger.warn("Unknown payload type: {}", messagePayload.getPayloadCase());
        }
        return List.of();
    }
}
