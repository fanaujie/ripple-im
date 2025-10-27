package com.fanaujie.ripple.msggateway.server.ws.handler;

import com.fanaujie.ripple.msggateway.exception.WsJwtDecodedException;
import com.fanaujie.ripple.msggateway.exception.WsUnauthorizedException;
import com.fanaujie.ripple.msggateway.server.jwt.JwtDecoder;
import com.fanaujie.ripple.msggateway.server.uitls.HttpHeader;
import com.fanaujie.ripple.msggateway.server.users.OnlineUser;
import com.fanaujie.ripple.protobuf.wsmessage.WsMessage;
import com.fanaujie.ripple.shaded.netty.buffer.ByteBufUtil;
import com.fanaujie.ripple.shaded.netty.channel.ChannelFutureListener;
import com.fanaujie.ripple.shaded.netty.channel.ChannelHandler;
import com.fanaujie.ripple.shaded.netty.channel.ChannelHandlerContext;
import com.fanaujie.ripple.shaded.netty.channel.SimpleChannelInboundHandler;
import com.fanaujie.ripple.shaded.netty.handler.codec.http.DefaultFullHttpResponse;
import com.fanaujie.ripple.shaded.netty.handler.codec.http.HttpHeaders;
import com.fanaujie.ripple.shaded.netty.handler.codec.http.HttpResponseStatus;
import com.fanaujie.ripple.shaded.netty.handler.codec.http.HttpVersion;
import com.fanaujie.ripple.shaded.netty.handler.codec.http.websocketx.*;
import com.fanaujie.ripple.shaded.netty.handler.timeout.IdleState;
import com.fanaujie.ripple.shaded.netty.handler.timeout.IdleStateEvent;
import com.fanaujie.ripple.shaded.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ChannelHandler.Sharable
public class WebSocketRouterHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketRouterHandler.class);
    private static final AttributeKey<String> DEVICE_ID_KEY = AttributeKey.valueOf("deviceId");

    private final JwtDecoder jwtDecoder;
    private final OnlineUser onlineUser;

    public WebSocketRouterHandler(JwtDecoder jwtDecoder, OnlineUser onlineUser) {
        this.jwtDecoder = jwtDecoder;
        this.onlineUser = onlineUser;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.debug(
                "channelInactive: Channel inactive, removing user - channel: {}", ctx.channel());
        this.onlineUser.remove(ctx.channel());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame webSocketFrame)
            throws Exception {
        logger.debug(
                "channelRead0: Received frame type: {}, channel: {}",
                webSocketFrame.getClass().getSimpleName(),
                ctx.channel());

        if (webSocketFrame instanceof BinaryWebSocketFrame frame) {
            logger.debug(
                    "channelRead0: Processing BinaryWebSocketFrame, data size: {}",
                    frame.content().readableBytes());
            byte[] data = ByteBufUtil.getBytes(frame.content());
            WsMessage message = WsMessage.parseFrom(data);
            logger.debug("channelRead0: Successfully parsed WsMessage");
            ctx.fireChannelRead(message);
        } else if (webSocketFrame instanceof PingWebSocketFrame frame) {
            logger.debug("channelRead0: Received PingWebSocketFrame, sending PongWebSocketFrame");
            ctx.writeAndFlush(new PongWebSocketFrame(frame.content().copy()));
        } else if (webSocketFrame instanceof CloseWebSocketFrame) {
            logger.debug("channelRead0: Received CloseWebSocketFrame, closing channel");
            ctx.close();
        } else {
            logger.error(
                    "channelRead0: Unsupported frame type: {}",
                    webSocketFrame.getClass().getName());
            throw new UnsupportedOperationException(
                    "Unsupported frame type: " + webSocketFrame.getClass().getName());
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        logger.debug(
                "userEventTriggered: Event type: {}, channel: {}",
                evt.getClass().getSimpleName(),
                ctx.channel());

        if (evt
                instanceof
                WebSocketServerProtocolHandler.HandshakeComplete handshakeCompletedEvent) {
            logger.debug("userEventTriggered: WebSocket handshake completed");
            HttpHeaders headers = handshakeCompletedEvent.requestHeaders();
            try {
                logger.debug("userEventTriggered: Extracting token and device ID from headers");
                HttpHeader.RippleHeader rippleHeader =
                        HttpHeader.extractTokenAndDeviceId(headers)
                                .orElseThrow(
                                        () -> {
                                            logger.error(
                                                    "userEventTriggered: Ripple Header not found");
                                            return new WsUnauthorizedException(
                                                    "Ripple Header Not Found");
                                        });
                logger.debug(
                        "userEventTriggered: Extracted device ID: {}", rippleHeader.getDeviceId());

                logger.debug("userEventTriggered: Decoding JWT claims");
                JwtDecoder.DecodedJwtClaims jwtClaims =
                        this.jwtDecoder.decodeJwtClaims(rippleHeader.getToken());
                String userId = jwtClaims.getSubject();
                logger.debug("userEventTriggered: Decoded userId: {}", userId);

                // Store deviceId in channel attributes for heartbeat handler
                ctx.channel().attr(DEVICE_ID_KEY).set(rippleHeader.getDeviceId());
                logger.debug(
                        "userEventTriggered: Stored deviceId in channel attributes: {}",
                        rippleHeader.getDeviceId());

                logger.debug(
                        "userEventTriggered: Adding online user - userId: {}, deviceId: {}",
                        userId,
                        rippleHeader.getDeviceId());
                this.onlineUser.add(userId, rippleHeader.getDeviceId(), ctx.channel());
                logger.debug("userEventTriggered: User added to online users successfully");
            } catch (WsUnauthorizedException | WsJwtDecodedException e) {
                logger.error("userEventTriggered: Authentication failed - {}", e.getMessage());
                DefaultFullHttpResponse response =
                        new DefaultFullHttpResponse(
                                HttpVersion.HTTP_1_1, HttpResponseStatus.UNAUTHORIZED);
                response.headers().set("WWW-Authenticate", "Bearer realm=\"Netty WebSocket\"");
                ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
            }
        } else if (evt instanceof IdleStateEvent event) {
            logger.debug("userEventTriggered: IdleStateEvent - state: {}", event.state());
            if (event.state() == IdleState.READER_IDLE) {
                logger.debug(
                        "userEventTriggered: ALL_IDLE triggered, removing user and closing channel");
                this.onlineUser.remove(ctx.channel());
                ctx.close();
            }
        } else {
            logger.debug("userEventTriggered: Passing event to next handler");
            ctx.fireUserEventTriggered(evt);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("exceptionCaught: Exception occurred - {}", cause.getMessage(), cause);
        ctx.close();
    }
}
