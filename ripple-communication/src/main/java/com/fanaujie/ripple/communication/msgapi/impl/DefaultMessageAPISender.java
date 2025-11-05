package com.fanaujie.ripple.communication.msgapi.impl;

import com.fanaujie.ripple.communication.grpc.client.GrpcClient;
import com.fanaujie.ripple.communication.msgapi.MessageAPISender;
import com.fanaujie.ripple.protobuf.msgapiserver.*;

import java.util.concurrent.ExecutorService;

public class DefaultMessageAPISender implements MessageAPISender {

    private final ExecutorService executorService;
    private final GrpcClient<MessageAPIGrpc.MessageAPIBlockingStub> msgAPIClient;

    public DefaultMessageAPISender(
            ExecutorService executorService,
            GrpcClient<MessageAPIGrpc.MessageAPIBlockingStub> msgAPIClientPool) {
        this.executorService = executorService;
        this.msgAPIClient = msgAPIClientPool;
    }

    @Override
    public void sendEvent(SendEventReq req) throws Exception {
        this.executorService.submit(() -> this.msgAPIClient.getStub().sendEvent(req)).get();
    }
}
