package com.fanaujie.ripple.msgdispatcher;

import com.datastax.oss.driver.api.core.CqlSession;
import com.fanaujie.ripple.cache.driver.RedisDriver;
import com.fanaujie.ripple.cache.service.impl.RedisConversationSummaryStorage;
import com.fanaujie.ripple.cache.service.impl.RedisUserProfileStorage;
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
import com.fanaujie.ripple.msgdispatcher.consumer.processor.utils.GroupHelper;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq;
import com.fanaujie.ripple.protobuf.msgapiserver.SendGroupCommandReq;
import com.fanaujie.ripple.protobuf.msgapiserver.SendMessageReq;
import com.fanaujie.ripple.protobuf.msgdispatcher.EventData;
import com.fanaujie.ripple.protobuf.msgdispatcher.GroupCommandData;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessageData;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;
import com.fanaujie.ripple.protobuf.storageupdater.StorageUpdatePayload;
import com.fanaujie.ripple.protobuf.push.PushMessage;
import com.fanaujie.ripple.storage.driver.CassandraDriver;
import com.fanaujie.ripple.cache.service.ConversationSummaryStorage;
import com.fanaujie.ripple.storage.service.impl.cassandra.CassandraUnreadCountCalculator;
import com.fanaujie.ripple.storage.service.impl.cassandra.CassandraStorageFacade;
import com.fanaujie.ripple.storage.service.impl.cassandra.CassandraStorageFacadeBuilder;
import org.redisson.api.RedissonClient;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    private void run() throws InterruptedException {
        Config config = ConfigFactory.load();
        String contactPointsStr = config.getString("cassandra.contact.points");
        List<String> cassandraContacts = Arrays.stream(contactPointsStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        String cassandraKeyspace = config.getString("cassandra.keyspace.name");
        String localDatacenter = config.getString("cassandra.local.datacenter");
        String messageTopic = config.getString("broker.topic.message");
        String brokerServer = config.getString("broker.server");
        String topicMessageGroupId = config.getString("ripple.topic.message.consumer.group.id");
        String topicMessageClientId = config.getString("ripple.topic.message.consumer.client.id");
        String pushTopic = config.getString("broker.topic.push");
        String storageUpdateTopic = config.getString("broker.topic.storage-updates");
        String botTopic = config.getString("broker.topic.bot-messages");

        String redisHost = config.getString("redis.host");
        int redisPort = config.getInt("redis.port");
        
        int kafkaMaxPollRecords = config.getInt("kafka.consumer.max-poll-records");
        int kafkaFetchMinBytes = config.getInt("kafka.consumer.fetch-min-bytes");
        int kafkaFetchMaxWaitMs = config.getInt("kafka.consumer.fetch-max-wait-ms");

        logger.info("Starting Message Dispatcher Service...");
        logger.info("Message Topic: {}", messageTopic);

        CqlSession cqlSession =
                createCQLSession(cassandraContacts, cassandraKeyspace, localDatacenter);

        CassandraStorageFacade storageFacade = new CassandraStorageFacadeBuilder().cqlSession(cqlSession).build();
        
        RedissonClient redissonClient = RedisDriver.createRedissonClient(redisHost, redisPort);
        RedisUserProfileStorage userProfileCache = new RedisUserProfileStorage(redissonClient, storageFacade);
        ConversationSummaryStorage conversationStorage = new RedisConversationSummaryStorage(redissonClient, new CassandraUnreadCountCalculator(cqlSession));

        DefaultKeyedPayloadHandler mainPayloadRouter =
                createMainPayloadHandler(
                        pushTopic,
                        storageUpdateTopic,
                        botTopic,
                        createPushMessageProducer(brokerServer),
                        createStorageUpdateProducer(brokerServer),
                        createMessagePayloadProducer(brokerServer),
                        storageFacade,
                        userProfileCache,
                        conversationStorage);
        
        GenericConsumer<String, MessagePayload> mainConsumer =
                createMessageTopicConsumer(
                        messageTopic,
                        brokerServer,
                        topicMessageGroupId,
                        topicMessageClientId,
                        kafkaMaxPollRecords,
                        kafkaFetchMinBytes,
                        kafkaFetchMaxWaitMs,
                        new MessageConsumer(mainPayloadRouter));

        Thread mainThread = new Thread(mainConsumer);
        mainThread.start();
        mainThread.join();
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

        return new KafkaGenericConsumer<>(
                KafkaConsumerConfigFactory.createMessagePayloadConsumerConfig(
                        topic, brokerServer, groupId, clientId, maxPollRecords, fetchMinBytes, fetchMaxWaitMs));
    }

    private GenericProducer<String, PushMessage> createPushMessageProducer(String brokerServer) {
        return new KafkaGenericProducer<>(
                KafkaProducerConfigFactory.createPushMessageProducerConfig(brokerServer));
    }

    private GenericProducer<String, StorageUpdatePayload> createStorageUpdateProducer(
            String brokerServer) {
        return new KafkaGenericProducer<>(
                KafkaProducerConfigFactory.createStorageUpdatePayloadProducerConfig(brokerServer));
    }

    private GenericProducer<String, MessagePayload> createMessagePayloadProducer(
            String brokerServer) {
        return new KafkaGenericProducer<>(
                KafkaProducerConfigFactory.createMessagePayloadProducerConfig(brokerServer));
    }

    private DefaultKeyedPayloadHandler createMainPayloadHandler(
            String pushTopic,
            String storageUpdateTopic,
            String botTopic,
            GenericProducer<String, PushMessage> pushMessageProducer,
            GenericProducer<String, StorageUpdatePayload> storageUpdateProducer,
            GenericProducer<String, MessagePayload> botMessageProducer,
            CassandraStorageFacade storageFacade,
            RedisUserProfileStorage userProfileCache,
            ConversationSummaryStorage conversationStorage) {

        ProcessorDispatcher<SendMessageReq.MessageCase, MessageData, Void> messageDispatcher = new DefaultProcessorDispatcher<>();
        messageDispatcher.RegisterProcessor(
                SendMessageReq.MessageCase.SINGLE_MESSAGE_CONTENT,
                new SingleMessagePayloadProcessor(
                        storageFacade, conversationStorage, pushMessageProducer, pushTopic, botMessageProducer, botTopic));

        // ... (rest of the handler creation)
        ProcessorDispatcher<SendEventReq.EventCase, EventData, Void> eventDispatcher = new DefaultProcessorDispatcher<>();
        eventDispatcher.RegisterProcessor( SendEventReq.EventCase.SELF_INFO_UPDATE_EVENT, new SelfInfoUpdateEventPayloadProcessor(storageFacade, storageUpdateProducer, storageUpdateTopic, pushMessageProducer, pushTopic));
        eventDispatcher.RegisterProcessor( SendEventReq.EventCase.RELATION_EVENT, new RelationUpdateEventPayloadProcessor(storageFacade, storageUpdateProducer, storageUpdateTopic, pushMessageProducer, pushTopic));
        GroupHelper groupNotificationHelper = new GroupHelper(storageUpdateProducer, storageUpdateTopic, storageFacade, conversationStorage);
        ProcessorDispatcher<SendGroupCommandReq.CommandContentCase, GroupCommandData, Void> groupCommandDispatcher = new DefaultProcessorDispatcher<>();
        groupCommandDispatcher.RegisterProcessor(SendGroupCommandReq.CommandContentCase.GROUP_CREATE_COMMAND, new CreateGroupCommandPayloadProcessor(storageFacade, userProfileCache, groupNotificationHelper));
        groupCommandDispatcher.RegisterProcessor(SendGroupCommandReq.CommandContentCase.GROUP_UPDATE_INFO_COMMAND, new UpdateGroupInfoCommandPayloadProcessor(storageFacade, userProfileCache, groupNotificationHelper));
        groupCommandDispatcher.RegisterProcessor(SendGroupCommandReq.CommandContentCase.GROUP_INVITE_COMMAND, new InviteGroupMemberCommandPayloadProcessor(storageFacade, userProfileCache, groupNotificationHelper));
        groupCommandDispatcher.RegisterProcessor(SendGroupCommandReq.CommandContentCase.GROUP_QUIT_COMMAND, new QuitGroupCommandPayloadProcessor(storageFacade, groupNotificationHelper, pushMessageProducer, pushTopic));
        return new DefaultKeyedPayloadHandler(eventDispatcher, messageDispatcher, groupCommandDispatcher);
    }

    private CqlSession createCQLSession(
            List<String> cassandraContacts, String cassandraKeyspace, String localDatacenter) {
        return CassandraDriver.createCqlSession(cassandraContacts, cassandraKeyspace, localDatacenter);
    }
}