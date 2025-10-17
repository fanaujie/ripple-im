package com.fanaujie.ripple.msgdispatcher;

import com.fanaujie.ripple.communication.messaging.GenericConsumer;
import com.fanaujie.ripple.communication.messaging.GenericProducer;
import com.fanaujie.ripple.communication.messaging.kafka.*;
import com.fanaujie.ripple.msgdispatcher.processor.impl.EventDataProcessor;
import com.fanaujie.ripple.msgdispatcher.service.MessageProcessor;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    private void run() {
        Config config = ConfigFactory.load();
        String messageTopic = config.getString("broker.topic.message");
        String brokerServer = config.getString("broker.server");
        String topicMessageGroupId = config.getString("ripple.topic.message.consumer.group.id");
        String topicMessageClientId = config.getString("ripple.topic.message.consumer.client.id");
        String pushTopic = config.getString("broker.topic.push");
        logger.info("Configuration - Broker Server: {}", brokerServer);
        logger.info("Starting Message Dispatcher...");
        logger.info("Message Topic: {}", messageTopic);
        logger.info("Push Topic: {}", pushTopic);
        logger.info("Message Topic Consumer Group ID: {}", topicMessageGroupId);
        logger.info("Message Topic Consumer Client ID: {}", topicMessageClientId);
        MessageProcessor msgProcessor =
                new MessageProcessor(
                        new EventDataProcessor(pushTopic, createPushMessageProducer(brokerServer)),
                        null);
        GenericConsumer<String, MessagePayload> messageTopicConsumer =
                createMessageTopicConsumer(
                        messageTopic,
                        brokerServer,
                        topicMessageGroupId,
                        topicMessageClientId,
                        msgProcessor);
        Thread messageConsumerThread = new Thread(messageTopicConsumer);
        messageConsumerThread.start();
        try {
            messageConsumerThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Application app = new Application();
        app.run();
    }

    private GenericConsumer<String, MessagePayload> createMessageTopicConsumer(
            String topic,
            String brokerServer,
            String groupId,
            String clientId,
            MessageProcessor msgProcessor) {

        GenericConsumer<String, MessagePayload> c =
                new KafkaGenericConsumer<>(
                        KafkaConsumerConfigFactory.createMessagePayloadConsumerConfig(
                                topic, brokerServer, groupId, clientId));
        c.subscribe(msgProcessor::processMessage);
        return c;
    }

    private GenericProducer<String, MessagePayload> createPushMessageProducer(String brokerServer) {
        return new KafkaGenericProducer<String, MessagePayload>(
                KafkaProducerConfigFactory.createMessagePayloadProducerConfig(brokerServer));
    }
}
