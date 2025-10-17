package com.fanaujie.ripple.msggateway.server.grpc;

import com.fanaujie.ripple.protobuf.msggateway.*;
import com.fanaujie.ripple.msggateway.server.users.OnlineUser;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageGatewayServiceImpl extends MessageGatewayGrpc.MessageGatewayImplBase {

    private static final Logger logger = LoggerFactory.getLogger(MessageGatewayServiceImpl.class);

    private final OnlineUser onlineUser;

    public MessageGatewayServiceImpl(OnlineUser onlineUser) {
        this.onlineUser = onlineUser;
    }

    @Override
    public StreamObserver<PushMessageRequest> pushMessageToUser(
            final StreamObserver<PushMessageResponse> responseObserver) {
        return new StreamObserver<PushMessageRequest>() {
            @Override
            public void onNext(PushMessageRequest request) {
                onlineUser
                        .get(request.getReceiveUserId(), request.getRequestDeviceId())
                        .ifPresentOrElse(
                                userSession -> {

                                    // Respond with success
                                    PushMessageResponse response =
                                            PushMessageResponse.newBuilder()
                                                    .setSendUserId(request.getSendUserId())
                                                    .setReceiveUserId(request.getReceiveUserId())
                                                    .setIsSuccess(true)
                                                    .build();
                                    responseObserver.onNext(response);
                                },
                                () -> {
                                    logger.warn(
                                            "User {} is offline. Cannot push message.",
                                            request.getReceiveUserId());
                                    PushMessageResponse response =
                                            PushMessageResponse.newBuilder()
                                                    .setSendUserId(request.getSendUserId())
                                                    .setReceiveUserId(request.getReceiveUserId())
                                                    .setIsSuccess(false)
                                                    .setErrorMsg("User is offline")
                                                    .build();
                                    responseObserver.onNext(response);
                                });
            }

            @Override
            public void onError(Throwable t) {
                logger.error("Error in pushMessageToUser stream", t);
                responseObserver.onCompleted();
            }

            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }
}
