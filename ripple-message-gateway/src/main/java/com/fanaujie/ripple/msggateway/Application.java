package com.fanaujie.ripple.msggateway;

import com.fanaujie.ripple.communication.batch.BatchExecutorService;
import com.fanaujie.ripple.communication.grpc.client.GrpcClient;
import com.fanaujie.ripple.communication.zookeeper.ZookeeperDiscoverService;
import com.fanaujie.ripple.msggateway.batch.UserOnlineBatchProcessorFactory;
import com.fanaujie.ripple.msggateway.batch.UserOnlineBatchTask;
import com.fanaujie.ripple.msggateway.server.grpc.GrpcServer;
import com.fanaujie.ripple.msggateway.server.jwt.DefaultJwtDecoder;
import com.fanaujie.ripple.msggateway.server.users.DefaultOnlineUser;
import com.fanaujie.ripple.msggateway.server.users.DefaultUserNotifier;
import com.fanaujie.ripple.msggateway.server.ws.WsService;
import com.fanaujie.ripple.msggateway.server.ws.config.WsConfig;
import com.fanaujie.ripple.protobuf.userpresence.UserPresenceGrpc;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.CompletableFuture;

public class Application {

    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    public void run() throws Exception {
        Config config = ConfigFactory.load();
        int wsPort = config.getInt("server.websocket.port");
        String wsPath = config.getString("server.websocket.path");
        int idleSeconds = config.getInt("ripple.heartbeat.idleSeconds");
        int grpcPort = config.getInt("server.grpc.port");
        String jwtSecret = config.getString("oauth2.jwk.secret");
        String userPresenceServer = config.getString("server.user-presence.address");
        String zookeeperAddress = config.getString("zookeeper.address");
        String discoveryPath = config.getString("zookeeper.message-gateway.discovery-path");
        int zookeeperSessionTimeoutMs = config.getInt("zookeeper.session-timeout-ms");
        int zookeeperConnectionTimeoutMs = config.getInt("zookeeper.connection-timeout-ms");

        // Read batch configuration
        int batchQueueSize = config.getInt("batch.user-online.queue-size");
        int batchWorkerSize = config.getInt("batch.user-online.worker-size");
        int batchMaxSize = config.getInt("batch.user-online.max-size");
        long batchTimeoutMs = config.getLong("batch.user-online.timeout-ms");

        logger.info("Starting Message Gateway server and gRPC service...");
        logger.info("WebSocket Port: {}", wsPort);
        logger.info("WebSocket Path: {}", wsPath);
        logger.info("WebSocket Idle Seconds: {}", idleSeconds);
        logger.info("gRPC Port: {}", grpcPort);
        logger.info("User Presence Server: {}", userPresenceServer);
        logger.info(
                "User Online Batch Config - queueSize: {}, workerSize: {}, maxSize: {}, timeoutMs: {}",
                batchQueueSize,
                batchWorkerSize,
                batchMaxSize,
                batchTimeoutMs);
        logger.info(
                "Zookeeper Config - Address: {}, Discovery Path: {}, Session Timeout: {}ms, Connection Timeout: {}ms",
                zookeeperAddress,
                discoveryPath,
                zookeeperSessionTimeoutMs,
                zookeeperConnectionTimeoutMs);

        ZookeeperDiscoverService discoveryService =
                new ZookeeperDiscoverService(
                        zookeeperAddress,
                        discoveryPath,
                        zookeeperSessionTimeoutMs,
                        zookeeperConnectionTimeoutMs);

        // Use GATEWAY_GRPC_ADDRESS if configured (K8s StatefulSet), otherwise fallback to local IP
        String configuredGrpcAddress = config.getString("server.gateway.grpc-address");
        String serverLocation;
        if (configuredGrpcAddress != null && !configuredGrpcAddress.isEmpty()) {
            serverLocation = configuredGrpcAddress;
            logger.info("Using configured gateway gRPC address: {}", serverLocation);
        } else {
            String localIp = InetAddress.getLocalHost().getHostAddress();
            serverLocation = String.format("%s:%d", localIp, grpcPort);
            logger.info("Using local IP for gateway gRPC address: {}", serverLocation);
        }
        discoveryService.registerService(serverLocation);

        // Initialize User Presence gRPC client (async stub for batch processing)
        GrpcClient<UserPresenceGrpc.UserPresenceStub> userPresenceGrpcClient =
                new GrpcClient<>(userPresenceServer, UserPresenceGrpc::newStub);

        // Initialize BatchExecutorService for user online status updates
        com.fanaujie.ripple.communication.batch.Config batchConfig =
                new com.fanaujie.ripple.communication.batch.Config(
                        batchQueueSize, batchWorkerSize, batchMaxSize, batchTimeoutMs);
        UserOnlineBatchProcessorFactory processorFactory =
                new UserOnlineBatchProcessorFactory(userPresenceGrpcClient);
        BatchExecutorService<UserOnlineBatchTask> batchExecutorService =
                new BatchExecutorService<>(batchConfig, processorFactory);
        logger.info("User Online BatchExecutorService initialized successfully with async stub");

        // Initialize services with batch executor
        DefaultOnlineUser onlineUser = new DefaultOnlineUser(grpcPort, batchExecutorService);
        DefaultUserNotifier userNotifier = new DefaultUserNotifier();
        GrpcServer grpcServer = new GrpcServer(grpcPort, onlineUser, userNotifier);
        WsService wsService =
                new WsService(
                        new WsConfig(wsPort, wsPath, idleSeconds),
                        new DefaultJwtDecoder(jwtSecret),
                        onlineUser,
                        batchExecutorService,
                        serverLocation);

        CompletableFuture<Void> grpcFuture = grpcServer.startAsync();
        CompletableFuture<Void> wsFuture = wsService.startAsync();
        CompletableFuture.allOf(grpcFuture, wsFuture).join();
    }

    public static void main(String[] args) {
        Application app = new Application();
        try {
            app.run();
        } catch (Exception e) {
            logger.error("Failed to start Application: {}", e.getMessage(), e);
        }
    }
}
