package com.fanaujie.ripple.msgdispatcher.server;

import com.fanaujie.ripple.protobuf.msgdispatcher.DispatchMessageReq;
import com.fanaujie.ripple.protobuf.msgdispatcher.DispatchMessageResp;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessageDispatcherGrpc;
import com.fanaujie.ripple.msgdispatcher.processor.MessageProcessor;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageDispatcherServiceImpl extends MessageDispatcherGrpc.MessageDispatcherImplBase {

    private static final Logger logger = LoggerFactory.getLogger(MessageDispatcherServiceImpl.class);

    private final MessageProcessor messageProcessor;

    public MessageDispatcherServiceImpl(MessageProcessor messageProcessor) {
        this.messageProcessor = messageProcessor;
    }

    @Override
    public void dispatchMessage(
            DispatchMessageReq request, StreamObserver<DispatchMessageResp> responseObserver) {
        try {
            logger.info(
                    "Received dispatchMessage request for user {} with message type {}",
                    request.getUserId(),
                    request.getMessageType());

            String dispatchId = null;
            if (messageProcessor != null) {
                dispatchId = messageProcessor.processMessage(request);
            }

            DispatchMessageResp response = DispatchMessageResp.newBuilder()
                    .setSuccess(true)
                    .setMessage("Message dispatched successfully")
                    .setDispatchId(dispatchId != null ? dispatchId : "")
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            logger.info(
                    "Successfully processed dispatchMessage request for user {} with dispatch ID: {}",
                    request.getUserId(), dispatchId);

        } catch (Exception e) {
            logger.error(
                    "Error processing dispatchMessage request for user {}", request.getUserId(), e);

            DispatchMessageResp errorResponse = DispatchMessageResp.newBuilder()
                    .setSuccess(false)
                    .setMessage("Error processing message: " + e.getMessage())
                    .setDispatchId("")
                    .build();

            responseObserver.onNext(errorResponse);
            responseObserver.onCompleted();
        }
    }
}