package com.fanaujie.ripple.msggateway.server.grpc;

import com.fanaujie.ripple.protobuf.msggateway.*;
import com.fanaujie.ripple.protobuf.msggateway.OnlineBatchPushChatMessageRsp;
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
    public void onlineBatchPushChatMsg(
            OnlineBatchPushChatMessageReq request,
            StreamObserver<OnlineBatchPushChatMessageRsp> responseObserver) {
        try {
            logger.info(
                    "Received batch push request for message: {}, targeting {} users",
                    request.getChatMessage().getMessageId(),
                    request.getPushToUserIdsList().size());

            OnlineBatchPushChatMessageRsp.Builder responseBuilder =
                    OnlineBatchPushChatMessageRsp.newBuilder();

            for (long userId : request.getPushToUserIdsList()) {
                SinglePushChatMessageResp.Builder singleResponse =
                        SinglePushChatMessageResp.newBuilder().setPushToUserId(userId);

                PushChatDeviceResult.Builder deviceResult =
                        PushChatDeviceResult.newBuilder().setDeviceId("default").setResultCode(0);

                singleResponse.addDeviceResults(deviceResult.build());
                responseBuilder.addResults(singleResponse.build());

                logger.debug("Processed push for user: {}", userId);
            }

            OnlineBatchPushChatMessageRsp response = responseBuilder.build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();

            logger.info(
                    "Successfully completed batch push for {} users",
                    request.getPushToUserIdsList().size());

        } catch (Exception e) {
            logger.error("Error processing batch push request", e);
            responseObserver.onError(e);
        }
    }
}
