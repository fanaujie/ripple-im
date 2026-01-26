package com.fanaujie.ripple.communication.gateway;

import com.fanaujie.ripple.protobuf.msggateway.PushMessageRequest;
import com.fanaujie.ripple.protobuf.push.SSEEventType;
import com.fanaujie.ripple.protobuf.userpresence.UserOnlineInfo;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface GatewayPusher {

    CompletableFuture<Void> push(
            String serverAddress, List<UserOnlineInfo> userInfos, PushMessageRequest request);

    CompletableFuture<Void> pushSSE(
            long receiveUserId,
            long sendUserId,
            String conversationId,
            SSEEventType eventType,
            String content,
            long messageId,
            long sendTimestamp);
}
