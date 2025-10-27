package com.fanaujie.ripple.pushserver.service.batch;

import com.fanaujie.ripple.communication.batch.BatchProcessorFactory;
import com.fanaujie.ripple.pushserver.service.grpc.MessageGatewayClientManager;

public class GatewayPushBatchProcessorFactory implements BatchProcessorFactory<GatewayPushTask> {

    private final MessageGatewayClientManager messageGatewayManager;

    public GatewayPushBatchProcessorFactory(MessageGatewayClientManager messageGatewayManager) {
        this.messageGatewayManager = messageGatewayManager;
    }

    @Override
    public BatchProcessor<GatewayPushTask> create() {
        return new GatewayPushBatchProcessor(messageGatewayManager);
    }
}
