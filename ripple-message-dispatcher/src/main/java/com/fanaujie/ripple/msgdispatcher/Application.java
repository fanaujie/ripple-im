package com.fanaujie.ripple.msgdispatcher;

import com.fanaujie.ripple.msgdispatcher.processor.DefaultMessageProcessor;
import com.fanaujie.ripple.msgdispatcher.processor.MessageProcessor;
import com.fanaujie.ripple.msgdispatcher.server.GrpcServer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class Application {

    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    public void run() {
        Config config = ConfigFactory.load();
        int grpcPort = config.getInt("server.grpc.port");

        logger.info("Starting Message Dispatcher server...");
        logger.info("gRPC Port: {}", grpcPort);

        MessageProcessor messageProcessor = new DefaultMessageProcessor();
        GrpcServer grpcServer = new GrpcServer(grpcPort, messageProcessor);
        CompletableFuture<Void> grpcFuture = grpcServer.startAsync();
        grpcFuture.join();
    }

    public static void main(String[] args) {
        Application app = new Application();
        app.run();
    }
}