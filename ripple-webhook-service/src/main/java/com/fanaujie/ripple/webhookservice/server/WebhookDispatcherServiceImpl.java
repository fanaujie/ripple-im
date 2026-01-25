package com.fanaujie.ripple.webhookservice.server;

import com.fanaujie.ripple.protobuf.msgdispatcher.BotMessageData;
import com.fanaujie.ripple.protobuf.webhookservice.DispatchResponse;
import com.fanaujie.ripple.protobuf.webhookservice.WebhookDispatcherGrpc;
import com.fanaujie.ripple.webhookservice.service.WebhookDispatcherService;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebhookDispatcherServiceImpl extends WebhookDispatcherGrpc.WebhookDispatcherImplBase {
    private static final Logger logger =
            LoggerFactory.getLogger(WebhookDispatcherServiceImpl.class);

    private final WebhookDispatcherService dispatcherService;

    public WebhookDispatcherServiceImpl(WebhookDispatcherService dispatcherService) {
        this.dispatcherService = dispatcherService;
    }

    @Override
    public void dispatchBotMessage(
            BotMessageData request, StreamObserver<DispatchResponse> responseObserver) {
        logger.info(
                "Received bot message dispatch request: messageId={}, botId={}, senderId={}",
                request.getMessageId(),
                request.getBotUserId(),
                request.getSenderUserId());

        try {
            // Fire-and-forget: dispatch asynchronously
            // The dispatcherService handles HTTP/SSE, push, and storage internally
            dispatcherService.dispatch(request);

            // Return success immediately
            DispatchResponse response = DispatchResponse.newBuilder().setAccepted(true).build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            logger.error(
                    "Failed to accept bot message dispatch request: messageId={}, error={}",
                    request.getMessageId(),
                    e.getMessage(),
                    e);

            DispatchResponse response =
                    DispatchResponse.newBuilder()
                            .setAccepted(false)
                            .setErrorMessage(e.getMessage())
                            .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
}
