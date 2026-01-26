package com.fanaujie.ripple.pushserver;

import com.fanaujie.ripple.cache.driver.RedisDriver;
import com.fanaujie.ripple.cache.service.ConversationSummaryStorage;
import com.fanaujie.ripple.cache.service.impl.RedisConversationSummaryStorage;
import com.fanaujie.ripple.communication.batch.Config;
import com.fanaujie.ripple.communication.gateway.GatewayConnectionManager;
import com.fanaujie.ripple.communication.grpc.client.GrpcClient;
import com.fanaujie.ripple.communication.msgqueue.GenericConsumer;
import com.fanaujie.ripple.communication.msgqueue.kafka.KafkaConsumerConfigFactory;
import com.fanaujie.ripple.communication.msgqueue.kafka.KafkaGenericConsumer;
import com.fanaujie.ripple.protobuf.push.PushMessage;
import com.fanaujie.ripple.protobuf.userpresence.UserPresenceGrpc;
import com.fanaujie.ripple.pushserver.service.PushService;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import com.fanaujie.ripple.storage.spi.RippleStorageLoader;
import com.typesafe.config.ConfigFactory;
import org.redisson.api.RedissonClient;
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
        int zookeeperSessionTimeoutMs = config.getInt("zookeeper.session-timeout-ms");
        int zookeeperConnectionTimeoutMs = config.getInt("zookeeper.connection-timeout-ms");

        // Load batch executor configuration
        int batchQueueSize = config.getInt("batch.executor.queue-size");
        int batchWorkerSize = config.getInt("batch.executor.worker-size");
        int batchMaxSize = config.getInt("batch.executor.max-size");
        long batchTimeoutMs = config.getLong("batch.executor.timeout-ms");

        // Load Kafka consumer batch configuration
        int kafkaMaxPollRecords = config.getInt("kafka.consumer.max-poll-records");
        int kafkaFetchMinBytes = config.getInt("kafka.consumer.fetch-min-bytes");
        int kafkaFetchMaxWaitMs = config.getInt("kafka.consumer.fetch-max-wait-ms");

        // Load Redis configuration
        String redisHost = config.getString("redis.host");
        int redisPort = config.getInt("redis.port");

        logger.info("Broker Config - Server: {}, Push Topic: {}", brokerServer, pushTopic);
        logger.info(
                "Ripple Config - Push Group ID: {}, Push Client ID: {}",
                topicPushGroupId,
                topicPushClientId);
        logger.info("User Presence Server: {}", userPresenceServer);
        logger.info(
                "Zookeeper Config - Address: {}, Discovery Path: {}, Session Timeout: {}ms, Connection Timeout: {}ms",
                zookeeperAddress,
                messageGatewayDiscoveryPath,
                zookeeperSessionTimeoutMs,
                zookeeperConnectionTimeoutMs);
        logger.info(
                "Batch Executor Config - Queue Size: {}, Worker Size: {}, Max Size: {}, Timeout (ms): {}",
                batchQueueSize,
                batchWorkerSize,
                batchMaxSize,
                batchTimeoutMs);
        logger.info(
                "Kafka Consumer Batch Config - Max Poll Records: {}, Fetch Min Bytes: {}, Fetch Max Wait (ms): {}",
                kafkaMaxPollRecords,
                kafkaFetchMinBytes,
                kafkaFetchMaxWaitMs);
        logger.info("Redis Config - Host: {}, Port: {}", redisHost, redisPort);

        Config batchConfig =
                new Config(batchQueueSize, batchWorkerSize, batchMaxSize, batchTimeoutMs);

        GatewayConnectionManager gatewayConnectionManager = null;
        PushService pushService = null;
        RedissonClient redissonClient = null;
        ConversationSummaryStorage conversationStorage = null;
        try {
            redissonClient = RedisDriver.createRedissonClient(redisHost, redisPort);
            RippleStorageFacade storageFacade = RippleStorageLoader.load(System::getenv);
            conversationStorage =
                    new RedisConversationSummaryStorage(redissonClient, storageFacade);
            logger.info("ConversationStorage initialized successfully");
            gatewayConnectionManager =
                    new GatewayConnectionManager(
                            zookeeperAddress,
                            messageGatewayDiscoveryPath,
                            zookeeperSessionTimeoutMs,
                            zookeeperConnectionTimeoutMs);
            gatewayConnectionManager.start();
            logger.info("GatewayConnectionManager initialized with ZooKeeper connection monitoring");

            pushService =
                    createPushService(
                            userPresenceServer,
                            gatewayConnectionManager,
                            batchConfig,
                            conversationStorage);

            GenericConsumer<String, PushMessage> pushTopicConsumer =
                    createPushTopicConsumer(
                            pushTopic,
                            brokerServer,
                            topicPushGroupId,
                            topicPushClientId,
                            kafkaMaxPollRecords,
                            kafkaFetchMinBytes,
                            kafkaFetchMaxWaitMs,
                            pushService);
            Thread pushConsumerThread = new Thread(pushTopicConsumer);
            pushConsumerThread.start();

            // Add shutdown hook for graceful cleanup
            final GatewayConnectionManager finalConnectionManager = gatewayConnectionManager;
            final PushService finalPushService = pushService;
            final RedissonClient finalRedissonClient = redissonClient;
            Runtime.getRuntime()
                    .addShutdownHook(
                            new Thread(
                                    () -> {
                                        logger.info("Shutting down application...");
                                        try {
                                            finalPushService.close();
                                            finalConnectionManager.close();
                                            if (finalRedissonClient != null) {
                                                finalRedissonClient.shutdown();
                                            }
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
                if (gatewayConnectionManager != null) {
                    gatewayConnectionManager.close();
                }
                if (redissonClient != null) {
                    redissonClient.shutdown();
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
            GatewayConnectionManager gatewayConnectionManager,
            Config batchConfig,
            ConversationSummaryStorage conversationStorage) {
        GrpcClient<UserPresenceGrpc.UserPresenceBlockingStub> userPresenceClient =
                new GrpcClient<>(userPresenceServer, UserPresenceGrpc::newBlockingStub);
        return new PushService(
                userPresenceClient, gatewayConnectionManager, batchConfig, conversationStorage);
    }

    private GenericConsumer<String, PushMessage> createPushTopicConsumer(
            String topic,
            String brokerServer,
            String groupId,
            String clientId,
            int maxPollRecords,
            int fetchMinBytes,
            int fetchMaxWaitMs,
            PushService pushService) {
        GenericConsumer<String, PushMessage> consumer =
                new KafkaGenericConsumer<>(
                        KafkaConsumerConfigFactory.createPushMessageConsumerConfig(
                                topic,
                                brokerServer,
                                groupId,
                                clientId,
                                maxPollRecords,
                                fetchMinBytes,
                                fetchMaxWaitMs));
        consumer.subscribe(pushService::processPushMessageBatch);
        return consumer;
    }
}
