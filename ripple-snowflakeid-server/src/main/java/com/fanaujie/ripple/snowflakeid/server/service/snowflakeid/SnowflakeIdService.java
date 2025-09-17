package com.fanaujie.ripple.snowflakeid.server.service.snowflakeid;

import com.fanaujie.ripple.protobuf.snowflakeid.GenerateIdRequest;
import com.fanaujie.ripple.shaded.netty.bootstrap.ServerBootstrap;
import com.fanaujie.ripple.shaded.netty.channel.*;
import com.fanaujie.ripple.shaded.netty.channel.nio.NioIoHandler;
import com.fanaujie.ripple.shaded.netty.channel.socket.nio.NioServerSocketChannel;
import com.fanaujie.ripple.shaded.netty.channel.socket.nio.NioSocketChannel;
import com.fanaujie.ripple.shaded.netty.handler.codec.protobuf.ProtobufDecoder;
import com.fanaujie.ripple.shaded.netty.handler.codec.protobuf.ProtobufEncoder;
import com.fanaujie.ripple.shaded.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import com.fanaujie.ripple.shaded.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SnowflakeIdService {

    Logger logger = LoggerFactory.getLogger(SnowflakeIdService.class);

    private final long workerId;
    private final int serverPort;

    public SnowflakeIdService(long workerId, int serverPort) {
        this.workerId = workerId;
        this.serverPort = serverPort;
    }

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public void start() {
        bossGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        SnowflakeIdGenerator snowflakeIdGenerator = new SnowflakeIdGenerator(this.workerId);
        serverBootstrap
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(
                        new ChannelInitializer<NioSocketChannel>() {
                            @Override
                            protected void initChannel(NioSocketChannel ch) {
                                ChannelPipeline pipeline = ch.pipeline();
                                pipeline.addLast(
                                        "frameDecoder", new ProtobufVarint32FrameDecoder());
                                pipeline.addLast(
                                        "protobufDecoder",
                                        new ProtobufDecoder(
                                                GenerateIdRequest.getDefaultInstance()));
                                pipeline.addLast(
                                        "frameEncoder", new ProtobufVarint32LengthFieldPrepender());
                                pipeline.addLast("protobufEncoder", new ProtobufEncoder());
                                pipeline.addLast(
                                        "handler", new ServerHandler(snowflakeIdGenerator));
                            }
                        });
        try {
            ChannelFuture serverChannelFuture = serverBootstrap.bind(this.serverPort).sync();
            logger.info("Server started on port: {}", this.serverPort);
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
            logger.error("Error stopping the server", e);
        } finally {
            logger.info("Server stopped.");
        }
    }
}
