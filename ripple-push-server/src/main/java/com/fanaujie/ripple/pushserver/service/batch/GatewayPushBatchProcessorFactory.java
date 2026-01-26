package com.fanaujie.ripple.pushserver.service.batch;

import com.fanaujie.ripple.communication.batch.BatchProcessorFactory;
import com.fanaujie.ripple.communication.gateway.GatewayConnectionManager;
import com.fanaujie.ripple.cache.service.ConversationSummaryStorage;

public class GatewayPushBatchProcessorFactory implements BatchProcessorFactory<GatewayPushTask> {

    private final GatewayConnectionManager gatewayConnectionManager;
    private final ConversationSummaryStorage conversationStorage;

    public GatewayPushBatchProcessorFactory(
            GatewayConnectionManager gatewayConnectionManager,
            ConversationSummaryStorage conversationStorage) {
        this.gatewayConnectionManager = gatewayConnectionManager;
        this.conversationStorage = conversationStorage;
    }

    @Override
    public BatchProcessor<GatewayPushTask> create() {
        return new GatewayPushBatchProcessor(gatewayConnectionManager, conversationStorage);
    }
}
