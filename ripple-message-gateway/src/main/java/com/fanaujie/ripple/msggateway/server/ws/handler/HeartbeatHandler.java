package com.fanaujie.ripple.msggateway.server.ws.handler;

import com.fanaujie.ripple.protobuf.wsmessage.HeartbeatRequest;
import com.fanaujie.ripple.protobuf.wsmessage.HeartbeatResponse;
import com.fanaujie.ripple.protobuf.wsmessage.WsMessage;
import com.fanaujie.ripple.shaded.netty.channel.ChannelHandler;
import com.fanaujie.ripple.shaded.netty.channel.ChannelHandlerContext;
import com.fanaujie.ripple.shaded.netty.channel.SimpleChannelInboundHandler;
import com.fanaujie.ripple.shaded.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;

@ChannelHandler.Sharable
public class HeartbeatHandler extends SimpleChannelInboundHandler<WsMessage> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WsMessage rippleMessage) {
        if (rippleMessage.hasHeartbeatRequest()) {
            HeartbeatRequest request = rippleMessage.getHeartbeatRequest();
            HeartbeatResponse heartbeatResponse =
                    HeartbeatResponse.newBuilder()
                            .setUserId(request.getUserId())
                            .setClientTimestamp(request.getTimestamp())
                            .setServerTimestamp(System.currentTimeMillis())
                            .build();
            WsMessage response =
                    WsMessage.newBuilder().setHeartbeatResponse(heartbeatResponse).build();
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
