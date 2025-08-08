package com.fanaujie.ripple.msggateway.server.ws;

import com.fanaujie.ripple.msggateway.server.config.WsConfig;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WsService {

    private static final int MAX_CONTENT_LENGTH = 65536;

    Logger logger = LoggerFactory.getLogger(WsService.class);

    private final WsConfig config;

    public WsService(WsConfig config) {
        this.config = config;
    }

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public void start() {
        bossGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(
                        new ChannelInitializer<NioSocketChannel>() {
                            @Override
                            protected void initChannel(NioSocketChannel ch) {
                                ChannelPipeline pipeline = ch.pipeline();
                                pipeline.addLast(new HttpServerCodec());
                                pipeline.addLast(new HttpObjectAggregator(MAX_CONTENT_LENGTH));
                                pipeline.addLast(new ChunkedWriteHandler());
                                pipeline.addLast(
                                        new WebSocketServerCompressionHandler(MAX_CONTENT_LENGTH));
                                pipeline.addLast(
                                        new WebSocketServerProtocolHandler(
                                                config.getWsPath(), null, true));
                                pipeline.addLast(new WebSocketFrameHandler());
                            }
                        });
        try {
            ChannelFuture serverChannelFuture = serverBootstrap.bind(this.config.getPort()).sync();
            logger.info("WsServer started on port: {}", this.config.getPort());
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
