package com.fanaujie.ripple.userpresence.server;

import com.fanaujie.ripple.cache.service.UserPresenceStorage;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class GrpcServer {

    private static final Logger logger = LoggerFactory.getLogger(GrpcServer.class);

    private final int port;
    private final UserPresenceStorage userPresenceStorage;
    private Server server;

    public GrpcServer(int port, UserPresenceStorage userPresenceStorage) {
        this.port = port;
        this.userPresenceStorage = userPresenceStorage;
    }

    public CompletableFuture<Void> startAsync() {
        return CompletableFuture.runAsync(
                () -> {
                    try {
                        UserPresenceServiceImpl userPresenceService =
                                new UserPresenceServiceImpl(userPresenceStorage);

                        server =
                                ServerBuilder.forPort(port)
                                        .addService(userPresenceService)
                                        .addService(ProtoReflectionService.newInstance())
                                        .build()
                                        .start();

                        logger.info("UserPresence gRPC server started on port: {}", port);

                        Runtime.getRuntime()
                                .addShutdownHook(
                                        new Thread(
                                                () -> {
                                                    logger.info(
                                                            "Shutting down UserPresence gRPC server due to JVM shutdown");
                                                    try {
                                                        GrpcServer.this.stop();
                                                    } catch (InterruptedException e) {
                                                        logger.error(
                                                                "Error during UserPresence gRPC server shutdown",
                                                                e);
                                                        Thread.currentThread().interrupt();
                                                    }
                                                }));
                        server.awaitTermination();
                    } catch (IOException | InterruptedException e) {
                        logger.error("Failed to start UserPresence gRPC server", e);
                        throw new RuntimeException(e);
                    }
                });
    }

    public void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
            logger.info("UserPresence gRPC server stopped");
        }
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }
}
