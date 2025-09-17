package com.fanaujie.ripple.msggateway.server.ws;

import com.fanaujie.ripple.protobuf.messaging.RippleMessage;
import com.fanaujie.ripple.protobuf.messaging.HeartbeatRequest;
import com.fanaujie.ripple.protobuf.messaging.HeartbeatResponse;
import com.fanaujie.ripple.shaded.netty.buffer.ByteBufUtil;
import com.fanaujie.ripple.shaded.netty.channel.ChannelHandlerContext;
import com.fanaujie.ripple.shaded.netty.channel.SimpleChannelInboundHandler;
import com.fanaujie.ripple.shaded.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import com.fanaujie.ripple.shaded.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import com.fanaujie.ripple.shaded.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebSocketFrameHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketFrameHandler.class);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame webSocketFrame) {
        if (webSocketFrame instanceof BinaryWebSocketFrame) {
            handleBinaryFrame(ctx, (BinaryWebSocketFrame) webSocketFrame);
        } else if (webSocketFrame instanceof TextWebSocketFrame) {
            String message = "unsupported frame type: " + webSocketFrame.getClass().getName();
            throw new UnsupportedOperationException(message);
        }
    }

    private void handleBinaryFrame(ChannelHandlerContext ctx, BinaryWebSocketFrame frame) {
        try {
            byte[] data = ByteBufUtil.getBytes(frame.content());
            RippleMessage message = RippleMessage.parseFrom(data);
            logger.info("Received RippleMessage: {}", message);

            // Handle one of message types
            if (message.hasHeartbeatRequest()) {
                handleHeartbeatRequest(ctx, message.getHeartbeatRequest());
            } else {
                logger.warn("Received unknown message type in RippleMessage");
            }

        } catch (Exception e) {
            logger.error("Error processing binary frame", e);
        }
    }

    private void handleHeartbeatRequest(ChannelHandlerContext ctx, HeartbeatRequest request) {
        try {
            logger.info(
                    "Processing heartbeat request from user: {}, timestamp: {}",
                    request.getUserId(),
                    request.getTimestamp());

            HeartbeatResponse heartbeatResponse =
                    HeartbeatResponse.newBuilder()
                            .setUserId(request.getUserId())
                            .setClientTimestamp(request.getTimestamp())
                            .setServerTimestamp(System.currentTimeMillis())
                            .build();

            RippleMessage response =
                    RippleMessage.newBuilder().setHeartbeatResponse(heartbeatResponse).build();
            BinaryWebSocketFrame resFrame =
                    new BinaryWebSocketFrame(
                            ctx.alloc().buffer().writeBytes(response.toByteArray()));
            ctx.writeAndFlush(resFrame);

            logger.info("Sent heartbeat response to user: {}", request.getUserId());

        } catch (Exception e) {
            logger.error("Error processing heartbeat request", e);
        }
    }
}
