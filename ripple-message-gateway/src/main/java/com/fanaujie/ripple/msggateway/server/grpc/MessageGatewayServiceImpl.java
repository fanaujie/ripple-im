package com.fanaujie.ripple.msggateway.server.grpc;

import com.fanaujie.ripple.msggateway.server.users.UserNotifier;
import com.fanaujie.ripple.protobuf.msggateway.*;
import com.fanaujie.ripple.msggateway.server.users.OnlineUser;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageGatewayServiceImpl extends MessageGatewayGrpc.MessageGatewayImplBase {

    private static final Logger logger = LoggerFactory.getLogger(MessageGatewayServiceImpl.class);

    private final OnlineUser onlineUser;
    private final UserNotifier pushToUser;

    public MessageGatewayServiceImpl(OnlineUser onlineUser, UserNotifier pushToUser) {
        this.onlineUser = onlineUser;
        this.pushToUser = pushToUser;
    }

    @Override
    public StreamObserver<PushMessageRequest> pushMessageToUser(
            final StreamObserver<PushMessageResponse> responseObserver) {
        logger.debug("pushMessageToUser: Starting push message stream");
        return new StreamObserver<PushMessageRequest>() {
            @Override
            public void onNext(PushMessageRequest request) {
                logger.debug(
                        "pushMessageToUser.onNext: Received push request - sendUserId: {}, receiveUserId: {}, deviceId: {}",
                        request.getSendUserId(),
                        request.getReceiveUserId(),
                        request.getRequestDeviceId());

                onlineUser
                        .get(request.getReceiveUserId(), request.getRequestDeviceId())
                        .ifPresentOrElse(
                                userSession -> {
                                    logger.debug(
                                            "pushMessageToUser.onNext: User {} on device {} is online, pushing message",
                                            request.getReceiveUserId(),
                                            request.getRequestDeviceId());
                                    pushToUser.push(userSession, request);
                                    // Respond with success
                                    PushMessageResponse response =
                                            PushMessageResponse.newBuilder()
                                                    .setSendUserId(request.getSendUserId())
                                                    .setReceiveUserId(request.getReceiveUserId())
                                                    .setIsSuccess(true)
                                                    .build();
                                    logger.debug(
                                            "pushMessageToUser.onNext: Sending success response for user {}",
                                            request.getReceiveUserId());
                                    responseObserver.onNext(response);
                                },
                                () -> {
                                    logger.warn(
                                            "pushMessageToUser.onNext: User {} on device {} is offline. Cannot push message.",
                                            request.getReceiveUserId(),
                                            request.getRequestDeviceId());
                                    PushMessageResponse response =
                                            PushMessageResponse.newBuilder()
                                                    .setSendUserId(request.getSendUserId())
                                                    .setReceiveUserId(request.getReceiveUserId())
                                                    .setIsSuccess(false)
                                                    .setErrorMsg("User is offline")
                                                    .build();
                                    logger.debug(
                                            "pushMessageToUser.onNext: Sending failure response for offline user {}",
                                            request.getReceiveUserId());
                                    responseObserver.onNext(response);
                                });
            }

            @Override
            public void onError(Throwable t) {
                logger.error(
                        "pushMessageToUser.onError: Error occurred in pushMessageToUser stream - {}",
                        t.getMessage(),
                        t);
                responseObserver.onCompleted();
            }

            @Override
            public void onCompleted() {
                logger.debug("pushMessageToUser.onCompleted: Push message stream completed");
                responseObserver.onCompleted();
            }
        };
    }
}
