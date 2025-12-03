package com.fanaujie.ripple.profileupdater;

import com.datastax.oss.driver.api.core.CqlSession;
import com.fanaujie.ripple.communication.msgqueue.GenericConsumer;
import com.fanaujie.ripple.communication.msgqueue.kafka.KafkaConsumerConfigFactory;
import com.fanaujie.ripple.communication.msgqueue.kafka.KafkaGenericConsumer;
import com.fanaujie.ripple.communication.processor.DefaultProcessorDispatcher;
import com.fanaujie.ripple.communication.processor.ProcessorDispatcher;
import com.fanaujie.ripple.profileupdater.consumer.ProfileUpdateConsumer;

import com.fanaujie.ripple.profileupdater.consumer.processor.FriendProfileUpdatePayloadProcessor;
import com.fanaujie.ripple.profileupdater.consumer.processor.GroupMemberProfileUpdatePayloadProcessor;
import com.fanaujie.ripple.profileupdater.consumer.processor.RelationProfileUpdatePayloadProcessor;
import com.fanaujie.ripple.protobuf.profileupdater.ProfileUpdatePayload;
import com.fanaujie.ripple.storage.driver.CassandraDriver;
import com.fanaujie.ripple.storage.driver.RedisDriver;
import com.fanaujie.ripple.storage.service.impl.cassandra.CassandraUserStorageFacade;
import com.fanaujie.ripple.storage.service.impl.cassandra.CassandraUserStorageFacadeBuilder;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
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
        List<String> cassandraContacts = config.getStringList("cassandra.contact.points");
        String cassandraKeyspace = config.getString("cassandra.keyspace.name");
        String localDatacenter = config.getString("cassandra.local.datacenter");

        String profileUpdateTopic = config.getString("broker.topic.profile-updates");
        String brokerServer = config.getString("broker.server");
        String consumerGroupId = config.getString("ripple.topic.profile-updates.consumer.group.id");
        String consumerClientId =
                config.getString("ripple.topic.profile-updates.consumer.client.id");

        int kafkaMaxPollRecords = config.getInt("kafka.consumer.max-poll-records");
        int kafkaFetchMinBytes = config.getInt("kafka.consumer.fetch-min-bytes");
        int kafkaFetchMaxWaitMs = config.getInt("kafka.consumer.fetch-max-wait-ms");

        int processorThreadPoolSize = config.getInt("processor.thread.pool.size");
        logger.info("Configuration - Redis Host: {}, Redis Port: {}", redisHost, redisPort);
        logger.info("Configuration - Broker Server: {}", brokerServer);
        logger.info("Starting Profile Update Consumer...");
        logger.info("Profile Update Topic: {}", profileUpdateTopic);
        logger.info("Consumer Group ID: {}", consumerGroupId);
        logger.info("Consumer Client ID: {}", consumerClientId);

        CqlSession cqlSession =
                createCQLSession(cassandraContacts, cassandraKeyspace, localDatacenter);

        CassandraUserStorageFacadeBuilder storageBuilder = new CassandraUserStorageFacadeBuilder();
        storageBuilder.cqlSession(cqlSession);
        storageBuilder.redissonClient(RedisDriver.createRedissonClient(redisHost, redisPort));
        CassandraUserStorageFacade storageFacade = storageBuilder.build();
        int cpuSize = Runtime.getRuntime().availableProcessors();
        ExecutorService executorService =
                createExecutorService(cpuSize, cpuSize * 2, processorThreadPoolSize);

        ProfileUpdateConsumer profileUpdateConsumer =
                new ProfileUpdateConsumer(
                        createProcessorDispatcher(storageFacade, executorService));

        GenericConsumer<String, ProfileUpdatePayload> kafkaConsumer =
                createProfileUpdateTopicConsumer(
                        profileUpdateTopic,
                        brokerServer,
                        consumerGroupId,
                        consumerClientId,
                        kafkaMaxPollRecords,
                        kafkaFetchMinBytes,
                        kafkaFetchMaxWaitMs,
                        profileUpdateConsumer);

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

    private GenericConsumer<String, ProfileUpdatePayload> createProfileUpdateTopicConsumer(
            String topic,
            String brokerServer,
            String groupId,
            String clientId,
            int maxPollRecords,
            int fetchMinBytes,
            int fetchMaxWaitMs,
            ProfileUpdateConsumer profileUpdateConsumer) {
        GenericConsumer<String, ProfileUpdatePayload> consumer =
                new KafkaGenericConsumer<>(
                        KafkaConsumerConfigFactory.createProfileUpdatePayloadConsumerConfig(
                                topic,
                                brokerServer,
                                groupId,
                                clientId,
                                maxPollRecords,
                                fetchMinBytes,
                                fetchMaxWaitMs));
        consumer.subscribe(profileUpdateConsumer::consumeBatch);
        return consumer;
    }

    private ProcessorDispatcher<ProfileUpdatePayload.PayloadCase, ProfileUpdatePayload, Void>
            createProcessorDispatcher(
                    CassandraUserStorageFacade storageFacade, ExecutorService executorService) {
        ProcessorDispatcher<ProfileUpdatePayload.PayloadCase, ProfileUpdatePayload, Void>
                processor = new DefaultProcessorDispatcher<>();
        processor.RegisterProcessor(
                ProfileUpdatePayload.PayloadCase.FRIEND_PROFILE_UPDATE_DATA,
                new FriendProfileUpdatePayloadProcessor(storageFacade, executorService));
        processor.RegisterProcessor(
                ProfileUpdatePayload.PayloadCase.RELATION_PROFILE_UPDATE_DATA,
                new RelationProfileUpdatePayloadProcessor(storageFacade, executorService));
        processor.RegisterProcessor(
                ProfileUpdatePayload.PayloadCase.GROUP_MEMBER_PROFILE_UPDATE_DATA,
                new GroupMemberProfileUpdatePayloadProcessor(storageFacade, executorService));
        return processor;
    }

    private CqlSession createCQLSession(
            List<String> cassandraContacts, String cassandraKeyspace, String localDatacenter) {
        return CassandraDriver.createCqlSession(
                cassandraContacts, cassandraKeyspace, localDatacenter);
    }

    private ExecutorService createExecutorService(int coreSize, int maxSize, int queueCapacity) {
        return new ThreadPoolExecutor(
                coreSize, maxSize, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(queueCapacity));
    }
}
