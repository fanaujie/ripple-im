package com.fanaujie.ripple.msgapiserver.server;

import com.fanaujie.ripple.msgapiserver.exception.NotFoundAnyFriendIdsException;
import com.fanaujie.ripple.msgapiserver.exception.NotFoundEvnetListenerException;
import com.fanaujie.ripple.msgapiserver.processor.EventProcessor;
import com.fanaujie.ripple.msgapiserver.processor.MessageProcessor;
import com.fanaujie.ripple.protobuf.msgapiserver.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.RejectedExecutionException;

public class MessageDispatcherServiceImpl extends MessageAPIGrpc.MessageAPIImplBase {

    private static final Logger logger =
            LoggerFactory.getLogger(MessageDispatcherServiceImpl.class);

    private final MessageProcessor messageProcessor;
    private final EventProcessor eventProcessor;

    public MessageDispatcherServiceImpl(
            MessageProcessor messageProcessor, EventProcessor eventProcessor) {
        this.messageProcessor = messageProcessor;
        this.eventProcessor = eventProcessor;
    }

    @Override
    public void sendMessage(
            SendMessageReq request, StreamObserver<SendMessageResp> responseObserver) {}

    @Override
    public void sendEvent(SendEventReq request, StreamObserver<SendEventResp> responseObserver) {
        try {
            SendEventResp response = this.eventProcessor.processEvent(request);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            if (e instanceof NotFoundAnyFriendIdsException) {
                responseObserver.onError(
                        Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
            } else if (e instanceof NotFoundEvnetListenerException) {
                responseObserver.onError(
                        Status.INVALID_ARGUMENT
                                .withDescription(e.getMessage())
                                .asRuntimeException());

            } else if (e instanceof RejectedExecutionException) {
                responseObserver.onError(
                        Status.RESOURCE_EXHAUSTED
                                .withDescription("Server is overloaded, try again later")
                                .asRuntimeException());
            } else {
                logger.error("Error processing event: ", e);
                responseObserver.onError(
                        Status.INTERNAL
                                .withDescription("Internal server error")
                                .asRuntimeException());
            }
        }
    }
}
