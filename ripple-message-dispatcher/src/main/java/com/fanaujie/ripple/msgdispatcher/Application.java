package com.fanaujie.ripple.msgdispatcher;

import com.datastax.oss.driver.api.core.CqlSession;
import com.fanaujie.ripple.communication.msgqueue.GenericConsumer;
import com.fanaujie.ripple.communication.msgqueue.GenericProducer;
import com.fanaujie.ripple.communication.msgqueue.kafka.KafkaConsumerConfigFactory;
import com.fanaujie.ripple.communication.msgqueue.kafka.KafkaGenericConsumer;
import com.fanaujie.ripple.communication.msgqueue.kafka.KafkaGenericProducer;
import com.fanaujie.ripple.communication.msgqueue.kafka.KafkaProducerConfigFactory;
import com.fanaujie.ripple.communication.processor.DefaultProcessorDispatcher;
import com.fanaujie.ripple.communication.processor.ProcessorDispatcher;
import com.fanaujie.ripple.msgdispatcher.consumer.MessageConsumer;
import com.fanaujie.ripple.msgdispatcher.consumer.DefaultKeyedPayloadHandler;
import com.fanaujie.ripple.msgdispatcher.consumer.processor.*;
import com.fanaujie.ripple.msgdispatcher.consumer.processor.utils.GroupNotificationHelper;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq;
import com.fanaujie.ripple.protobuf.msgapiserver.SendGroupCommandReq;
import com.fanaujie.ripple.protobuf.msgapiserver.SendMessageReq;
import com.fanaujie.ripple.protobuf.msgdispatcher.EventData;
import com.fanaujie.ripple.protobuf.msgdispatcher.GroupCommandData;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessageData;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;
import com.fanaujie.ripple.protobuf.profileupdater.ProfileUpdatePayload;
import com.fanaujie.ripple.protobuf.push.PushEventData;
import com.fanaujie.ripple.protobuf.push.PushMessage;
import com.fanaujie.ripple.storage.cache.UserProfileRedissonKvCache;
import com.fanaujie.ripple.storage.driver.CassandraDriver;
import com.fanaujie.ripple.storage.driver.RedisDriver;
import com.fanaujie.ripple.storage.model.UserProfile;
import com.fanaujie.ripple.storage.service.impl.cassandra.CassandraUserStorageFacade;
import com.fanaujie.ripple.storage.service.impl.cassandra.CassandraUserStorageFacadeBuilder;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.redisson.Redisson;
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
        List<String> cassandraContacts = config.getStringList("cassandra.contact.points");
        String cassandraKeyspace = config.getString("cassandra.keyspace.name");
        String localDatacenter = config.getString("cassandra.local.datacenter");
        String messageTopic = config.getString("broker.topic.message");
        String brokerServer = config.getString("broker.server");
        String topicMessageGroupId = config.getString("ripple.topic.message.consumer.group.id");
        String topicMessageClientId = config.getString("ripple.topic.message.consumer.client.id");
        String pushTopic = config.getString("broker.topic.push");
        String profileUpdateTopic = config.getString("broker.topic.profile-updates");
        String redisHost = config.getString("redis.host");
        int redisPort = config.getInt("redis.port");
        // Load Kafka consumer batch configuration
        int kafkaMaxPollRecords = config.getInt("kafka.consumer.max-poll-records");
        int kafkaFetchMinBytes = config.getInt("kafka.consumer.fetch-min-bytes");
        int kafkaFetchMaxWaitMs = config.getInt("kafka.consumer.fetch-max-wait-ms");

        // Load processor configuration
        logger.info("Configuration - Redis Host: {}, Redis Port: {}", redisHost, redisPort);
        logger.info("Configuration - Broker Server: {}", brokerServer);
        logger.info("Starting Message Dispatcher...");
        logger.info("Message Topic: {}", messageTopic);
        logger.info("Push Topic: {}", pushTopic);
        logger.info("Profile Update Topic: {}", profileUpdateTopic);
        logger.info("Message Topic Consumer Group ID: {}", topicMessageGroupId);
        logger.info("Message Topic Consumer Client ID: {}", topicMessageClientId);
        logger.info(
                "Kafka Consumer Batch Config - Max Poll Records: {}, Fetch Min Bytes: {}, Fetch Max Wait (ms): {}",
                kafkaMaxPollRecords,
                kafkaFetchMinBytes,
                kafkaFetchMaxWaitMs);

        CqlSession cqlSession =
                createCQLSession(cassandraContacts, cassandraKeyspace, localDatacenter);

        CassandraUserStorageFacadeBuilder userStorageFacadeBuilder =
                new CassandraUserStorageFacadeBuilder();
        userStorageFacadeBuilder.cqlSession(cqlSession);
        CassandraUserStorageFacade userStorageFacade = userStorageFacadeBuilder.build();
        UserProfileRedissonKvCache userProfileCache =
                new UserProfileRedissonKvCache(
                        RedisDriver.createRedissonClient(redisHost, redisPort), userStorageFacade);
        DefaultKeyedPayloadHandler payloadRouter =
                createKeyedPayloadHandler(
                        pushTopic,
                        profileUpdateTopic,
                        createPushMessageProducer(brokerServer),
                        createProfileUpdateProducer(brokerServer),
                        userStorageFacade,
                        userProfileCache);
        MessageConsumer msgProcessor = new MessageConsumer(payloadRouter);
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

    private GenericProducer<String, ProfileUpdatePayload> createProfileUpdateProducer(
            String brokerServer) {
        return new KafkaGenericProducer<String, ProfileUpdatePayload>(
                KafkaProducerConfigFactory.createProfileUpdatePayloadProducerConfig(brokerServer));
    }

    private DefaultKeyedPayloadHandler createKeyedPayloadHandler(
            String pushTopic,
            String profileUpdateTopic,
            GenericProducer<String, PushMessage> pushMessageProducer,
            GenericProducer<String, ProfileUpdatePayload> profileUpdateProducer,
            CassandraUserStorageFacade userStorageFacade,
            UserProfileRedissonKvCache userProfileCache) {

        ProcessorDispatcher<SendMessageReq.MessageCase, MessageData, Void> messageDispatcher =
                new DefaultProcessorDispatcher<>();
        messageDispatcher.RegisterProcessor(
                SendMessageReq.MessageCase.SINGLE_MESSAGE_CONTENT,
                new SingleMessagePayloadProcessor(
                        userStorageFacade, pushMessageProducer, pushTopic));

        ProcessorDispatcher<SendEventReq.EventCase, EventData, Void> eventDispatcher =
                new DefaultProcessorDispatcher<>();
        eventDispatcher.RegisterProcessor(
                SendEventReq.EventCase.SELF_INFO_UPDATE_EVENT,
                new SelfInfoUpdateEventPayloadProcessor(
                        userStorageFacade,
                        profileUpdateProducer,
                        profileUpdateTopic,
                        pushMessageProducer,
                        pushTopic));
        eventDispatcher.RegisterProcessor(
                SendEventReq.EventCase.RELATION_EVENT,
                new RelationUpdateEventPayloadProcessor(
                        userStorageFacade,
                        profileUpdateProducer,
                        profileUpdateTopic,
                        pushMessageProducer,
                        pushTopic));

        GroupNotificationHelper groupNotificationHelper =
                new GroupNotificationHelper(
                        profileUpdateProducer,
                        profileUpdateTopic,
                        pushMessageProducer,
                        pushTopic,
                        userStorageFacade);

        ProcessorDispatcher<SendGroupCommandReq.CommandContentCase, GroupCommandData, Void>
                groupCommandDispatcher = new DefaultProcessorDispatcher<>();
        groupCommandDispatcher.RegisterProcessor(
                SendGroupCommandReq.CommandContentCase.GROUP_CREATE_COMMAND,
                new CreateGroupCommandPayloadProcessor(
                        userStorageFacade, userProfileCache, groupNotificationHelper));
        groupCommandDispatcher.RegisterProcessor(
                SendGroupCommandReq.CommandContentCase.GROUP_UPDATE_INFO_COMMAND,
                new UpdateGroupInfoCommandPayloadProcessor(userStorageFacade));
        groupCommandDispatcher.RegisterProcessor(
                SendGroupCommandReq.CommandContentCase.GROUP_INVITE_COMMAND,
                new InviteGroupMemberCommandPayloadProcessor(
                        userStorageFacade, userProfileCache, groupNotificationHelper));
        groupCommandDispatcher.RegisterProcessor(
                SendGroupCommandReq.CommandContentCase.GROUP_QUIT_COMMAND,
                new QuitGroupCommandPayloadProcessor(userStorageFacade));
        return new DefaultKeyedPayloadHandler(
                eventDispatcher, messageDispatcher, groupCommandDispatcher);
    }

    private CqlSession createCQLSession(
            List<String> cassandraContacts, String cassandraKeyspace, String localDatacenter) {
        return CassandraDriver.createCqlSession(
                cassandraContacts, cassandraKeyspace, localDatacenter);
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
