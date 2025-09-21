package com.fanaujie.ripple.userpresence;

import com.fanaujie.ripple.userpresence.server.GrpcServer;
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

        logger.info("Starting User Presence server...");
        logger.info("gRPC Port: {}", grpcPort);
        GrpcServer grpcServer = new GrpcServer(grpcPort, null);
        CompletableFuture<Void> grpcFuture = grpcServer.startAsync();
        grpcFuture.join();
    }

    public static void main(String[] args) {
        Application app = new Application();
        app.run();
    }
}
