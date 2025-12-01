package com.fanaujie.ripple.msggateway.batch;

import com.fanaujie.ripple.communication.batch.BatchProcessorFactory;
import com.fanaujie.ripple.communication.grpc.client.GrpcClient;
import com.fanaujie.ripple.protobuf.userpresence.BatchUserOnlineReq;
import com.fanaujie.ripple.protobuf.userpresence.BatchUserOnlineResp;
import com.fanaujie.ripple.protobuf.userpresence.UserOnlineReq;
import com.fanaujie.ripple.protobuf.userpresence.UserPresenceGrpc;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class UserOnlineBatchProcessor
        implements BatchProcessorFactory.BatchProcessor<UserOnlineBatchTask> {

    private static final Logger logger = LoggerFactory.getLogger(UserOnlineBatchProcessor.class);

    private final GrpcClient<UserPresenceGrpc.UserPresenceStub> userPresenceGrpcClient;

    public UserOnlineBatchProcessor(
            GrpcClient<UserPresenceGrpc.UserPresenceStub> userPresenceGrpcClient) {
        this.userPresenceGrpcClient = userPresenceGrpcClient;
    }

    @Override
    public void process(List<UserOnlineBatchTask> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        // Convert batch tasks to UserOnlineReq list
        BatchUserOnlineReq.Builder requestBuilder = BatchUserOnlineReq.newBuilder();

        for (UserOnlineBatchTask task : batch) {
            UserOnlineReq req =
                    UserOnlineReq.newBuilder()
                            .setUserId(task.userId())
                            .setDeviceId(task.deviceId())
                            .setIsOnline(task.isOnline())
                            .setServerLocation(task.serverLocation())
                            .build();
            requestBuilder.addRequests(req);
        }

        BatchUserOnlineReq batchRequest = requestBuilder.build();
        final int batchSize = batch.size();
        // Call the batch gRPC method asynchronously (fire-and-forget)
        userPresenceGrpcClient
                .getStub()
                .setUserOnlineBatch(
                        batchRequest,
                        new StreamObserver<BatchUserOnlineResp>() {
                            @Override
                            public void onNext(BatchUserOnlineResp response) {}

                            @Override
                            public void onError(Throwable throwable) {
                                logger.error(
                                        "process: Error in async batch processing of {} tasks - {}",
                                        batchSize,
                                        throwable.getMessage(),
                                        throwable);
                            }

                            @Override
                            public void onCompleted() {}
                        });
    }
}
