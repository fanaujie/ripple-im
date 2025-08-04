package com.fanaujie.ripple.snowflakeid.server.service.snowflakeid;

import com.fanaujie.ripple.protobuf.snowflakeid.GenerateIdRequest;
import com.fanaujie.ripple.snowflakeid.server.Application;
import com.fanaujie.ripple.snowflakeid.server.config.Config;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SnowflakeIdService {

    Logger logger = LoggerFactory.getLogger(SnowflakeIdService.class);

    private final Config config;

    public SnowflakeIdService(Config config) {
        this.config = config;
    }


    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public void start() {
        bossGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        SnowflakeIdGenerator snowflakeIdGenerator = new SnowflakeIdGenerator(this.config.getWorkerId());
        serverBootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast("frameDecoder", new ProtobufVarint32FrameDecoder());
                        pipeline.addLast("protobufDecoder", new ProtobufDecoder(GenerateIdRequest.getDefaultInstance()));
                        pipeline.addLast("frameEncoder", new ProtobufVarint32LengthFieldPrepender());
                        pipeline.addLast("protobufEncoder", new ProtobufEncoder());
                        pipeline.addLast("handler", new ServerHandler(snowflakeIdGenerator));
                    }
                });
        try {
            ChannelFuture serverChannelFuture = serverBootstrap.bind(this.config.getPort()).sync();
            logger.info("Server started on port: {}", this.config.getPort());
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
