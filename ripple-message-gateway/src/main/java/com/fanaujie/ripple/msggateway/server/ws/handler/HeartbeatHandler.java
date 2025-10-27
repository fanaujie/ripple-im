package com.fanaujie.ripple.msggateway.server.ws.handler;

import com.fanaujie.ripple.communication.batch.BatchExecutorService;
import com.fanaujie.ripple.msggateway.batch.UserOnlineBatchTask;
import com.fanaujie.ripple.protobuf.wsmessage.HeartbeatRequest;
import com.fanaujie.ripple.protobuf.wsmessage.HeartbeatResponse;
import com.fanaujie.ripple.protobuf.wsmessage.WsMessage;
import com.fanaujie.ripple.shaded.netty.channel.ChannelHandler;
import com.fanaujie.ripple.shaded.netty.channel.ChannelHandlerContext;
import com.fanaujie.ripple.shaded.netty.channel.SimpleChannelInboundHandler;
import com.fanaujie.ripple.shaded.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import com.fanaujie.ripple.shaded.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

@ChannelHandler.Sharable
public class HeartbeatHandler extends SimpleChannelInboundHandler<WsMessage> {

    private static final Logger logger = LoggerFactory.getLogger(HeartbeatHandler.class);
    private static final AttributeKey<String> DEVICE_ID_KEY = AttributeKey.valueOf("deviceId");

    private final BatchExecutorService<UserOnlineBatchTask> batchExecutorService;
    private final String serverLocation;

    public HeartbeatHandler(
            BatchExecutorService<UserOnlineBatchTask> batchExecutorService,
            String serverLocation) {
        this.batchExecutorService = batchExecutorService;
        this.serverLocation = serverLocation;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WsMessage rippleMessage) {
        logger.debug(
                "channelRead0: Received WsMessage, hasHeartbeatRequest: {}",
                rippleMessage.hasHeartbeatRequest());

        if (rippleMessage.hasHeartbeatRequest()) {
            HeartbeatRequest request = rippleMessage.getHeartbeatRequest();
            String userId = request.getUserId();
            long clientTimestamp = request.getTimestamp();
            long serverTimestamp = Instant.now().getEpochSecond();

            logger.debug(
                    "channelRead0: Received heartbeat from userId: {}, clientTimestamp: {}, serverTimestamp: {}",
                    userId,
                    clientTimestamp,
                    serverTimestamp);

            long timeDifference = serverTimestamp - clientTimestamp;
            logger.debug(
                    "channelRead0: Time difference between client and server: {} seconds",
                    timeDifference);

            // Push user online status to batch queue for periodic refresh
            String deviceId = ctx.channel().attr(DEVICE_ID_KEY).get();
            if (deviceId != null) {
                try {
                    UserOnlineBatchTask task =
                            new UserOnlineBatchTask(userId, deviceId, true, this.serverLocation);
                    batchExecutorService.push(task);
                    logger.debug(
                            "channelRead0: User online heartbeat task pushed to batch queue - userId: {}, deviceId: {}",
                            userId,
                            deviceId);
                } catch (InterruptedException e) {
                    logger.error(
                            "channelRead0: Failed to push heartbeat task to batch queue - userId: {}, deviceId: {}",
                            userId,
                            deviceId,
                            e);
                    Thread.currentThread().interrupt();
                }
            } else {
                logger.warn(
                        "channelRead0: Device ID not found in channel attributes for userId: {}",
                        userId);
            }

            HeartbeatResponse heartbeatResponse =
                    HeartbeatResponse.newBuilder()
                            .setUserId(userId)
                            .setClientTimestamp(clientTimestamp)
                            .setServerTimestamp(serverTimestamp)
                            .build();
            logger.debug("channelRead0: Built HeartbeatResponse for userId: {}", userId);

            WsMessage response =
                    WsMessage.newBuilder().setHeartbeatResponse(heartbeatResponse).build();
            logger.debug(
                    "channelRead0: Built WsMessage response, size: {} bytes",
                    response.getSerializedSize());

            BinaryWebSocketFrame respFrame =
                    new BinaryWebSocketFrame(
                            ctx.alloc().buffer().writeBytes(response.toByteArray()));
            logger.debug("channelRead0: Created BinaryWebSocketFrame");

            ctx.writeAndFlush(respFrame);
            logger.debug(
                    "channelRead0: Heartbeat response sent to userId: {} on channel: {}",
                    userId,
                    ctx.channel());
        } else {
            logger.debug(
                    "channelRead0: Message is not a heartbeat request, passing to next handler");
            ctx.fireChannelRead(rippleMessage);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error(
                "exceptionCaught: Exception occurred in HeartbeatHandler - {}",
                cause.getMessage(),
                cause);
        ctx.close();
    }
}
