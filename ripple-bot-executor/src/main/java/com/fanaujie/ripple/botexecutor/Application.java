package com.fanaujie.ripple.botexecutor;

import com.datastax.oss.driver.api.core.CqlSession;
import com.fanaujie.ripple.botexecutor.config.BotExecutorConfig;
import com.fanaujie.ripple.botexecutor.consumer.BotMessageConsumer;
import com.fanaujie.ripple.botexecutor.consumer.BotPayloadHandler;
import com.fanaujie.ripple.botexecutor.processor.BotExecutorProcessor;
import com.fanaujie.ripple.botexecutor.service.PendingMessageService;
import com.fanaujie.ripple.communication.msgqueue.GenericProducer;
import com.fanaujie.ripple.communication.msgqueue.kafka.KafkaConsumerConfig;
import com.fanaujie.ripple.communication.msgqueue.kafka.KafkaGenericProducer;
import com.fanaujie.ripple.communication.msgqueue.kafka.KafkaProducerConfigFactory;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;
import com.fanaujie.ripple.protobuf.push.PushMessage;
import com.fanaujie.ripple.storage.driver.CassandraDriver;
import com.fanaujie.ripple.storage.service.impl.cassandra.CassandraStorageFacade;
import com.fanaujie.ripple.storage.service.impl.cassandra.CassandraStorageFacadeBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPooled;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    private void run() throws InterruptedException {
        Config config = ConfigFactory.load();

        // Cassandra config
        String contactPointsStr = config.getString("cassandra.contact-points");
        List<String> cassandraContacts = Arrays.stream(contactPointsStr.split(",")).map(String::trim).collect(Collectors.toList());
        String cassandraKeyspace = config.getString("cassandra.keyspace-name");
        String localDatacenter = config.getString("cassandra.local-datacenter");

        // Kafka config
        String brokerServer = config.getString("broker.server");
        String botTopic = config.getString("broker.topic.bot-messages");
        String messageTopic = config.getString("broker.topic.message");
        String pushTopic = config.getString("broker.topic.push-messages");
        String groupId = config.getString("ripple.topic.bot-executor.group.id");
        String clientId = config.getString("ripple.topic.bot-executor.client.id");

        int kafkaMaxPollRecords = config.getInt("kafka.consumer.max-poll-records");
        int kafkaFetchMinBytes = config.getInt("kafka.consumer.fetch-min-bytes");
        int kafkaFetchMaxWaitMs = config.getInt("kafka.consumer.fetch-max-wait-ms");

        // Worker pool config
        int workerCoreSize = config.getInt("bot-executor.worker.core-size");
        int workerMaxSize = config.getInt("bot-executor.worker.max-size");
        int workerQueueCapacity = config.getInt("bot-executor.worker.queue-capacity");

        // Redis config
        String redisHost = config.getString("redis.host");
        int redisPort = config.getInt("redis.port");

        // Bot executor config
        BotExecutorConfig botConfig = BotExecutorConfig.fromTypesafeConfig(config);

        logger.info("Starting Bot Executor Service...");
        logger.info("Cassandra: {}, Keyspace: {}", contactPointsStr, cassandraKeyspace);
        logger.info("Kafka: {}, Bot Topic: {}, Group ID: {}", brokerServer, botTopic, groupId);
        logger.info("Redis: {}:{}", redisHost, redisPort);

        // Initialize Cassandra
        CqlSession cqlSession = CassandraDriver.createCqlSession(cassandraContacts, cassandraKeyspace, localDatacenter);
        CassandraStorageFacade storageFacade = new CassandraStorageFacadeBuilder().cqlSession(cqlSession).build();

        // Initialize Redis
        JedisPooled jedis = new JedisPooled(redisHost, redisPort);
        ObjectMapper objectMapper = new ObjectMapper();
        PendingMessageService pendingMessageService = new PendingMessageService(jedis, objectMapper, botConfig.getPendingMessageTtlSeconds());

        // Initialize worker pool
        ExecutorService workerPool = new ThreadPoolExecutor(workerCoreSize, workerMaxSize, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(workerQueueCapacity));

        // Initialize Kafka producers
        GenericProducer<String, MessagePayload> messageProducer = new KafkaGenericProducer<>(
                KafkaProducerConfigFactory.createMessagePayloadProducerConfig(brokerServer));
        GenericProducer<String, PushMessage> pushProducer = new KafkaGenericProducer<>(
                KafkaProducerConfigFactory.createPushMessageProducerConfig(brokerServer));

        // Initialize processor
        BotExecutorProcessor botExecutorProcessor = new BotExecutorProcessor(
                storageFacade,
                messageProducer,
                pushProducer,
                messageTopic,
                pushTopic,
                workerPool,
                pendingMessageService,
                botConfig);
        BotPayloadHandler botPayloadHandler = new BotPayloadHandler(botExecutorProcessor);

        // Initialize Kafka consumer
        KafkaConsumerConfig consumerConfig = KafkaConsumerConfig.builder()
                .topic(botTopic)
                .bootstrapServers(brokerServer)
                .groupId(groupId)
                .clientId(clientId)
                .keyDeserializer("org.apache.kafka.common.serialization.StringDeserializer")
                .valueDeserializer("com.fanaujie.ripple.communication.msgqueue.kafka.deserializer.MessagePayloadDeserializer")
                .maxPollRecords(kafkaMaxPollRecords)
                .fetchMinBytes(kafkaFetchMinBytes)
                .fetchMaxWaitMs(kafkaFetchMaxWaitMs)
                .build();

        BotMessageConsumer botConsumer = new BotMessageConsumer(consumerConfig, workerPool, botPayloadHandler, workerQueueCapacity);

        Thread botConsumerThread = new Thread(botConsumer);
        botConsumerThread.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down bot executor service...");
            botConsumer.stop();
            workerPool.shutdown();
            jedis.close();
        }));

        botConsumerThread.join();
    }

    public static void main(String[] args) {
        Application app = new Application();
        try {
            app.run();
        } catch (Exception e) {
            logger.error("Bot Executor Service encountered an error", e);
        }
    }
}
