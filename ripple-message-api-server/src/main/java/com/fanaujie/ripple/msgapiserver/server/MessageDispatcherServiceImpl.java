package com.fanaujie.ripple.msgapiserver.server;

import com.fanaujie.ripple.communication.processor.ProcessorDispatcher;
import com.fanaujie.ripple.communication.exception.NotFoundListenerException;
import com.fanaujie.ripple.protobuf.msgapiserver.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.RejectedExecutionException;

public class MessageDispatcherServiceImpl extends MessageAPIGrpc.MessageAPIImplBase {

    private static final Logger logger =
            LoggerFactory.getLogger(MessageDispatcherServiceImpl.class);

    private final ProcessorDispatcher<SendMessageReq.MessageCase, SendMessageReq, SendMessageResp>
            messageDispatcher;
    private final ProcessorDispatcher<SendEventReq.EventCase, SendEventReq, SendEventResp>
            eventDispatcher;

    public MessageDispatcherServiceImpl(
            ProcessorDispatcher<SendMessageReq.MessageCase, SendMessageReq, SendMessageResp>
                    messageDispatcher,
            ProcessorDispatcher<SendEventReq.EventCase, SendEventReq, SendEventResp>
                    eventDispatcher) {
        this.messageDispatcher = messageDispatcher;
        this.eventDispatcher = eventDispatcher;
    }

    @Override
    public void sendMessage(
            SendMessageReq request, StreamObserver<SendMessageResp> responseObserver) {
        try {
            SendMessageResp response =
                    this.messageDispatcher.dispatch(request.getMessageCase(), request);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (NotFoundListenerException e) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asException());
        } catch (RejectedExecutionException e) {
            responseObserver.onError(
                    Status.RESOURCE_EXHAUSTED
                            .withDescription("Server is overloaded, try again later")
                            .asException());
        } catch (Exception e) {
            logger.error("Error processing message: ", e);
            responseObserver.onError(
                    Status.INTERNAL.withDescription("Internal server error").asRuntimeException());
        }
    }

    @Override
    public void sendEvent(SendEventReq request, StreamObserver<SendEventResp> responseObserver) {
        try {
            SendEventResp response = this.eventDispatcher.dispatch(request.getEventCase(), request);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (NotFoundListenerException e) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asException());
        } catch (RejectedExecutionException e) {
            responseObserver.onError(
                    Status.RESOURCE_EXHAUSTED
                            .withDescription("Server is overloaded, try again later")
                            .asException());
        } catch (Exception e) {
            logger.error("Error processing event: ", e);
            responseObserver.onError(
                    Status.INTERNAL.withDescription("Internal server error").asRuntimeException());
        }
    }
}
