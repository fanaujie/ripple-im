package com.fanaujie.ripple.msggateway;

import com.fanaujie.ripple.msggateway.server.grpc.GrpcServer;
import com.fanaujie.ripple.msggateway.server.jwt.DefaultJwtDecoder;
import com.fanaujie.ripple.msggateway.server.users.DefaultOnlineUser;
import com.fanaujie.ripple.msggateway.server.ws.WsService;
import com.fanaujie.ripple.msggateway.server.ws.config.WsConfig;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class Application {

    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    public void run() {
        Config config = ConfigFactory.load();
        int wsPort = config.getInt("server.websocket.port");
        String wsPath = config.getString("server.websocket.path");
        int idleSeconds = config.getInt("ripple.heartbeat.idleSeconds");
        int grpcPort = config.getInt("server.grpc.port");
        String jwtSecret = config.getString("oauth2.jwk.secret");

        logger.info("Starting Message Gateway server and gRPC service...");
        logger.info("WebSocket Port: {}", wsPort);
        logger.info("WebSocket Path: {}", wsPath);
        logger.info("WebSocket Idle Seconds: {}", idleSeconds);
        logger.info("gRPC Port: {}", grpcPort);

        DefaultOnlineUser onlineUser = new DefaultOnlineUser();

        GrpcServer grpcServer = new GrpcServer(grpcPort, onlineUser);
        WsService wsService =
                new WsService(
                        new WsConfig(wsPort, wsPath, idleSeconds),
                        new DefaultJwtDecoder(jwtSecret),
                        onlineUser);

        CompletableFuture<Void> grpcFuture = grpcServer.startAsync();
        CompletableFuture<Void> wsFuture = wsService.startAsync();
        CompletableFuture.allOf(grpcFuture, wsFuture).join();
    }

    public static void main(String[] args) {
        Application app = new Application();
        app.run();
    }
}
