package com.fanaujie.ripple.webhookservice;

import com.fanaujie.ripple.communication.msgqueue.GenericProducer;
import com.fanaujie.ripple.communication.msgqueue.kafka.KafkaGenericProducer;
import com.fanaujie.ripple.communication.msgqueue.kafka.KafkaProducerConfigFactory;
import com.fanaujie.ripple.protobuf.push.PushMessage;
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

    private void run() throws Exception {
        Config config = ConfigFactory.load();

        // gRPC Server Configuration
        int grpcPort = config.getInt("grpc.server.port");

        // Kafka Configuration
        String pushTopic = config.getString("broker.topic.push");
        String brokerServer = config.getString("broker.server");

        // Webhook HTTP Configuration
        int connectTimeoutMs = config.getInt("webhook.http.connect-timeout-ms");
        int readTimeoutMs = config.getInt("webhook.http.read-timeout-ms");

        logger.info("Starting Webhook Service...");
        logger.info("gRPC Port: {}", grpcPort);
        logger.info("Push Topic: {}", pushTopic);
        logger.info("Broker Server: {}", brokerServer);
        logger.info("HTTP Connect Timeout: {}ms, Read Timeout: {}ms", connectTimeoutMs, readTimeoutMs);

        // Initialize storage
        RippleStorageFacade storageFacade = RippleStorageLoader.load(System::getenv);

        // Initialize HTTP client
        httpClient = new WebhookHttpClient(connectTimeoutMs, readTimeoutMs);

        // Initialize push producer
        GenericProducer<String, PushMessage> pushProducer = new KafkaGenericProducer<>(
                KafkaProducerConfigFactory.createPushMessageProducerConfig(brokerServer));

        // Initialize dispatcher service
        WebhookDispatcherService dispatcherService = new WebhookDispatcherService(
                httpClient,
                storageFacade,
                pushProducer,
                pushTopic);

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
            } catch (InterruptedException e) {
                logger.error("Error during shutdown", e);
                Thread.currentThread().interrupt();
            }
        }));

        // Block until shutdown
        server.awaitTermination();
    }

    private void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
            logger.info("gRPC server stopped");
        }
        if (httpClient != null) {
            httpClient.close();
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
