package com.fanaujie.ripple.storageupdater;

import com.fanaujie.ripple.cache.driver.RedisDriver;
import com.fanaujie.ripple.cache.service.impl.RedisUserProfileStorage;
import com.fanaujie.ripple.communication.msgqueue.GenericConsumer;
import com.fanaujie.ripple.communication.msgqueue.GenericProducer;
import com.fanaujie.ripple.communication.msgqueue.kafka.KafkaConsumerConfigFactory;
import com.fanaujie.ripple.communication.msgqueue.kafka.KafkaGenericConsumer;
import com.fanaujie.ripple.communication.msgqueue.kafka.KafkaGenericProducer;
import com.fanaujie.ripple.communication.msgqueue.kafka.KafkaProducerConfigFactory;
import com.fanaujie.ripple.communication.processor.DefaultProcessorDispatcher;
import com.fanaujie.ripple.communication.processor.ProcessorDispatcher;
import com.fanaujie.ripple.protobuf.push.PushMessage;
import com.fanaujie.ripple.storageupdater.consumer.StorageUpdateConsumer;

import com.fanaujie.ripple.storageupdater.consumer.processor.FriendStorageUpdatePayloadProcessor;
import com.fanaujie.ripple.storageupdater.consumer.processor.GroupInfoBatchUpdateProcessor;
import com.fanaujie.ripple.storageupdater.consumer.processor.GroupMemberBatchInsertProcessor;
import com.fanaujie.ripple.storageupdater.consumer.processor.UserGroupBatchUpdateProcessor;
import com.fanaujie.ripple.storageupdater.consumer.processor.RelationBatchUpdateProcessor;
import com.fanaujie.ripple.protobuf.storageupdater.StorageUpdatePayload;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import com.fanaujie.ripple.storage.spi.RippleStorageLoader;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    private void run() throws InterruptedException {
        Config config = ConfigFactory.load();
        String redisHost = config.getString("redis.host");
        int redisPort = config.getInt("redis.port");
        String storageUpdateTopic = config.getString("broker.topic.storage-updates");
        String pushTopic = config.getString("broker.topic.push");
        String brokerServer = config.getString("broker.server");
        String consumerGroupId = config.getString("ripple.topic.storage-updates.consumer.group.id");
        String consumerClientId =
                config.getString("ripple.topic.storage-updates.consumer.client.id");

        int kafkaMaxPollRecords = config.getInt("kafka.consumer.max-poll-records");
        int kafkaFetchMinBytes = config.getInt("kafka.consumer.fetch-min-bytes");
        int kafkaFetchMaxWaitMs = config.getInt("kafka.consumer.fetch-max-wait-ms");

        int processorThreadPoolSize = config.getInt("processor.thread.pool.size");
        logger.info("Configuration - Redis Host: {}, Redis Port: {}", redisHost, redisPort);
        logger.info("Configuration - Broker Server: {}", brokerServer);
        logger.info("Starting Storage Update Consumer...");
        logger.info("Storage Update Topic: {}", storageUpdateTopic);
        logger.info("Push Topic: {}", pushTopic);
        logger.info("Consumer Group ID: {}", consumerGroupId);
        logger.info("Consumer Client ID: {}", consumerClientId);

        RippleStorageFacade storageFacade = RippleStorageLoader.load(System::getenv);

        int cpuSize = Runtime.getRuntime().availableProcessors();
        ExecutorService executorService =
                createExecutorService(cpuSize, cpuSize * 2, processorThreadPoolSize);

        GenericProducer<String, PushMessage> pushMessageProducer =
                createPushMessageProducer(brokerServer);

        RedisUserProfileStorage userProfileCache =
                new RedisUserProfileStorage(
                        RedisDriver.createRedissonClient(redisHost, redisPort), storageFacade);

        StorageUpdateConsumer storageUpdateConsumer =
                new StorageUpdateConsumer(
                        createProcessorDispatcher(
                                storageFacade,
                                executorService,
                                pushMessageProducer,
                                pushTopic,
                                userProfileCache));

        GenericConsumer<String, StorageUpdatePayload> kafkaConsumer =
                createStorageUpdateTopicConsumer(
                        storageUpdateTopic,
                        brokerServer,
                        consumerGroupId,
                        consumerClientId,
                        kafkaMaxPollRecords,
                        kafkaFetchMinBytes,
                        kafkaFetchMaxWaitMs,
                        storageUpdateConsumer);

        Thread consumerThread = new Thread(kafkaConsumer);
        consumerThread.start();
        consumerThread.join();
    }

    public static void main(String[] args) {
        Application app = new Application();
        try {
            app.run();
        } catch (Exception e) {
            logger.error("Application encountered an error: ", e);
        }
    }

    private GenericConsumer<String, StorageUpdatePayload> createStorageUpdateTopicConsumer(
            String topic,
            String brokerServer,
            String groupId,
            String clientId,
            int maxPollRecords,
            int fetchMinBytes,
            int fetchMaxWaitMs,
            StorageUpdateConsumer storageUpdateConsumer) {
        GenericConsumer<String, StorageUpdatePayload> consumer =
                new KafkaGenericConsumer<>(
                        KafkaConsumerConfigFactory.createStorageUpdatePayloadConsumerConfig(
                                topic,
                                brokerServer,
                                groupId,
                                clientId,
                                maxPollRecords,
                                fetchMinBytes,
                                fetchMaxWaitMs));
        consumer.subscribe(storageUpdateConsumer::consumeBatch);
        return consumer;
    }

    private ProcessorDispatcher<StorageUpdatePayload.PayloadCase, StorageUpdatePayload, Void>
            createProcessorDispatcher(
                    RippleStorageFacade storageFacade,
                    ExecutorService executorService,
                    GenericProducer<String, PushMessage> pushMessageProducer,
                    String pushTopic,
                    RedisUserProfileStorage userProfileCache) {
        ProcessorDispatcher<StorageUpdatePayload.PayloadCase, StorageUpdatePayload, Void>
                processor = new DefaultProcessorDispatcher<>();
        processor.RegisterProcessor(
                StorageUpdatePayload.PayloadCase.FRIEND_STORAGE_UPDATE_DATA,
                new FriendStorageUpdatePayloadProcessor(storageFacade));
        processor.RegisterProcessor(
                StorageUpdatePayload.PayloadCase.RELATION_BATCH_UPDATE_DATA,
                new RelationBatchUpdateProcessor(storageFacade, executorService));
        processor.RegisterProcessor(
                StorageUpdatePayload.PayloadCase.USER_GROUP_BATCH_UPDATE_DATA,
                new UserGroupBatchUpdateProcessor(storageFacade, executorService));
        processor.RegisterProcessor(
                StorageUpdatePayload.PayloadCase.GROUP_MEMBER_BATCH_INSERT_DATA,
                new GroupMemberBatchInsertProcessor(
                        storageFacade, executorService, pushMessageProducer, pushTopic));
        processor.RegisterProcessor(
                StorageUpdatePayload.PayloadCase.GROUP_INFO_BATCH_UPDATE_DATA,
                new GroupInfoBatchUpdateProcessor(
                        storageFacade, executorService, pushMessageProducer, pushTopic));
        return processor;
    }

    private ExecutorService createExecutorService(int coreSize, int maxSize, int queueCapacity) {
        return new ThreadPoolExecutor(
                coreSize, maxSize, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(queueCapacity));
    }

    private GenericProducer<String, PushMessage> createPushMessageProducer(String brokerServer) {
        return new KafkaGenericProducer<>(
                KafkaProducerConfigFactory.createPushMessageProducerConfig(brokerServer));
    }
}
