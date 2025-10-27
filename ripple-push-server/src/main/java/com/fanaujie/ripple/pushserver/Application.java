package com.fanaujie.ripple.pushserver;

import com.fanaujie.ripple.communication.batch.Config;
import com.fanaujie.ripple.communication.grpc.client.GrpcClient;
import com.fanaujie.ripple.communication.messaging.GenericConsumer;
import com.fanaujie.ripple.communication.messaging.kafka.KafkaConsumerConfigFactory;
import com.fanaujie.ripple.communication.messaging.kafka.KafkaGenericConsumer;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;
import com.fanaujie.ripple.protobuf.userpresence.UserPresenceGrpc;
import com.fanaujie.ripple.pushserver.service.grpc.MessageGatewayClientManager;
import com.fanaujie.ripple.pushserver.service.PushService;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Application {

    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    private void run() {
        com.typesafe.config.Config config = ConfigFactory.load();
        String pushTopic = config.getString("broker.topic.push");
        String brokerServer = config.getString("broker.server");
        String topicPushGroupId = config.getString("ripple.topic.push.consumer.group.id");
        String topicPushClientId = config.getString("ripple.topic.push.consumer.client.id");
        String userPresenceServer = config.getString("server.user-presence.address");
        String zookeeperAddress = config.getString("zookeeper.address");
        String messageGatewayDiscoveryPath =
                config.getString("zookeeper.message-gateway.discovery-path");

        // Load batch executor configuration
        int batchQueueSize = config.getInt("batch.executor.queue-size");
        int batchWorkerSize = config.getInt("batch.executor.worker-size");
        int batchMaxSize = config.getInt("batch.executor.max-size");
        long batchTimeoutMs = config.getLong("batch.executor.timeout-ms");
        logger.info("Broker Config - Server: {}, Push Topic: {}", brokerServer, pushTopic);
        logger.info(
                "Ripple Config - Push Group ID: {}, Push Client ID: {}",
                topicPushGroupId,
                topicPushClientId);
        logger.info("User Presence Server: {}", userPresenceServer);
        logger.info(
                "Zookeeper Address: {}, Message Gateway Discovery Path: {}",
                zookeeperAddress,
                messageGatewayDiscoveryPath);
        logger.info(
                "Batch Executor Config - Queue Size: {}, Worker Size: {}, Max Size: {}, Timeout (ms): {}",
                batchQueueSize,
                batchWorkerSize,
                batchMaxSize,
                batchTimeoutMs);

        Config batchConfig =
                new Config(batchQueueSize, batchWorkerSize, batchMaxSize, batchTimeoutMs);

        MessageGatewayClientManager messageGatewayManager = null;
        PushService pushService = null;
        try {
            // Initialize MessageGatewayClientManager
            messageGatewayManager =
                    new MessageGatewayClientManager(zookeeperAddress, messageGatewayDiscoveryPath);
            messageGatewayManager.start();
            logger.info("MessageGatewayClientManager initialized successfully");

            pushService = createPushService(userPresenceServer, messageGatewayManager, batchConfig);

            GenericConsumer<String, MessagePayload> pushTopicConsumer =
                    createPushTopicConsumer(
                            pushTopic,
                            brokerServer,
                            topicPushGroupId,
                            topicPushClientId,
                            pushService);
            Thread pushConsumerThread = new Thread(pushTopicConsumer);
            pushConsumerThread.start();

            // Add shutdown hook for graceful cleanup
            final MessageGatewayClientManager finalManager = messageGatewayManager;
            final PushService finalPushService = pushService;
            Runtime.getRuntime()
                    .addShutdownHook(
                            new Thread(
                                    () -> {
                                        logger.info("Shutting down application...");
                                        try {
                                            finalPushService.close();
                                            finalManager.close();
                                        } catch (Exception e) {
                                            logger.error("Error during shutdown cleanup", e);
                                        }
                                    }));

            pushConsumerThread.join();
        } catch (InterruptedException e) {
            logger.error("Push consumer thread interrupted", e);
        } catch (Exception e) {
            logger.error("Error initializing application", e);
            try {
                if (pushService != null) {
                    pushService.close();
                }
                if (messageGatewayManager != null) {
                    messageGatewayManager.close();
                }
            } catch (Exception closeException) {
                logger.error("Error closing", closeException);
            }
        }
    }

    public static void main(String[] args) {
        Application app = new Application();
        app.run();
    }

    private PushService createPushService(
            String userPresenceServer,
            MessageGatewayClientManager messageGatewayManager,
            Config batchConfig) {
        GrpcClient<UserPresenceGrpc.UserPresenceBlockingStub> userPresenceClient =
                new GrpcClient<>(userPresenceServer, UserPresenceGrpc::newBlockingStub);
        return new PushService(userPresenceClient, messageGatewayManager, batchConfig);
    }

    private GenericConsumer<String, MessagePayload> createPushTopicConsumer(
            String topic,
            String brokerServer,
            String groupId,
            String clientId,
            PushService pushService) {
        GenericConsumer<String, MessagePayload> consumer =
                new KafkaGenericConsumer<>(
                        KafkaConsumerConfigFactory.createMessagePayloadConsumerConfig(
                                topic, brokerServer, groupId, clientId));
        consumer.subscribe(pushService::processMessagePayload);
        return consumer;
    }
}
