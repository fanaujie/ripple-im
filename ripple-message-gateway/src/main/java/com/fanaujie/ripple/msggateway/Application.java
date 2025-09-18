package com.fanaujie.ripple.msggateway;

import com.typesafe.config.ConfigFactory;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Application {

    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    public void run() {
        // print all environment variables
        logger.info("Environment Variables:");
        System.getenv().forEach((key, value) -> logger.info("{}={}", key, value));

        Config config = ConfigFactory.load();
        logger.info("Starting Message Gateway server and gRPC service...");
        logger.info("WebSocket port: {}", config.getInt("server.websocket.port"));
        logger.info("WebSocket path: {}", config.getString("server.websocket.path"));
        logger.info("gRPC port: {}", config.getInt("server.grpc.port"));
    }

    public static void main(String[] args) {
        Application app = new Application();
        app.run();
    }
}
