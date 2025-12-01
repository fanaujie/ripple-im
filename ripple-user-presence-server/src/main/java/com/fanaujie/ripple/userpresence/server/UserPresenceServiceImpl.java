package com.fanaujie.ripple.userpresence.server;

import com.fanaujie.ripple.protobuf.userpresence.*;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import com.fanaujie.ripple.storage.service.UserPresenceStorage;
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
            this.userPresenceStorage.setUserOnline(request);
            UserOnlineResp response = UserOnlineResp.newBuilder().build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            logger.error(
                    "setUserOnline: Error setting user online for userId: {} - {}",
                    request.getUserId(),
                    e.getMessage(),
                    e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void setUserOnlineBatch(
            BatchUserOnlineReq request, StreamObserver<BatchUserOnlineResp> responseObserver) {
        try {
            this.userPresenceStorage.setUserOnlineBatch(request.getRequestsList());
            BatchUserOnlineResp response = BatchUserOnlineResp.newBuilder().build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            logger.error(
                    "setUserOnlineBatch: Error processing batch request - {}", e.getMessage(), e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void queryUserOnline(
            QueryUserOnlineReq request, StreamObserver<QueryUserOnlineResp> responseObserver) {
        try {
            QueryUserOnlineResp response = this.userPresenceStorage.getUserOnline(request);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            logger.error(
                    "queryUserOnline: Error querying user online status - {}", e.getMessage(), e);
            responseObserver.onError(e);
        }
    }
}
