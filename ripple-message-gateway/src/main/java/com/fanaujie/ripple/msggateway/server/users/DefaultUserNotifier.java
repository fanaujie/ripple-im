package com.fanaujie.ripple.msggateway.server.users;

import com.fanaujie.ripple.protobuf.msggateway.PushMessageRequest;
import com.fanaujie.ripple.protobuf.wsmessage.WsMessage;
import com.fanaujie.ripple.shaded.netty.channel.Channel;
import com.fanaujie.ripple.shaded.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultUserNotifier implements UserNotifier {

    private final Logger logger = LoggerFactory.getLogger(DefaultUserNotifier.class);

    @Override
    public void push(Channel channel, PushMessageRequest request) {
        logger.debug(
                "push: Pushing message to user {} on device {}, message size: {}",
                request.getReceiveUserId(),
                request.getRequestDeviceId(),
                request.toByteArray().length);
        // Create a BinaryWebSocketFrame with the WsMessage bytes
        WsMessage wsMessage = WsMessage.newBuilder().setPushMessageRequest(request).build();
        BinaryWebSocketFrame respFrame =
                new BinaryWebSocketFrame(
                        channel.alloc().buffer().writeBytes(wsMessage.toByteArray()));
        logger.debug("push: BinaryWebSocketFrame created, channel: {}", channel);

        var f = channel.writeAndFlush(respFrame);
        logger.debug("push: Message flushed to channel, awaiting response");

        f.addListener(
                future -> {
                    if (!future.isSuccess()) {
                        logger.error(
                                "push: Failed to push message to user {} on device {} - {}",
                                request.getReceiveUserId(),
                                request.getRequestDeviceId(),
                                future.cause().getMessage(),
                                future.cause());
                    } else {
                        logger.debug(
                                "push: Successfully pushed message to user {} on device {}",
                                request.getReceiveUserId(),
                                request.getRequestDeviceId());
                    }
                });
    }
}
