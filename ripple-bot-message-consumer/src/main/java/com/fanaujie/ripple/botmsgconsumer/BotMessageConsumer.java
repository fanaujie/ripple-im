package com.fanaujie.ripple.botmsgconsumer;

import com.fanaujie.ripple.botmsgconsumer.client.WebhookServiceClientManager;
import com.fanaujie.ripple.communication.grpc.client.GrpcClient;
import com.fanaujie.ripple.communication.msgqueue.MessageRecord;
import com.fanaujie.ripple.protobuf.msgdispatcher.BotMessageData;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;
import com.fanaujie.ripple.protobuf.webhookservice.DispatchResponse;
import com.fanaujie.ripple.protobuf.webhookservice.WebhookDispatcherGrpc;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class BotMessageConsumer {
    private static final Logger logger = LoggerFactory.getLogger(BotMessageConsumer.class);

    private final WebhookServiceClientManager clientManager;

    public BotMessageConsumer(WebhookServiceClientManager clientManager) {
        this.clientManager = clientManager;
    }

    public void consumeBatch(List<MessageRecord<String, MessagePayload>> records) throws Exception {
        for (MessageRecord<String, MessagePayload> record : records) {
            consume(record.key(), record.value());
        }
    }

    public void consume(String key, MessagePayload payload) {
        if (!payload.hasBotMessageData()) {
            logger.warn("Received non-bot message payload on bot webhook topic");
            return;
        }

        BotMessageData botMessage = payload.getBotMessageData();
        logger.info(
                "Processing bot message: messageId={}, botId={}, senderId={}",
                botMessage.getMessageId(),
                botMessage.getBotUserId(),
                botMessage.getSenderUserId());

        // Get the webhook-service client (gRPC round-robin is handled at channel level)
        GrpcClient<WebhookDispatcherGrpc.WebhookDispatcherStub> grpcClient =
                clientManager.getClient();
        WebhookDispatcherGrpc.WebhookDispatcherStub stub = grpcClient.getStub();

        // Fire-and-forget gRPC call using async stub
        stub.dispatchBotMessage(
                botMessage,
                new StreamObserver<DispatchResponse>() {
                    @Override
                    public void onNext(DispatchResponse response) {
                        if (response.getAccepted()) {
                            logger.debug(
                                    "Bot message {} accepted by webhook-service",
                                    botMessage.getMessageId());
                        } else {
                            logger.error(
                                    "Bot message {} rejected by webhook-service: {}",
                                    botMessage.getMessageId(),
                                    response.getErrorMessage());
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        logger.error(
                                "gRPC call failed for bot message {}: {}",
                                botMessage.getMessageId(),
                                t.getMessage(),
                                t);
                    }

                    @Override
                    public void onCompleted() {
                        logger.debug(
                                "gRPC call completed for bot message {}",
                                botMessage.getMessageId());
                    }
                });
    }
}
