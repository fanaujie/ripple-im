package com.fanaujie.ripple.msgapiserver.server;

import com.fanaujie.ripple.communication.processor.ProcessorDispatcher;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventResp;
import com.fanaujie.ripple.protobuf.msgapiserver.SendMessageReq;
import com.fanaujie.ripple.protobuf.msgapiserver.SendMessageResp;
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
    private final ProcessorDispatcher<SendMessageReq.MessageCase, SendMessageReq, SendMessageResp>
            messageDispatcher;
    private final ProcessorDispatcher<SendEventReq.EventCase, SendEventReq, SendEventResp>
            eventDispatcher;
    private Server server;

    public GrpcServer(
            int port,
            ProcessorDispatcher<SendMessageReq.MessageCase, SendMessageReq, SendMessageResp>
                    messageDispatcher,
            ProcessorDispatcher<SendEventReq.EventCase, SendEventReq, SendEventResp>
                    eventDispatcher) {
        this.port = port;
        this.messageDispatcher = messageDispatcher;
        this.eventDispatcher = eventDispatcher;
    }

    public CompletableFuture<Void> startAsync() {
        return CompletableFuture.runAsync(
                () -> {
                    try {
                        MessageDispatcherServiceImpl messageDispatcherService =
                                new MessageDispatcherServiceImpl(
                                        messageDispatcher, eventDispatcher);

                        server =
                                ServerBuilder.forPort(port)
                                        .addService(messageDispatcherService)
                                        .addService(ProtoReflectionService.newInstance())
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
