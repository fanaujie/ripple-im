package com.fanaujie.ripple.msggateway.server.grpc;

import com.fanaujie.ripple.msggateway.server.users.OnlineUser;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class GrpcServer {

    private static final Logger logger = LoggerFactory.getLogger(GrpcServer.class);

    private final int port;
    private final OnlineUser onlineUser;
    private Server server;

    public GrpcServer(int port, OnlineUser onlineUser) {
        this.port = port;
        this.onlineUser = onlineUser;
    }

    public CompletableFuture<Void> startAsync() {
        return CompletableFuture.runAsync(
                () -> {
                    try {
                        MessageGatewayServiceImpl messageGatewayService =
                                new MessageGatewayServiceImpl(onlineUser);

                        server =
                                ServerBuilder.forPort(port)
                                        .addService(messageGatewayService)
                                        .build()
                                        .start();

                        logger.info("gRPC server started on port: {}", port);

                        Runtime.getRuntime()
                                .addShutdownHook(
                                        new Thread(
                                                () -> {
                                                    logger.info(
                                                            "Shutting down gRPC server due to JVM shutdown");
                                                    try {
                                                        GrpcServer.this.stop();
                                                    } catch (InterruptedException e) {
                                                        logger.error(
                                                                "Error during gRPC server shutdown",
                                                                e);
                                                        Thread.currentThread().interrupt();
                                                    }
                                                }));
                        server.awaitTermination();
                    } catch (IOException | InterruptedException e) {
                        logger.error("Failed to start gRPC server", e);
                        throw new RuntimeException(e);
                    }
                });
    }

    public void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
            logger.info("gRPC server stopped");
        }
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }
}
