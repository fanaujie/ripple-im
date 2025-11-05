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
    public void pushMessageToUser(
            BatchPushMessageRequest batchRequest,
            StreamObserver<BatchPushMessageResponse> responseObserver) {
        BatchPushMessageResponse.Builder batchResponseBuilder =
                BatchPushMessageResponse.newBuilder();

        for (PushMessageRequest request : batchRequest.getRequestsList()) {
            PushMessageResponse response =
                    onlineUser
                            .get(request.getReceiveUserId(), request.getRequestDeviceId())
                            .map(
                                    userSession -> {
                                        pushToUser.push(userSession, request);
                                        return PushMessageResponse.newBuilder()
                                                .setSendUserId(request.getSendUserId())
                                                .setReceiveUserId(request.getReceiveUserId())
                                                .setIsSuccess(true)
                                                .build();
                                    })
                            .orElseGet(
                                    () -> {
                                        logger.warn(
                                                "pushMessageToUser: User {} on device {} is offline. Cannot push message.",
                                                request.getReceiveUserId(),
                                                request.getRequestDeviceId());
                                        return PushMessageResponse.newBuilder()
                                                .setSendUserId(request.getSendUserId())
                                                .setReceiveUserId(request.getReceiveUserId())
                                                .setIsSuccess(false)
                                                .build();
                                    });

            batchResponseBuilder.addResponses(response);
        }

        BatchPushMessageResponse batchResponse = batchResponseBuilder.build();
        responseObserver.onNext(batchResponse);
        responseObserver.onCompleted();
    }
}
