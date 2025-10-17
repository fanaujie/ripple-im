package com.fanaujie.ripple.userpresence.server;

import com.fanaujie.ripple.protobuf.userpresence.*;
import com.fanaujie.ripple.storage.service.UserPresenceStorage;
import io.grpc.stub.StreamObserver;

public class UserPresenceServiceImpl extends UserPresenceGrpc.UserPresenceImplBase {

    private final UserPresenceStorage userPresenceStorage;

    public UserPresenceServiceImpl(UserPresenceStorage userPresenceStorage) {
        this.userPresenceStorage = userPresenceStorage;
    }

    @Override
    public void setUserOnline(
            UserOnlineReq request, StreamObserver<UserOnlineResp> responseObserver) {
        userPresenceStorage.setUserOnline(request);
        UserOnlineResp response = UserOnlineResp.newBuilder().build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void queryUserOnline(
            QueryUserOnlineReq request, StreamObserver<QueryUserOnlineResp> responseObserver) {
        QueryUserOnlineResp response = this.userPresenceStorage.getUserOnline(request);
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
