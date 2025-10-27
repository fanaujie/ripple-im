package com.fanaujie.ripple.msggateway.server.ws;

import com.fanaujie.ripple.communication.batch.BatchExecutorService;
import com.fanaujie.ripple.msggateway.batch.UserOnlineBatchTask;
import com.fanaujie.ripple.msggateway.server.jwt.JwtDecoder;
import com.fanaujie.ripple.msggateway.server.users.OnlineUser;
import com.fanaujie.ripple.msggateway.server.ws.config.WsConfig;
import com.fanaujie.ripple.msggateway.server.ws.handler.HeartbeatHandler;
import com.fanaujie.ripple.msggateway.server.ws.handler.WebSocketRouterHandler;
import com.fanaujie.ripple.shaded.netty.bootstrap.ServerBootstrap;
import com.fanaujie.ripple.shaded.netty.channel.*;
import com.fanaujie.ripple.shaded.netty.channel.nio.NioIoHandler;
import com.fanaujie.ripple.shaded.netty.channel.socket.nio.NioServerSocketChannel;
import com.fanaujie.ripple.shaded.netty.channel.socket.nio.NioSocketChannel;
import com.fanaujie.ripple.shaded.netty.handler.codec.http.HttpObjectAggregator;
import com.fanaujie.ripple.shaded.netty.handler.codec.http.HttpServerCodec;
import com.fanaujie.ripple.shaded.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import com.fanaujie.ripple.shaded.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import com.fanaujie.ripple.shaded.netty.handler.stream.ChunkedWriteHandler;
import com.fanaujie.ripple.shaded.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class WsService {

    private static final int MAX_CONTENT_LENGTH = 65536;

    Logger logger = LoggerFactory.getLogger(WsService.class);

    private final WsConfig wsConfig;
    private final JwtDecoder jwtDecoder;
    private final OnlineUser onlineUser;
    private final BatchExecutorService<UserOnlineBatchTask> batchExecutorService;
    private final String serverLocation;

    public WsService(
            WsConfig config,
            JwtDecoder jwtDecoder,
            OnlineUser onlineUser,
            BatchExecutorService<UserOnlineBatchTask> batchExecutorService,
            String serverLocation) {
        this.wsConfig = config;
        this.jwtDecoder = jwtDecoder;
        this.onlineUser = onlineUser;
        this.batchExecutorService = batchExecutorService;
        this.serverLocation = serverLocation;
    }

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public CompletableFuture<Void> startAsync() {
        return CompletableFuture.runAsync(
                () -> {
                    try {
                        start();
                    } catch (Exception e) {
                        logger.error("Failed to start WebSocket server", e);
                        throw new RuntimeException(e);
                    }
                });
    }

    public void start() {
        bossGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        WebSocketRouterHandler webSocketRouterHandler =
                new WebSocketRouterHandler(jwtDecoder, onlineUser);
        HeartbeatHandler heartbeatHandler =
                new HeartbeatHandler(batchExecutorService, serverLocation);
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(
                        new ChannelInitializer<NioSocketChannel>() {
                            @Override
                            protected void initChannel(NioSocketChannel ch) {
                                ChannelPipeline pipeline = ch.pipeline();
                                pipeline.addLast(
                                        new IdleStateHandler(wsConfig.getIdleSeconds(), 0, 0));
                                pipeline.addLast(new HttpServerCodec());
                                pipeline.addLast(new HttpObjectAggregator(MAX_CONTENT_LENGTH));
                                pipeline.addLast(new ChunkedWriteHandler());
                                pipeline.addLast(
                                        new WebSocketServerCompressionHandler(MAX_CONTENT_LENGTH));
                                pipeline.addLast(
                                        new WebSocketServerProtocolHandler(
                                                wsConfig.getWsPath(), null, true));
                                pipeline.addLast(webSocketRouterHandler);
                                pipeline.addLast(heartbeatHandler);
                            }
                        });
        try {
            ChannelFuture serverChannelFuture = serverBootstrap.bind(wsConfig.getPort()).sync();
            logger.info("WsServer started on port: {}", wsConfig.getPort());
            serverChannelFuture.channel().closeFuture().sync();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        try {
            if (workerGroup != null) {
                workerGroup.shutdownGracefully().sync();
            }
            if (bossGroup != null) {
                bossGroup.shutdownGracefully().sync();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Error stopping the WsServer", e);
        } finally {
            logger.info("WsServer stopped.");
        }
    }
}
