package com.fanaujie.ripple.msgapiserver;

import com.datastax.oss.driver.api.core.CqlSession;
import com.fanaujie.ripple.communication.msgqueue.GenericProducer;
import com.fanaujie.ripple.communication.msgqueue.kafka.KafkaGenericProducer;
import com.fanaujie.ripple.communication.msgqueue.kafka.KafkaProducerConfigFactory;
import com.fanaujie.ripple.communication.processor.ProcessorDispatcher;
import com.fanaujie.ripple.msgapiserver.processor.RelationEventProcessor;
import com.fanaujie.ripple.msgapiserver.processor.SelfInfoUpdateEventProcessor;
import com.fanaujie.ripple.communication.processor.DefaultProcessorDispatcher;
import com.fanaujie.ripple.msgapiserver.processor.SingleMessageContentProcessor;
import com.fanaujie.ripple.msgapiserver.server.GrpcServer;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventResp;
import com.fanaujie.ripple.protobuf.msgapiserver.SendMessageReq;
import com.fanaujie.ripple.protobuf.msgapiserver.SendMessageResp;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;
import com.fanaujie.ripple.protobuf.profileupdater.ProfileUpdatePayload;

import com.fanaujie.ripple.storage.driver.CassandraDriver;
import com.fanaujie.ripple.storage.driver.RedisDriver;
import com.fanaujie.ripple.storage.service.impl.cassandra.CassandraUserStorageFacade;
import com.fanaujie.ripple.storage.service.impl.cassandra.CassandraUserStorageFacadeBuilder;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
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
        String profileUpdateTopic = config.getString("broker.topic.profile-updates");
        String brokerServer = config.getString("broker.server");
        int executorQueueSize = config.getInt("ripple.executor.queue.capacity");
        int grpcPort = config.getInt("server.grpc.port");
        logger.info("Configuration - Redis Host: {}, Redis Port: {}", redisHost, redisPort);
        logger.info("Configuration - Cassandra Contact Points: {}", cassandraContacts);
        logger.info("Configuration - Cassandra Keyspace: {}", cassandraKeyspace);
        logger.info("Configuration - Cassandra Local Datacenter: {}", localDatacenter);
        logger.info("Configuration - Broker Server: {}", brokerServer);
        logger.info("Configuration - Broker Topic: {}", brokerTopic);
        logger.info("Configuration - Profile Update Topic: {}", profileUpdateTopic);
        logger.info("Configuration - Executor Queue Size: {}", executorQueueSize);
        logger.info("gRPC Port: {}", grpcPort);
        GenericProducer<String, MessagePayload> producer = createKafkaProducer(brokerServer);
        GenericProducer<String, ProfileUpdatePayload> profileUpdateProducer =
                createProfileUpdateProducer(brokerServer);

        ExecutorService executorService = createExecutorService(executorQueueSize);
        CqlSession cqlSession =
                CassandraDriver.createCqlSession(
                        cassandraContacts, cassandraKeyspace, localDatacenter);
        CassandraUserStorageFacadeBuilder userStorageFacadeBuilder =
                new CassandraUserStorageFacadeBuilder();
        userStorageFacadeBuilder
                .cqlSession(cqlSession)
                .redissonClient(RedisDriver.createRedissonClient(redisHost, redisPort));
        CassandraUserStorageFacade userStorageFacade = userStorageFacadeBuilder.build();
        GrpcServer grpcServer =
                new GrpcServer(
                        grpcPort,
                        createMessageDispatcher(
                                brokerTopic, userStorageFacade, producer, executorService),
                        createEventDispatcher(
                                brokerTopic,
                                profileUpdateTopic,
                                userStorageFacade,
                                producer,
                                profileUpdateProducer,
                                executorService));
        CompletableFuture<Void> grpcFuture = grpcServer.startAsync();
        logger.info("Starting Message Publisher server...");
        grpcFuture.join();
    }

    public static void main(String[] args) {
        Application app = new Application();
        app.run();
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

    private GenericProducer<String, ProfileUpdatePayload> createProfileUpdateProducer(
            String brokerServer) {
        return new KafkaGenericProducer<>(
                KafkaProducerConfigFactory.createProfileUpdatePayloadProducerConfig(brokerServer));
    }

    private ProcessorDispatcher<SendMessageReq.MessageCase, SendMessageReq, SendMessageResp>
            createMessageDispatcher(
                    String topic,
                    CassandraUserStorageFacade userStorageFacade,
                    GenericProducer<String, MessagePayload> producer,
                    ExecutorService executorService) {
        ProcessorDispatcher<SendMessageReq.MessageCase, SendMessageReq, SendMessageResp>
                messageDispatcher = new DefaultProcessorDispatcher<>();
        messageDispatcher.RegisterProcessor(
                SendMessageReq.MessageCase.SINGLE_MESSAGE_CONTENT,
                new SingleMessageContentProcessor(
                        topic, userStorageFacade, producer, executorService));
        return messageDispatcher;
    }

    private ProcessorDispatcher<SendEventReq.EventCase, SendEventReq, SendEventResp>
            createEventDispatcher(
                    String messageTopic,
                    String profileUpdateTopic,
                    CassandraUserStorageFacade userStorageFacade,
                    GenericProducer<String, MessagePayload> messageProducer,
                    GenericProducer<String, ProfileUpdatePayload> profileUpdateProducer,
                    ExecutorService executorService) {
        ProcessorDispatcher<SendEventReq.EventCase, SendEventReq, SendEventResp> eventDispatcher =
                new DefaultProcessorDispatcher<>();
        eventDispatcher.RegisterProcessor(
                SendEventReq.EventCase.SELF_INFO_UPDATE_EVENT,
                new SelfInfoUpdateEventProcessor(
                        messageTopic,
                        profileUpdateTopic,
                        userStorageFacade,
                        messageProducer,
                        profileUpdateProducer,
                        executorService));
        eventDispatcher.RegisterProcessor(
                SendEventReq.EventCase.RELATION_EVENT,
                new RelationEventProcessor(
                        messageTopic,
                        profileUpdateTopic,
                        messageProducer,
                        profileUpdateProducer,
                        executorService,
                        userStorageFacade));
        return eventDispatcher;
    }
}
