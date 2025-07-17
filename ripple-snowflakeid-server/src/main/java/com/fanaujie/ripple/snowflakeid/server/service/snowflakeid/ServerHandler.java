package com.fanaujie.ripple.snowflakeid.server.service.snowflakeid;

import com.fanaujie.ripple.protobuf.snowflakeid.GenerateIdRequest;
import com.fanaujie.ripple.protobuf.snowflakeid.GenerateIdResponse;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

@ChannelHandler.Sharable
public class ServerHandler extends SimpleChannelInboundHandler<GenerateIdRequest> {

    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public ServerHandler(SnowflakeIdGenerator snowflakeIdGenerator) {
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }
    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, GenerateIdRequest generateIdRequest) throws Exception {
        GenerateIdResponse res = GenerateIdResponse.newBuilder()
                .setRequestId(generateIdRequest.getRequestId())
                .setId(this.snowflakeIdGenerator.nextId())
                .build();
        channelHandlerContext.writeAndFlush(res);
    }
}
