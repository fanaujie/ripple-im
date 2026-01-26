package com.fanaujie.ripple.communication.gateway;

import com.fanaujie.ripple.communication.grpc.client.GrpcClient;
import com.fanaujie.ripple.protobuf.msggateway.BatchPushMessageRequest;
import com.fanaujie.ripple.protobuf.msggateway.BatchPushMessageResponse;
import com.fanaujie.ripple.protobuf.msggateway.MessageGatewayGrpc;
import com.fanaujie.ripple.protobuf.msggateway.PushMessageRequest;
import com.fanaujie.ripple.protobuf.push.SSEEventType;
import com.fanaujie.ripple.protobuf.userpresence.UserOnlineInfo;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Fire-and-forget gateway pusher for batch operations.
 *
 * <p>This implementation uses async gRPC calls with no-op response handling,
 * making it suitable for high-throughput batch operations where individual
 * message acknowledgment is not required.
 *
 * <p>Note: The {@link #pushSSE} method is not supported in this implementation
 * as it requires user presence lookup. Use {@link DirectGatewayPusher} for SSE operations.
 */
public class AsyncGatewayPusher implements GatewayPusher {
    private static final Logger logger = LoggerFactory.getLogger(AsyncGatewayPusher.class);

    private final GatewayConnectionManager connectionManager;

    public AsyncGatewayPusher(GatewayConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
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

        // Build batch request
        BatchPushMessageRequest.Builder batchBuilder = BatchPushMessageRequest.newBuilder();
        for (UserOnlineInfo userInfo : userInfos) {
            // Clone the request with the correct user info
            PushMessageRequest userRequest = request.toBuilder()
                    .setReceiveUserId(userInfo.getUserId())
                    .setReceiveDeviceId(userInfo.getDeviceId())
                    .build();
            batchBuilder.addRequests(userRequest);
        }

        // Send async (fire and forget)
        clientOpt.get().getStub().pushMessageToUser(
                batchBuilder.build(),
                new StreamObserver<BatchPushMessageResponse>() {
                    @Override
                    public void onNext(BatchPushMessageResponse response) {
                        // Success, nothing to do
                    }

                    @Override
                    public void onError(Throwable t) {
                        logger.warn("Failed to push to gateway {}: {}",
                                serverAddress, t.getMessage());
                    }

                    @Override
                    public void onCompleted() {
                        // Nothing to do
                    }
                });

        // Return immediately completed future (fire-and-forget)
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Not supported in AsyncGatewayPusher.
     * Use {@link DirectGatewayPusher} for SSE operations that require user presence lookup.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public CompletableFuture<Void> pushSSE(
            long receiveUserId,
            long sendUserId,
            String conversationId,
            SSEEventType eventType,
            String content,
            long messageId,
            long sendTimestamp) {
        throw new UnsupportedOperationException(
                "pushSSE is not supported in AsyncGatewayPusher. Use DirectGatewayPusher instead.");
    }

    /**
     * Push a pre-built batch request to a specific gateway.
     * This is a convenience method for batch processing scenarios.
     *
     * @param serverAddress the gateway server address
     * @param batchRequest  the pre-built batch request
     * @return CompletableFuture that completes immediately (fire-and-forget)
     */
    public CompletableFuture<Void> pushBatch(
            String serverAddress,
            BatchPushMessageRequest batchRequest) {

        Optional<GrpcClient<MessageGatewayGrpc.MessageGatewayStub>> clientOpt =
                connectionManager.getClient(serverAddress);

        if (clientOpt.isEmpty()) {
            logger.warn("No gateway client for server {}, discarding batch request", serverAddress);
            return CompletableFuture.completedFuture(null);
        }

        clientOpt.get().getStub().pushMessageToUser(
                batchRequest,
                new StreamObserver<BatchPushMessageResponse>() {
                    @Override
                    public void onNext(BatchPushMessageResponse response) {
                        // Success, nothing to do
                    }

                    @Override
                    public void onError(Throwable t) {
                        logger.warn("Failed to push batch to gateway {}: {}",
                                serverAddress, t.getMessage());
                    }

                    @Override
                    public void onCompleted() {
                        // Nothing to do
                    }
                });

        return CompletableFuture.completedFuture(null);
    }
}
