package com.fanaujie.ripple.msggateway.server.ws.handler;

import com.fanaujie.ripple.protobuf.messaging.HeartbeatRequest;
import com.fanaujie.ripple.protobuf.messaging.HeartbeatResponse;
import com.fanaujie.ripple.protobuf.messaging.RippleMessage;
import com.fanaujie.ripple.shaded.netty.channel.ChannelHandler;
import com.fanaujie.ripple.shaded.netty.channel.ChannelHandlerContext;
import com.fanaujie.ripple.shaded.netty.channel.SimpleChannelInboundHandler;
import com.fanaujie.ripple.shaded.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;

@ChannelHandler.Sharable
public class HeartbeatHandler extends SimpleChannelInboundHandler<RippleMessage> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RippleMessage rippleMessage) {
        if (rippleMessage.hasHeartbeatRequest()) {
            HeartbeatRequest request = rippleMessage.getHeartbeatRequest();
            HeartbeatResponse heartbeatResponse =
                    HeartbeatResponse.newBuilder()
                            .setUserId(request.getUserId())
                            .setClientTimestamp(request.getTimestamp())
                            .setServerTimestamp(System.currentTimeMillis())
                            .build();
            RippleMessage response =
                    RippleMessage.newBuilder().setHeartbeatResponse(heartbeatResponse).build();
            BinaryWebSocketFrame respFrame =
                    new BinaryWebSocketFrame(
                            ctx.alloc().buffer().writeBytes(response.toByteArray()));
            ctx.writeAndFlush(respFrame);
        } else {
            ctx.fireChannelRead(rippleMessage);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }
}
