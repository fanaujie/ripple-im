package com.fanaujie.ripple.webhookservice;

import com.fanaujie.ripple.communication.gateway.DirectGatewayPusher;
import com.fanaujie.ripple.communication.gateway.GatewayConnectionManager;
import com.fanaujie.ripple.communication.gateway.GatewayPusher;
import com.fanaujie.ripple.communication.grpc.client.GrpcClient;
import com.fanaujie.ripple.protobuf.userpresence.UserPresenceGrpc;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import com.fanaujie.ripple.storage.spi.RippleStorageLoader;
import com.fanaujie.ripple.webhookservice.http.WebhookHttpClient;
import com.fanaujie.ripple.webhookservice.server.WebhookDispatcherServiceImpl;
import com.fanaujie.ripple.webhookservice.service.WebhookDispatcherService;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    private Server server;
    private WebhookHttpClient httpClient;
    private GatewayConnectionManager gatewayConnectionManager;

    private void run() throws Exception {
        Config config = ConfigFactory.load();

        // gRPC Server Configuration
        int grpcPort = config.getInt("grpc.server.port");

        // ZooKeeper Configuration
        String zookeeperAddress = config.getString("zookeeper.address");
        String gatewayDiscoveryPath = config.getString("zookeeper.discovery.message-gateway");

        // User Presence Configuration
        String userPresenceAddress = config.getString("grpc.client.user-presence.address");

        // Webhook HTTP Configuration
        int connectTimeoutMs = config.getInt("webhook.http.connect-timeout-ms");
        int readTimeoutMs = config.getInt("webhook.http.read-timeout-ms");

        logger.info("Starting Webhook Service...");
        logger.info("gRPC Port: {}", grpcPort);
        logger.info("ZooKeeper: {}", zookeeperAddress);
        logger.info("Gateway Discovery Path: {}", gatewayDiscoveryPath);
        logger.info("User Presence Address: {}", userPresenceAddress);
        logger.info("HTTP Connect Timeout: {}ms, Read Timeout: {}ms", connectTimeoutMs, readTimeoutMs);

        // Initialize storage
        RippleStorageFacade storageFacade = RippleStorageLoader.load(System::getenv);

        // Initialize HTTP client
        httpClient = new WebhookHttpClient(connectTimeoutMs, readTimeoutMs);

        // Initialize user presence client
        GrpcClient<UserPresenceGrpc.UserPresenceBlockingStub> userPresenceClient =
                new GrpcClient<>(userPresenceAddress, UserPresenceGrpc::newBlockingStub);

        // Initialize gateway connection manager (shared from ripple-communication)
        gatewayConnectionManager = new GatewayConnectionManager(zookeeperAddress, gatewayDiscoveryPath);
        gatewayConnectionManager.start();

        // Initialize direct gateway pusher for low-latency SSE push
        GatewayPusher gatewayPusher = new DirectGatewayPusher(
                gatewayConnectionManager, userPresenceClient);

        // Initialize dispatcher service
        WebhookDispatcherService dispatcherService = new WebhookDispatcherService(
                httpClient,
                storageFacade,
                gatewayPusher);

        // Create gRPC service implementation
        WebhookDispatcherServiceImpl grpcService = new WebhookDispatcherServiceImpl(dispatcherService);

        // Start gRPC server
        server = ServerBuilder.forPort(grpcPort)
                .addService(grpcService)
                .addService(ProtoReflectionService.newInstance())
                .build()
                .start();

        logger.info("Webhook Service gRPC server started on port: {}", grpcPort);

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down Webhook Service...");
            try {
                stop();
            } catch (Exception e) {
                logger.error("Error during shutdown", e);
            }
        }));

        // Block until shutdown
        server.awaitTermination();
    }

    private void stop() throws Exception {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
            logger.info("gRPC server stopped");
        }
        if (httpClient != null) {
            httpClient.close();
        }
        if (gatewayConnectionManager != null) {
            gatewayConnectionManager.close();
            logger.info("Gateway connection manager closed");
        }
    }

    public static void main(String[] args) {
        Application app = new Application();
        try {
            app.run();
        } catch (Exception e) {
            logger.error("Application encountered an error: ", e);
        }
    }
}
