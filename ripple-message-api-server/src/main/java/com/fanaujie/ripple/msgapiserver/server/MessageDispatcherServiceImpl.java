package com.fanaujie.ripple.msgapiserver.server;

import com.fanaujie.ripple.communication.processor.ProcessorDispatcher;
import com.fanaujie.ripple.msgapiserver.exception.NotFoundAnyFriendIdsException;
import com.fanaujie.ripple.msgapiserver.exception.NotFoundListenerException;
import com.fanaujie.ripple.protobuf.msgapiserver.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.RejectedExecutionException;

public class MessageDispatcherServiceImpl extends MessageAPIGrpc.MessageAPIImplBase {

    private static final Logger logger =
            LoggerFactory.getLogger(MessageDispatcherServiceImpl.class);

    private final ProcessorDispatcher<SendEventReq.EventCase, SendEventReq, SendEventResp>
            messageDispatcher;
    private final ProcessorDispatcher<SendEventReq.EventCase, SendEventReq, SendEventResp>
            eventDispatcher;

    public MessageDispatcherServiceImpl(
            ProcessorDispatcher<SendEventReq.EventCase, SendEventReq, SendEventResp>
                    messageDispatcher,
            ProcessorDispatcher<SendEventReq.EventCase, SendEventReq, SendEventResp>
                    eventDispatcher) {
        this.messageDispatcher = messageDispatcher;
        this.eventDispatcher = eventDispatcher;
    }

    @Override
    public void sendMessage(
            SendMessageReq request, StreamObserver<SendMessageResp> responseObserver) {}

    @Override
    public void sendEvent(SendEventReq request, StreamObserver<SendEventResp> responseObserver) {
        try {
            SendEventResp response = this.eventDispatcher.dispatch(request);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (NotFoundAnyFriendIdsException e) {
            responseObserver.onError(
                    Status.NOT_FOUND.withDescription(e.getMessage()).asException());
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
