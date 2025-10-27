package com.fanaujie.ripple.msggateway.batch;

import com.fanaujie.ripple.communication.batch.BatchProcessorFactory;
import com.fanaujie.ripple.communication.grpc.client.GrpcClient;
import com.fanaujie.ripple.protobuf.userpresence.UserPresenceGrpc;

public class UserOnlineBatchProcessorFactory
        implements BatchProcessorFactory<UserOnlineBatchTask> {

    private final GrpcClient<UserPresenceGrpc.UserPresenceStub> userPresenceGrpcClient;

    public UserOnlineBatchProcessorFactory(
            GrpcClient<UserPresenceGrpc.UserPresenceStub> userPresenceGrpcClient) {
        this.userPresenceGrpcClient = userPresenceGrpcClient;
    }

    @Override
    public BatchProcessor<UserOnlineBatchTask> create() {
        return new UserOnlineBatchProcessor(userPresenceGrpcClient);
    }
}
