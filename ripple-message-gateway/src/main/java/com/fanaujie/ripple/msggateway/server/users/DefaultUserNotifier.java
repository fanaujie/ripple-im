package com.fanaujie.ripple.msggateway.server.users;

import com.fanaujie.ripple.protobuf.msggateway.PushMessageRequest;
import com.fanaujie.ripple.shaded.netty.channel.Channel;
import com.fanaujie.ripple.shaded.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultUserNotifier implements UserNotifier {

    private final Logger logger = LoggerFactory.getLogger(DefaultUserNotifier.class);

    @Override
    public void push(Channel channel, PushMessageRequest request) {
        BinaryWebSocketFrame respFrame =
                new BinaryWebSocketFrame(
                        channel.alloc().buffer().writeBytes(request.toByteArray()));
        var f = channel.writeAndFlush(respFrame);
        f.addListener(
                future -> {
                    if (!future.isSuccess()) {
                        logger.error(
                                "Failed to push message to user {} on device {}",
                                request.getReceiveUserId(),
                                request.getRequestDeviceId(),
                                future.cause());
                    }
                });
    }
}
