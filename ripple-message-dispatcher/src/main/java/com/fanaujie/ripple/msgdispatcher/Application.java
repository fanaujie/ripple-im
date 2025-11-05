package com.fanaujie.ripple.msgdispatcher;

import com.datastax.oss.driver.api.core.CqlSession;
import com.fanaujie.ripple.communication.msgqueue.GenericConsumer;
import com.fanaujie.ripple.communication.msgqueue.GenericProducer;
import com.fanaujie.ripple.communication.msgqueue.kafka.KafkaConsumerConfigFactory;
import com.fanaujie.ripple.communication.msgqueue.kafka.KafkaGenericConsumer;
import com.fanaujie.ripple.communication.msgqueue.kafka.KafkaGenericProducer;
import com.fanaujie.ripple.communication.msgqueue.kafka.KafkaProducerConfigFactory;
import com.fanaujie.ripple.communication.processor.ProcessorDispatcher;
import com.fanaujie.ripple.msgdispatcher.consumer.MessageConsumer;
import com.fanaujie.ripple.msgdispatcher.consumer.DefaultEventPayloadRouter;
import com.fanaujie.ripple.msgdispatcher.consumer.processor.DefaultEventPayloadProcessorDispatcher;
import com.fanaujie.ripple.msgdispatcher.consumer.processor.RelationUpdateEventPayloadProcessor;
import com.fanaujie.ripple.msgdispatcher.consumer.processor.SelfInfoUpdateEventPayloadProcessor;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq;
import com.fanaujie.ripple.protobuf.msgdispatcher.EventData;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;
import com.fanaujie.ripple.protobuf.push.PushMessage;
import com.fanaujie.ripple.storage.driver.CassandraDriver;
import com.fanaujie.ripple.storage.repository.RelationRepository;
import com.fanaujie.ripple.storage.repository.UserRepository;
import com.fanaujie.ripple.storage.repository.impl.CassandraRelationRepository;
import com.fanaujie.ripple.storage.repository.impl.CassandraUserRepository;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
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
        List<String> cassandraContacts = config.getStringList("cassandra.contact.points");
        String cassandraKeyspace = config.getString("cassandra.keyspace.name");
        String localDatacenter = config.getString("cassandra.local.datacenter");
        String messageTopic = config.getString("broker.topic.message");
        String brokerServer = config.getString("broker.server");
        String topicMessageGroupId = config.getString("ripple.topic.message.consumer.group.id");
        String topicMessageClientId = config.getString("ripple.topic.message.consumer.client.id");
        String pushTopic = config.getString("broker.topic.push");

        // Load Kafka consumer batch configuration
        int kafkaMaxPollRecords = config.getInt("kafka.consumer.max-poll-records");
        int kafkaFetchMinBytes = config.getInt("kafka.consumer.fetch-min-bytes");
        int kafkaFetchMaxWaitMs = config.getInt("kafka.consumer.fetch-max-wait-ms");

        // Load event processor configuration
        int selfEventProcessorThreadPoolSize =
                config.getInt("selfevent.processor.thread.pool.size");
        int routerPushThreadPoolSize = config.getInt("router.push.thread.pool.size");
        logger.info("Configuration - Broker Server: {}", brokerServer);
        logger.info("Starting Message Dispatcher...");
        logger.info("Message Topic: {}", messageTopic);
        logger.info("Push Topic: {}", pushTopic);
        logger.info("Message Topic Consumer Group ID: {}", topicMessageGroupId);
        logger.info("Message Topic Consumer Client ID: {}", topicMessageClientId);
        logger.info(
                "Kafka Consumer Batch Config - Max Poll Records: {}, Fetch Min Bytes: {}, Fetch Max Wait (ms): {}",
                kafkaMaxPollRecords,
                kafkaFetchMinBytes,
                kafkaFetchMaxWaitMs);
        logger.info(
                "Event Processor Config - Self Event Processor Thread Pool Size: {}, Router Push Thread Pool Size: {}",
                selfEventProcessorThreadPoolSize,
                routerPushThreadPoolSize);

        CqlSession cqlSession =
                createCQLSession(cassandraContacts, cassandraKeyspace, localDatacenter);
        UserRepository userRepository = createUserRepository(cqlSession);
        RelationRepository relationRepository = createRelationRepository(cqlSession);
        int cpuSize = Runtime.getRuntime().availableProcessors();
        DefaultEventPayloadRouter eventPayloadProcessor =
                createEventPayloadProcessor(
                        pushTopic,
                        createPushMessageProducer(brokerServer),
                        userRepository,
                        relationRepository,
                        createExecutorService(
                                cpuSize, cpuSize * 2, selfEventProcessorThreadPoolSize),
                        createExecutorService(cpuSize, cpuSize * 2, routerPushThreadPoolSize));
        MessageConsumer msgProcessor = new MessageConsumer(eventPayloadProcessor, null);
        GenericConsumer<String, MessagePayload> messageTopicConsumer =
                createMessageTopicConsumer(
                        messageTopic,
                        brokerServer,
                        topicMessageGroupId,
                        topicMessageClientId,
                        kafkaMaxPollRecords,
                        kafkaFetchMinBytes,
                        kafkaFetchMaxWaitMs,
                        msgProcessor);

        Thread messageConsumerThread = new Thread(messageTopicConsumer);
        messageConsumerThread.start();
        messageConsumerThread.join();
    }

    public static void main(String[] args) {
        Application app = new Application();
        try {
            app.run();
        } catch (Exception e) {
            logger.error("Application encountered an error: ", e);
        }
    }

    private GenericConsumer<String, MessagePayload> createMessageTopicConsumer(
            String topic,
            String brokerServer,
            String groupId,
            String clientId,
            int maxPollRecords,
            int fetchMinBytes,
            int fetchMaxWaitMs,
            MessageConsumer msgProcessor) {

        GenericConsumer<String, MessagePayload> c =
                new KafkaGenericConsumer<>(
                        KafkaConsumerConfigFactory.createMessagePayloadConsumerConfig(
                                topic,
                                brokerServer,
                                groupId,
                                clientId,
                                maxPollRecords,
                                fetchMinBytes,
                                fetchMaxWaitMs));
        c.subscribe(msgProcessor::consumeBatch);
        return c;
    }

    private GenericProducer<String, PushMessage> createPushMessageProducer(String brokerServer) {
        return new KafkaGenericProducer<String, PushMessage>(
                KafkaProducerConfigFactory.createPushMessageProducerConfig(brokerServer));
    }

    private DefaultEventPayloadRouter createEventPayloadProcessor(
            String pushTopic,
            GenericProducer<String, PushMessage> pushMessageProducer,
            UserRepository userRepository,
            RelationRepository relationRepository,
            ExecutorService selfInfoExecutor,
            ExecutorService routerExecutor) {

        ProcessorDispatcher<SendEventReq.EventCase, EventData, Void> dispatcher =
                new DefaultEventPayloadProcessorDispatcher();
        dispatcher.RegisterProcessor(
                SendEventReq.EventCase.SELF_INFO_UPDATE_EVENT,
                new SelfInfoUpdateEventPayloadProcessor(
                        selfInfoExecutor, userRepository, relationRepository));
        dispatcher.RegisterProcessor(
                SendEventReq.EventCase.RELATION_EVENT,
                new RelationUpdateEventPayloadProcessor(userRepository, relationRepository));
        return new DefaultEventPayloadRouter(
                pushTopic, pushMessageProducer, dispatcher, routerExecutor);
    }

    private CqlSession createCQLSession(
            List<String> cassandraContacts, String cassandraKeyspace, String localDatacenter) {
        return CassandraDriver.createCqlSession(
                cassandraContacts, cassandraKeyspace, localDatacenter);
    }

    private UserRepository createUserRepository(CqlSession cqlSession) {
        return new CassandraUserRepository(cqlSession);
    }

    private RelationRepository createRelationRepository(CqlSession cqlSession) {
        return new CassandraRelationRepository(cqlSession);
    }

    private ExecutorService createExecutorService(
            int executorServiceCoreSize, int executorServiceMaxSize, int queueCapacity) {
        return new ThreadPoolExecutor(
                executorServiceCoreSize,
                executorServiceMaxSize,
                60,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity));
    }
}
