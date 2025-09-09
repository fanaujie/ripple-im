package com.fanaujie.ripple.snowflakeid.client;

import com.fanaujie.ripple.protobuf.snowflakeid.GenerateIdResponse;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@ChannelHandler.Sharable
public class ClientHandler extends SimpleChannelInboundHandler<GenerateIdResponse> {

    private final Logger logger = LoggerFactory.getLogger(ClientHandler.class);
    private final Map<String, CompletableFuture<GenerateIdResponse>> responseFutures;

    public ClientHandler(Map<String, CompletableFuture<GenerateIdResponse>> responseFutures) {
        this.responseFutures = responseFutures;
    }

    @Override
    protected void channelRead0(
            ChannelHandlerContext channelHandlerContext, GenerateIdResponse generateIdResponse)
            throws Exception {
        CompletableFuture<GenerateIdResponse> v =
                this.responseFutures.get(generateIdResponse.getRequestId());
        if (v != null) {
            v.complete(generateIdResponse);
        } else {
            logger.warn(
                    "Received response for unknown requestId: {}",
                    generateIdResponse.getRequestId());
        }
    }
}
