package com.fanaujie.ripple.snowflakeid.client;

import com.fanaujie.ripple.protobuf.snowflakeid.GenerateIdRequest;
import com.fanaujie.ripple.protobuf.snowflakeid.GenerateIdResponse;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.pool.AbstractChannelPoolHandler;
import io.netty.channel.pool.ChannelPool;
import io.netty.channel.pool.SimpleChannelPool;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.netty.util.concurrent.Future;


import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;


public class SnowflakeIdClient {
    private final RequestIdGenerator requestIdGenerator = new RequestIdGenerator();
    private final Map<String, CompletableFuture<GenerateIdResponse>> responseFutures = new ConcurrentHashMap<String, CompletableFuture<GenerateIdResponse>>();
    private final ChannelPool channelPool;

    public SnowflakeIdClient(String host, int port) {
        EventLoopGroup group = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        Bootstrap bootstrap = new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .remoteAddress(host, port);
        this.channelPool = new SimpleChannelPool(bootstrap, new AbstractChannelPoolHandler() {
            @Override
            public void channelCreated(Channel channel) throws Exception {
                ChannelPipeline pipeline = channel.pipeline();
                pipeline.addLast("frameDecoder", new ProtobufVarint32FrameDecoder());
                pipeline.addLast("protobufDecoder", new ProtobufDecoder(GenerateIdResponse.getDefaultInstance()));
                pipeline.addLast("frameEncoder", new ProtobufVarint32LengthFieldPrepender());
                pipeline.addLast("protobufEncoder", new ProtobufEncoder());
                pipeline.addLast("handler", new ClientHandler(responseFutures));
            }
            @Override
            public void channelAcquired(Channel channel) throws Exception {
            }
        });
    }

    public void Close() {
        if (channelPool != null) {
            channelPool.close();
        }
    }


    public CompletableFuture<GenerateIdResponse> requestSnowflakeId() {
        String requestId = this.requestIdGenerator.generateRequestId();
        GenerateIdRequest request = GenerateIdRequest.newBuilder().setRequestId(requestId).build();

        CompletableFuture<GenerateIdResponse> future = new CompletableFuture<>();
        this.responseFutures.put(requestId, future);
        Future<Channel> channelFuture = channelPool.acquire();
        channelFuture.addListener((Future<Channel> f)  -> {
            if (f.isSuccess()) {
                Channel channel = f.getNow();
                ChannelFuture writeFuture = channel.writeAndFlush(request);
                writeFuture.addListener((ChannelFuture wf) -> {
                    if (wf.isSuccess()) {
                        this.channelPool.release(channel);
                    } else {
                        responseFutures.remove(requestId);
                        future.completeExceptionally(wf.cause());
                        this.channelPool.release(channel);
                    }
                });
            } else {
                responseFutures.remove(requestId);
                future.completeExceptionally(f.cause());
            }
        });

        return future;
    }

}
