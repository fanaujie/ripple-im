package com.fanaujie.ripple.userpresence.server;

import com.fanaujie.ripple.protobuf.userpresence.UserOnlineReq;
import com.fanaujie.ripple.protobuf.userpresence.UserOnlineResp;
import com.fanaujie.ripple.protobuf.userpresence.UserPresenceGrpc;
import com.fanaujie.ripple.userpresence.storage.UserPresenceStorage;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserPresenceServiceImpl extends UserPresenceGrpc.UserPresenceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(UserPresenceServiceImpl.class);

    private final UserPresenceStorage userPresenceStorage;

    public UserPresenceServiceImpl(UserPresenceStorage userPresenceStorage) {
        this.userPresenceStorage = userPresenceStorage;
    }

    @Override
    public void setUserOnline(
            UserOnlineReq request, StreamObserver<UserOnlineResp> responseObserver) {
        try {
            logger.info(
                    "Received setUserOnline request for user {} on device {}, online: {}",
                    request.getUserId(),
                    request.getDeviceId(),
                    request.getIsOnline());

            userPresenceStorage.setUserOnline(request);

            UserOnlineResp response = UserOnlineResp.newBuilder().build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();

            logger.info(
                    "Successfully processed setUserOnline request for user {}",
                    request.getUserId());

        } catch (Exception e) {
            logger.error(
                    "Error processing setUserOnline request for user {}", request.getUserId(), e);
            responseObserver.onError(e);
        }
    }
}
