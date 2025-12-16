package com.fanaujie.ripple.pushserver.service.batch;

import com.fanaujie.ripple.communication.batch.BatchProcessorFactory;
import com.fanaujie.ripple.storage.service.ConversationStateFacade;
import com.fanaujie.ripple.pushserver.service.grpc.MessageGatewayClientManager;

public class GatewayPushBatchProcessorFactory implements BatchProcessorFactory<GatewayPushTask> {

    private final MessageGatewayClientManager messageGatewayManager;
    private final ConversationStateFacade conversationStorage;

    public GatewayPushBatchProcessorFactory(
            MessageGatewayClientManager messageGatewayManager,
            ConversationStateFacade conversationStorage) {
        this.messageGatewayManager = messageGatewayManager;
        this.conversationStorage = conversationStorage;
    }

    @Override
    public BatchProcessor<GatewayPushTask> create() {
        return new GatewayPushBatchProcessor(messageGatewayManager, conversationStorage);
    }
}
