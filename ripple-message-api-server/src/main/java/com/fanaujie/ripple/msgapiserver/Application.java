package com.fanaujie.ripple.msgapiserver;

import com.fanaujie.ripple.communication.msgqueue.GenericProducer;
import com.fanaujie.ripple.communication.msgqueue.kafka.KafkaGenericProducer;
import com.fanaujie.ripple.communication.msgqueue.kafka.KafkaProducerConfigFactory;
import com.fanaujie.ripple.communication.processor.DefaultProcessorDispatcher;
import com.fanaujie.ripple.communication.processor.ProcessorDispatcher;
import com.fanaujie.ripple.msgapiserver.processor.*;
import com.fanaujie.ripple.msgapiserver.server.GrpcServer;
import com.fanaujie.ripple.protobuf.msgapiserver.*;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import com.fanaujie.ripple.storage.spi.RippleStorageLoader;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.*;

public class Application {

    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    public void run() {
        Config config = ConfigFactory.load();
        String redisHost = config.getString("redis.host");
        int redisPort = config.getInt("redis.port");
        String brokerTopic = config.getString("broker.topic.message");
        String brokerServer = config.getString("broker.server");
        int executorQueueSize = config.getInt("ripple.executor.queue.capacity");
        int grpcPort = config.getInt("server.grpc.port");
        logger.info("Configuration - Redis Host: {}, Redis Port: {}", redisHost, redisPort);
        logger.info("Configuration - Broker Server: {}", brokerServer);
        logger.info("Configuration - Broker Topic: {}", brokerTopic);
        logger.info("Configuration - Executor Queue Size: {}", executorQueueSize);
        logger.info("gRPC Port: {}", grpcPort);
        GenericProducer<String, MessagePayload> producer = createKafkaProducer(brokerServer);
        ExecutorService executorService = createExecutorService(executorQueueSize);
        RippleStorageFacade userStorageFacade = RippleStorageLoader.load(System::getenv);
        GrpcServer grpcServer =
                new GrpcServer(
                        grpcPort,
                        createMessageDispatcher(
                                brokerTopic, userStorageFacade, producer, executorService),
                        createEventDispatcher(
                                brokerTopic, userStorageFacade, producer, executorService),
                        createGroupDispatcher(
                                brokerTopic, userStorageFacade, producer, executorService));
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

    private ProcessorDispatcher<SendMessageReq.MessageCase, SendMessageReq, SendMessageResp>
            createMessageDispatcher(
                    String topic,
                    RippleStorageFacade userStorageFacade,
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
                    RippleStorageFacade userStorageFacade,
                    GenericProducer<String, MessagePayload> messageProducer,
                    ExecutorService executorService) {
        ProcessorDispatcher<SendEventReq.EventCase, SendEventReq, SendEventResp> eventDispatcher =
                new DefaultProcessorDispatcher<>();
        eventDispatcher.RegisterProcessor(
                SendEventReq.EventCase.SELF_INFO_UPDATE_EVENT,
                new SelfInfoUpdateEventProcessor(
                        messageTopic, userStorageFacade, messageProducer, executorService));
        eventDispatcher.RegisterProcessor(
                SendEventReq.EventCase.RELATION_EVENT,
                new RelationEventProcessor(
                        messageTopic, messageProducer, executorService, userStorageFacade));
        return eventDispatcher;
    }

    private ProcessorDispatcher<
                    SendGroupCommandReq.CommandContentCase,
                    SendGroupCommandReq,
                    SendGroupCommandResp>
            createGroupDispatcher(
                    String topic,
                    RippleStorageFacade userStorageFacade,
                    GenericProducer<String, MessagePayload> producer,
                    ExecutorService executorService) {
        ProcessorDispatcher<
                        SendGroupCommandReq.CommandContentCase,
                        SendGroupCommandReq,
                        SendGroupCommandResp>
                groupDispatcher = new DefaultProcessorDispatcher<>();
        groupDispatcher.RegisterProcessor(
                SendGroupCommandReq.CommandContentCase.GROUP_CREATE_COMMAND,
                new CreateGroupProcessor(topic, userStorageFacade, producer, executorService));
        groupDispatcher.RegisterProcessor(
                SendGroupCommandReq.CommandContentCase.GROUP_INVITE_COMMAND,
                new InviteMembersProcessor(topic, userStorageFacade, producer, executorService));
        groupDispatcher.RegisterProcessor(
                SendGroupCommandReq.CommandContentCase.GROUP_QUIT_COMMAND,
                new QuitGroupProcessor(topic, userStorageFacade, producer, executorService));
        groupDispatcher.RegisterProcessor(
                SendGroupCommandReq.CommandContentCase.GROUP_UPDATE_INFO_COMMAND,
                new UpdateGroupInfoProcessor(topic, userStorageFacade, producer, executorService));
        return groupDispatcher;
    }
}
