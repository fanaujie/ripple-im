package com.fanaujie.ripple.pushserver.service.batch;

import com.fanaujie.ripple.communication.batch.BatchProcessorFactory;
import com.fanaujie.ripple.pushserver.service.grpc.MessageGatewayClientPoolManager;

public class GatewayPushBatchProcessorFactory implements BatchProcessorFactory<GatewayPushTask> {

    private final MessageGatewayClientPoolManager messageGatewayManager;

    public GatewayPushBatchProcessorFactory(MessageGatewayClientPoolManager messageGatewayManager) {
        this.messageGatewayManager = messageGatewayManager;
    }

    @Override
    public BatchProcessor<GatewayPushTask> create() {
        return new GatewayPushBatchProcessor(messageGatewayManager);
    }
}
