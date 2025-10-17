package com.fanaujie.ripple.msggateway.client;

import com.fanaujie.ripple.protobuf.messaging.HeartbeatResponse;
import com.fanaujie.ripple.protobuf.messaging.WsMessage;
import com.fanaujie.ripple.shaded.netty.channel.*;
import com.fanaujie.ripple.shaded.netty.buffer.*;
import com.fanaujie.ripple.shaded.netty.handler.codec.http.FullHttpResponse;
import com.fanaujie.ripple.shaded.netty.handler.codec.http.websocketx.*;
import com.fanaujie.ripple.shaded.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class WsClientHandler extends SimpleChannelInboundHandler<Object> {

    private static final Logger logger = LoggerFactory.getLogger(WsClientHandler.class);

    private final WebSocketClientHandshaker handshaker;
    private final Map<String, CompletableFuture<HeartbeatResponse>> responseFutures;
    private ChannelPromise handshakeFuture;

    public WsClientHandler(
            WebSocketClientHandshaker handshaker,
            Map<String, CompletableFuture<HeartbeatResponse>> responseFutures) {
        this.handshaker = handshaker;
        this.responseFutures = responseFutures;
    }

    public ChannelPromise getHandshakeFuture() {
        return handshakeFuture;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        handshakeFuture = ctx.newPromise();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        handshaker.handshake(ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        logger.info("WebSocket client disconnected");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        Channel ch = ctx.channel();

        if (!handshaker.isHandshakeComplete()) {
            try {
                handshaker.finishHandshake(ch, (FullHttpResponse) msg);
                logger.info("WebSocket client connected");
                handshakeFuture.setSuccess();
            } catch (WebSocketHandshakeException e) {
                logger.error("WebSocket handshake failed", e);
                handshakeFuture.setFailure(e);
            }
            return;
        }

        if (msg instanceof FullHttpResponse response) {
            throw new IllegalStateException(
                    "Unexpected FullHttpResponse (getStatus="
                            + response.status()
                            + ", content="
                            + response.content().toString(CharsetUtil.UTF_8)
                            + ')');
        }

        WebSocketFrame frame = (WebSocketFrame) msg;

        if (frame instanceof BinaryWebSocketFrame) {
            handleBinaryFrame((BinaryWebSocketFrame) frame);
        } else if (frame instanceof TextWebSocketFrame) {
            logger.warn("Received unsupported text frame: {}", ((TextWebSocketFrame) frame).text());
        } else if (frame instanceof PongWebSocketFrame) {
            logger.info("Received pong frame");
        } else if (frame instanceof CloseWebSocketFrame) {
            logger.info("Received close frame");
            ch.close();
        }
    }

    private void handleBinaryFrame(BinaryWebSocketFrame frame) {
        try {
            byte[] data = ByteBufUtil.getBytes(frame.content());
            WsMessage message = WsMessage.parseFrom(data);
            logger.debug("Received RippleMessage: {}", message);

            // Handle one of message types
            if (message.hasHeartbeatResponse()) {
                handleHeartbeatResponse(message.getHeartbeatResponse());
            } else {
                logger.warn("Received unknown message type in RippleMessage");
            }

        } catch (Exception e) {
            logger.error("Error processing binary frame", e);
        }
    }

    private void handleHeartbeatResponse(HeartbeatResponse response) {
        try {
            logger.debug(
                    "Received heartbeat response for user: {}, client_timestamp: {}, server_timestamp: {}",
                    response.getUserId(),
                    response.getClientTimestamp(),
                    response.getServerTimestamp());

            CompletableFuture<HeartbeatResponse> future =
                    responseFutures.remove(response.getUserId());
            if (future != null) {
                future.complete(response);
            } else {
                logger.warn(
                        "No pending heartbeat request found for userId: {}", response.getUserId());
            }

        } catch (Exception e) {
            logger.error("Error processing heartbeat response", e);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("WebSocket client error", cause);
        if (!handshakeFuture.isDone()) {
            handshakeFuture.setFailure(cause);
        }
        ctx.close();
    }
}
