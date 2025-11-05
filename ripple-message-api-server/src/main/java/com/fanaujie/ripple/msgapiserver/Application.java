package com.fanaujie.ripple.msgapiserver;

import com.datastax.oss.driver.api.core.CqlSession;
import com.fanaujie.ripple.communication.msgqueue.GenericProducer;
import com.fanaujie.ripple.communication.msgqueue.kafka.KafkaGenericProducer;
import com.fanaujie.ripple.communication.msgqueue.kafka.KafkaProducerConfigFactory;
import com.fanaujie.ripple.communication.processor.ProcessorDispatcher;
import com.fanaujie.ripple.msgapiserver.processor.RelationEventProcessor;
import com.fanaujie.ripple.msgapiserver.processor.SelfInfoUpdateEventProcessor;
import com.fanaujie.ripple.msgapiserver.processor.DefaultSendEventProcessorDispatcher;
import com.fanaujie.ripple.msgapiserver.server.GrpcServer;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventResp;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;
import com.fanaujie.ripple.storage.cache.KvCache;
import com.fanaujie.ripple.storage.cache.impl.RedissonKvCache;
import com.fanaujie.ripple.storage.driver.CassandraDriver;
import com.fanaujie.ripple.storage.driver.RedisDriver;
import com.fanaujie.ripple.storage.repository.RelationRepository;
import com.fanaujie.ripple.storage.repository.UserRepository;
import com.fanaujie.ripple.storage.repository.impl.CassandraRelationRepository;
import com.fanaujie.ripple.storage.repository.impl.CassandraUserRepository;
import com.fanaujie.ripple.storage.service.CachedRelationStorage;
import com.fanaujie.ripple.storage.service.impl.DefaultRelationStorage;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.*;

public class Application {

    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    public void run() {
        Config config = ConfigFactory.load();
        String redisHost = config.getString("redis.host");
        int redisPort = config.getInt("redis.port");
        List<String> cassandraContacts = config.getStringList("cassandra.contact.points");
        String cassandraKeyspace = config.getString("cassandra.keyspace.name");
        String localDatacenter = config.getString("cassandra.local.datacenter");
        String brokerTopic = config.getString("broker.topic.message");
        String brokerServer = config.getString("broker.server");
        int executorQueueSize = config.getInt("ripple.executor.queue.capacity");
        int grpcPort = config.getInt("server.grpc.port");
        logger.info("Configuration - Redis Host: {}, Redis Port: {}", redisHost, redisPort);
        logger.info("Configuration - Cassandra Contact Points: {}", cassandraContacts);
        logger.info("Configuration - Cassandra Keyspace: {}", cassandraKeyspace);
        logger.info("Configuration - Cassandra Local Datacenter: {}", localDatacenter);
        logger.info("Configuration - Broker Server: {}", brokerServer);
        logger.info("Configuration - Broker Topic: {}", brokerTopic);
        logger.info("Configuration - Executor Queue Size: {}", executorQueueSize);
        logger.info("gRPC Port: {}", grpcPort);
        GenericProducer<String, MessagePayload> producer = createKafkaProducer(brokerServer);

        ExecutorService executorService = createExecutorService(executorQueueSize);
        CqlSession cqlSession =
                CassandraDriver.createCqlSession(
                        cassandraContacts, cassandraKeyspace, localDatacenter);
        CachedRelationStorage cachedRelationStorage =
                createRelationStorageService(redisHost, redisPort, cqlSession);
        UserRepository userRepository = new CassandraUserRepository(cqlSession);
        RelationRepository relationRepository = new CassandraRelationRepository(cqlSession);
        GrpcServer grpcServer =
                new GrpcServer(
                        grpcPort,
                        null,
                        createEventProcessor(
                                brokerTopic,
                                userRepository,
                                relationRepository,
                                cachedRelationStorage,
                                producer,
                                executorService));
        CompletableFuture<Void> grpcFuture = grpcServer.startAsync();
        logger.info("Starting Message Publisher server...");
        grpcFuture.join();
    }

    public static void main(String[] args) {
        Application app = new Application();
        app.run();
    }

    private DefaultRelationStorage createRelationStorageService(
            String redisHost, int redisPort, CqlSession cqlSession) {
        RelationRepository relationRepository = new CassandraRelationRepository(cqlSession);
        RedissonClient redissonClient = RedisDriver.createRedissonClient(redisHost, redisPort);
        KvCache kvCache = new RedissonKvCache(redissonClient);
        return new DefaultRelationStorage(kvCache, relationRepository);
    }

    private ExecutorService createExecutorService(int queueCapacity) {
        int cpuCoreSize = Runtime.getRuntime().availableProcessors();
        return new ThreadPoolExecutor(
                cpuCoreSize,
                cpuCoreSize * 2,
                60,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity));
    }

    private GenericProducer<String, MessagePayload> createKafkaProducer(String brokerServer) {
        return new KafkaGenericProducer<>(
                KafkaProducerConfigFactory.createMessagePayloadProducerConfig(brokerServer));
    }

    private ProcessorDispatcher<SendEventReq.EventCase, SendEventReq, SendEventResp>
            createEventProcessor(
                    String topic,
                    UserRepository userRepository,
                    RelationRepository relationRepository,
                    CachedRelationStorage relationStorage,
                    GenericProducer<String, MessagePayload> producer,
                    ExecutorService executorService) {
        ProcessorDispatcher<SendEventReq.EventCase, SendEventReq, SendEventResp> eventProcessor =
                new DefaultSendEventProcessorDispatcher();
        eventProcessor.RegisterProcessor(
                SendEventReq.EventCase.SELF_INFO_UPDATE_EVENT,
                new SelfInfoUpdateEventProcessor(
                        topic, relationStorage, producer, executorService));
        eventProcessor.RegisterProcessor(
                SendEventReq.EventCase.RELATION_EVENT,
                new RelationEventProcessor(
                        topic, producer, executorService, userRepository, relationRepository));
        return eventProcessor;
    }
}
