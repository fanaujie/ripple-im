package com.fanaujie.ripple.pushserver.service;

import com.fanaujie.ripple.communication.batch.BatchExecutorService;
import com.fanaujie.ripple.communication.batch.Config;
import com.fanaujie.ripple.communication.gateway.GatewayConnectionManager;
import com.fanaujie.ripple.communication.grpc.client.GrpcClient;
import com.fanaujie.ripple.communication.msgqueue.MessageRecord;
import com.fanaujie.ripple.protobuf.push.PushMessage;
import com.fanaujie.ripple.protobuf.userpresence.QueryUserOnlineReq;
import com.fanaujie.ripple.protobuf.userpresence.QueryUserOnlineResp;
import com.fanaujie.ripple.protobuf.userpresence.UserOnlineInfo;
import com.fanaujie.ripple.protobuf.userpresence.UserPresenceGrpc;
import com.fanaujie.ripple.pushserver.service.batch.GatewayPushBatchProcessorFactory;
import com.fanaujie.ripple.pushserver.service.batch.GatewayPushTask;
import com.fanaujie.ripple.cache.service.ConversationSummaryStorage;
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
            GatewayConnectionManager gatewayConnectionManager,
            Config batchConfig,
            ConversationSummaryStorage conversationStorage) {
        this.userPresenceClient = userPresenceClientPool;
        this.batchExecutor =
                new BatchExecutorService<>(
                        batchConfig,
                        new GatewayPushBatchProcessorFactory(
                                gatewayConnectionManager, conversationStorage));
        logger.info(
                "PushService initialized with batch config: queueSize={}, workerSize={}, "
                        + "batchMaxSize={}, timeoutMs={}",
                batchConfig.queueSize(),
                batchConfig.workerSize(),
                batchConfig.batchMaxSize(),
                batchConfig.queueTimeoutMs());
    }

    public void processPushMessageBatch(List<MessageRecord<String, PushMessage>> records) {
        for (MessageRecord<String, PushMessage> record : records) {
            processMessage(record.key(), record.value());
        }
    }

    public void processMessage(String key, PushMessage value) {
        try {
            List<String> receiveUserIds = getReceiveUserIds(value);

            if (receiveUserIds.isEmpty()) {
                return;
            }
            QueryUserOnlineReq req =
                    QueryUserOnlineReq.newBuilder().addAllUserIds(receiveUserIds).build();
            QueryUserOnlineResp resp = userPresenceClient.getStub().queryUserOnline(req);
            // Group users by their gateway server location
            Map<String, List<UserOnlineInfo>> usersByServer =
                    resp.getUserOnlineInfosList().stream()
                            .collect(Collectors.groupingBy(UserOnlineInfo::getServerLocation));
            // Submit push tasks to batch executor
            for (Map.Entry<String, List<UserOnlineInfo>> entry : usersByServer.entrySet()) {
                String serverAddress = entry.getKey();
                List<UserOnlineInfo> userInfos = entry.getValue();

                if (userInfos.isEmpty()) {
                    continue;
                }
                GatewayPushTask task = new GatewayPushTask(serverAddress, userInfos, value);
                try {
                    batchExecutor.push(task);
                } catch (InterruptedException e) {
                    logger.error(
                            "processMessagePayload: Interrupted while pushing task for server: {}",
                            serverAddress,
                            e);
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
        } catch (Exception e) {
            logger.error(
                    "processMessagePayload: Error processing message payload with key: {}", key, e);
        }
    }

    public void close() throws InterruptedException {
        logger.info("Shutting down PushService...");
        batchExecutor.shutdown();
        batchExecutor.awaitTermination();
        logger.info("close: PushService shutdown complete");
    }

    private List<String> getReceiveUserIds(PushMessage pushMessage) {

        switch (pushMessage.getPayloadCase()) {
            case EVENT_DATA:
                return pushMessage.getEventData().getUserNotificationsMap().keySet().stream()
                        .map(String::valueOf)
                        .collect(Collectors.toList());
            case MESSAGE_DATA:
                return pushMessage.getMessageData().getReceiveUserIdsList().stream()
                        .map(id -> Long.toString(id))
                        .collect(Collectors.toList());
            default:
                logger.error(
                        "getReceiveUserIds: Unknown payload type: {}",
                        pushMessage.getPayloadCase());
                throw new IllegalArgumentException(
                        "Unknown payload type: " + pushMessage.getPayloadCase());
        }
    }
}
