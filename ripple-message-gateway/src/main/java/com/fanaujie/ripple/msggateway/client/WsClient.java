package com.fanaujie.ripple.msggateway.client;

import com.fanaujie.ripple.protobuf.messaging.RippleMessage;
import com.fanaujie.ripple.protobuf.messaging.HeartbeatRequest;
import com.fanaujie.ripple.protobuf.messaging.HeartbeatResponse;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class WsClient {

    private static final Logger logger = LoggerFactory.getLogger(WsClient.class);
    private static final int MAX_CONTENT_LENGTH = 65536;

    private final Map<String, CompletableFuture<HeartbeatResponse>> responseFutures =
            new ConcurrentHashMap<>();

    private final EventLoopGroup group;
    private final Channel channel;
    private WebSocketClientHandshaker handshaker;

    public WsClient(String host, int port, String path) {
        group = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        try {
            URI uri = new URI("ws://" + host + ":" + port + path);
            final WsClientHandler handler =
                    new WsClientHandler(
                            WebSocketClientHandshakerFactory.newHandshaker(
                                    uri,
                                    WebSocketVersion.V13,
                                    null,
                                    true,
                                    new DefaultHttpHeaders()),
                            responseFutures);
            Bootstrap bootstrap = new Bootstrap();
            bootstrap
                    .group(group)
                    .channel(NioSocketChannel.class)
                    .handler(
                            new ChannelInitializer<SocketChannel>() {
                                @Override
                                protected void initChannel(SocketChannel ch) {
                                    ChannelPipeline pipeline = ch.pipeline();
                                    pipeline.addLast(new HttpClientCodec());
                                    pipeline.addLast(new HttpObjectAggregator(MAX_CONTENT_LENGTH));
                                    pipeline.addLast(
                                            new WebSocketClientCompressionHandler(
                                                    MAX_CONTENT_LENGTH));
                                    pipeline.addLast(handler);
                                }
                            });

            channel = bootstrap.connect(host, port).sync().channel();
            handler.getHandshakeFuture().sync();
        } catch (Exception e) {
            if (group != null) {
                group.shutdownGracefully();
            }
            throw new RuntimeException("Failed to connect to WebSocket server", e);
        }
    }


    public CompletableFuture<HeartbeatResponse> sendHeartbeat(String userId) {
        CompletableFuture<HeartbeatResponse> future = new CompletableFuture<>();
        if (channel == null || !channel.isActive()) {
            future.completeExceptionally(new IllegalStateException("Client not connected"));
            return future;
        }
        
        long timestamp = System.currentTimeMillis();
        responseFutures.put(userId, future);
        
        try {
            HeartbeatRequest heartbeatRequest = HeartbeatRequest.newBuilder()
                    .setUserId(userId)
                    .setTimestamp(timestamp)
                    .build();
                    
            RippleMessage request = RippleMessage.newBuilder()
                    .setHeartbeatRequest(heartbeatRequest)
                    .build();
                    
            byte[] data = request.toByteArray();
            BinaryWebSocketFrame frame =
                    new BinaryWebSocketFrame(channel.alloc().buffer(data.length).writeBytes(data));

            ChannelFuture writeFuture = channel.writeAndFlush(frame);
            writeFuture.addListener(
                    channelFuture -> {
                        if (!channelFuture.isSuccess()) {
                            responseFutures.remove(userId);
                            future.completeExceptionally(channelFuture.cause());
                        }
                    });
        } catch (Exception e) {
            responseFutures.remove(userId);
            future.completeExceptionally(e);
        }

        return future;
    }

    public void close() {
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(new CloseWebSocketFrame());
        }
        if (group != null) {
            group.shutdownGracefully();
        }
        logger.info("WebSocket client closed");
    }

    public boolean isConnected() {
        return channel != null && channel.isActive();
    }
}
