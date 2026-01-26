package com.fanaujie.ripple.communication.gateway;

import com.fanaujie.ripple.communication.grpc.client.GrpcClient;
import com.fanaujie.ripple.protobuf.msggateway.BatchPushMessageRequest;
import com.fanaujie.ripple.protobuf.msggateway.BatchPushMessageResponse;
import com.fanaujie.ripple.protobuf.msggateway.MessageGatewayGrpc;
import com.fanaujie.ripple.protobuf.msggateway.PushMessageRequest;
import com.fanaujie.ripple.protobuf.push.SSEEventType;
import com.fanaujie.ripple.protobuf.userpresence.QueryUserOnlineReq;
import com.fanaujie.ripple.protobuf.userpresence.QueryUserOnlineResp;
import com.fanaujie.ripple.protobuf.userpresence.UserOnlineInfo;
import com.fanaujie.ripple.protobuf.userpresence.UserPresenceGrpc;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Synchronous gateway pusher for latency-sensitive operations.
 *
 * <p>This implementation performs user presence lookup synchronously and
 * uses async gRPC calls for the actual push. It's suitable for operations
 * where low latency is important, such as bot streaming responses or
 * typing indicators.
 */
public class DirectGatewayPusher implements GatewayPusher {
    private static final Logger logger = LoggerFactory.getLogger(DirectGatewayPusher.class);

    private final GatewayConnectionManager connectionManager;
    private final GrpcClient<UserPresenceGrpc.UserPresenceBlockingStub> userPresenceClient;

    public DirectGatewayPusher(
            GatewayConnectionManager connectionManager,
            GrpcClient<UserPresenceGrpc.UserPresenceBlockingStub> userPresenceClient) {
        this.connectionManager = connectionManager;
        this.userPresenceClient = userPresenceClient;
    }

    @Override
    public CompletableFuture<Void> push(
            String serverAddress,
            List<UserOnlineInfo> userInfos,
            PushMessageRequest request) {

        Optional<GrpcClient<MessageGatewayGrpc.MessageGatewayStub>> clientOpt =
                connectionManager.getClient(serverAddress);

        if (clientOpt.isEmpty()) {
            logger.warn("No gateway client for server {}, discarding push request", serverAddress);
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<Void> future = new CompletableFuture<>();

        // Build batch request
        BatchPushMessageRequest.Builder batchBuilder = BatchPushMessageRequest.newBuilder();
        for (UserOnlineInfo userInfo : userInfos) {
            PushMessageRequest userRequest = request.toBuilder()
                    .setReceiveUserId(userInfo.getUserId())
                    .setReceiveDeviceId(userInfo.getDeviceId())
                    .build();
            batchBuilder.addRequests(userRequest);
        }

        // Send async with completion tracking
        clientOpt.get().getStub().pushMessageToUser(
                batchBuilder.build(),
                new StreamObserver<BatchPushMessageResponse>() {
                    @Override
                    public void onNext(BatchPushMessageResponse response) {
                        // Success
                    }

                    @Override
                    public void onError(Throwable t) {
                        logger.warn("Failed to push to gateway {}: {}",
                                serverAddress, t.getMessage());
                        future.completeExceptionally(t);
                    }

                    @Override
                    public void onCompleted() {
                        future.complete(null);
                    }
                });

        return future;
    }

    @Override
    public CompletableFuture<Void> pushSSE(
            long receiveUserId,
            long sendUserId,
            String conversationId,
            SSEEventType eventType,
            String content,
            long messageId,
            long sendTimestamp) {

        try {
            // Query user-presence for user's gateway location
            QueryUserOnlineReq req = QueryUserOnlineReq.newBuilder()
                    .addUserIds(String.valueOf(receiveUserId))
                    .build();
            QueryUserOnlineResp resp = userPresenceClient.getStub().queryUserOnline(req);

            if (resp.getUserOnlineInfosList().isEmpty()) {
                // User is offline, silently discard
                logger.debug("User {} is offline, discarding SSE event", receiveUserId);
                return CompletableFuture.completedFuture(null);
            }

            // Group by gateway server
            Map<String, List<UserOnlineInfo>> usersByServer = resp.getUserOnlineInfosList()
                    .stream()
                    .collect(Collectors.groupingBy(UserOnlineInfo::getServerLocation));

            // Send to each gateway
            List<CompletableFuture<Void>> futures = usersByServer.entrySet().stream()
                    .map(entry -> pushSSEToServer(
                            entry.getKey(),
                            entry.getValue(),
                            sendUserId,
                            conversationId,
                            eventType,
                            content,
                            messageId,
                            sendTimestamp))
                    .toList();

            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

        } catch (Exception e) {
            logger.error("Error pushing SSE event to user {}: {}", receiveUserId, e.getMessage(), e);
            // Return completed future - don't propagate error for push failures
            return CompletableFuture.completedFuture(null);
        }
    }

    private CompletableFuture<Void> pushSSEToServer(
            String serverAddress,
            List<UserOnlineInfo> userInfos,
            long sendUserId,
            String conversationId,
            SSEEventType eventType,
            String content,
            long messageId,
            long sendTimestamp) {

        Optional<GrpcClient<MessageGatewayGrpc.MessageGatewayStub>> clientOpt =
                connectionManager.getClient(serverAddress);

        if (clientOpt.isEmpty()) {
            logger.warn("No gateway client for server {}, discarding SSE event", serverAddress);
            return CompletableFuture.completedFuture(null);
        }

        // Build batch request using GatewayRequestBuilder
        BatchPushMessageRequest batchRequest = GatewayRequestBuilder.buildSSEBatchRequest(
                userInfos,
                sendUserId,
                conversationId,
                eventType,
                content,
                messageId,
                sendTimestamp);

        CompletableFuture<Void> future = new CompletableFuture<>();

        // Send async
        clientOpt.get().getStub().pushMessageToUser(
                batchRequest,
                new StreamObserver<BatchPushMessageResponse>() {
                    @Override
                    public void onNext(BatchPushMessageResponse response) {
                        // Success
                    }

                    @Override
                    public void onError(Throwable t) {
                        logger.warn("Failed to push SSE to gateway {}: {}",
                                serverAddress, t.getMessage());
                        // Complete normally to not fail the whole operation
                        future.complete(null);
                    }

                    @Override
                    public void onCompleted() {
                        future.complete(null);
                    }
                });

        return future;
    }
}
