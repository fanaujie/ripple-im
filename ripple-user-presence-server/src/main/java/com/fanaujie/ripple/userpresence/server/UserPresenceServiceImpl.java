package com.fanaujie.ripple.userpresence.server;

import com.fanaujie.ripple.protobuf.userpresence.*;
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
        logger.debug(
                "setUserOnline: Setting user online - userId: {}, deviceId: {}, serverLocation: {}",
                request.getUserId(),
                request.getDeviceId(),
                request.getServerLocation());

        try {
            userPresenceStorage.setUserOnline(request);
            logger.debug(
                    "setUserOnline: User online status saved successfully for userId: {}",
                    request.getUserId());

            UserOnlineResp response = UserOnlineResp.newBuilder().build();
            logger.debug("setUserOnline: Sending response to client");
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            logger.debug("setUserOnline: Response completed for userId: {}", request.getUserId());
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
        logger.debug(
                "setUserOnlineBatch: Processing batch request with {} users",
                request.getRequestsCount());

        try {
            userPresenceStorage.setUserOnlineBatch(request.getRequestsList());
            logger.debug(
                    "setUserOnlineBatch: Batch processing completed successfully for {} users",
                    request.getRequestsCount());
            BatchUserOnlineResp response = BatchUserOnlineResp.newBuilder().build();
            logger.debug("setUserOnlineBatch: Sending response - success");
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            logger.debug("setUserOnlineBatch: Response completed");
        } catch (Exception e) {
            logger.error(
                    "setUserOnlineBatch: Error processing batch request - {}", e.getMessage(), e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void queryUserOnline(
            QueryUserOnlineReq request, StreamObserver<QueryUserOnlineResp> responseObserver) {
        logger.debug(
                "queryUserOnline: Querying online status for {} users",
                request.getUserIdsList().size());
        logger.debug("queryUserOnline: User IDs: {}", request.getUserIdsList());

        try {
            QueryUserOnlineResp response = this.userPresenceStorage.getUserOnline(request);
            logger.debug(
                    "queryUserOnline: Found {} online users",
                    response.getUserOnlineInfosList().size());

            for (UserOnlineInfo userInfo : response.getUserOnlineInfosList()) {
                logger.debug(
                        "queryUserOnline: User {} on device {} at server {}",
                        userInfo.getUserId(),
                        userInfo.getDeviceId(),
                        userInfo.getServerLocation());
            }

            logger.debug(
                    "queryUserOnline: Sending response with {} users",
                    response.getUserOnlineInfosList().size());
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            logger.debug("queryUserOnline: Query response completed");
        } catch (Exception e) {
            logger.error(
                    "queryUserOnline: Error querying user online status - {}", e.getMessage(), e);
            responseObserver.onError(e);
        }
    }
}
