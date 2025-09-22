package com.fanaujie.ripple.msgdispatcher.server;

import com.fanaujie.ripple.msgdispatcher.processor.MessageProcessor;
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
    private final MessageProcessor messageProcessor;
    private Server server;

    public GrpcServer(int port, MessageProcessor messageProcessor) {
        this.port = port;
        this.messageProcessor = messageProcessor;
    }

    public CompletableFuture<Void> startAsync() {
        return CompletableFuture.runAsync(
                () -> {
                    try {
                        MessageDispatcherServiceImpl messageDispatcherService =
                                new MessageDispatcherServiceImpl(messageProcessor);

                        server =
                                ServerBuilder.forPort(port)
                                        .addService(messageDispatcherService)
                                        .build()
                                        .start();

                        logger.info("MessageDispatcher gRPC server started on port: {}", port);

                        Runtime.getRuntime()
                                .addShutdownHook(
                                        new Thread(
                                                () -> {
                                                    logger.info(
                                                            "Shutting down MessageDispatcher gRPC server due to JVM shutdown");
                                                    try {
                                                        GrpcServer.this.stop();
                                                    } catch (InterruptedException e) {
                                                        logger.error(
                                                                "Error during MessageDispatcher gRPC server shutdown",
                                                                e);
                                                        Thread.currentThread().interrupt();
                                                    }
                                                }));
                        server.awaitTermination();
                    } catch (IOException | InterruptedException e) {
                        logger.error("Failed to start MessageDispatcher gRPC server", e);
                        throw new RuntimeException(e);
                    }
                });
    }

    public void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
            logger.info("MessageDispatcher gRPC server stopped");
        }
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }
}