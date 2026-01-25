package com.fanaujie.ripple.botmsgconsumer;

import com.fanaujie.ripple.botmsgconsumer.client.WebhookServiceClientManager;
import com.fanaujie.ripple.communication.msgqueue.GenericConsumer;
import com.fanaujie.ripple.communication.msgqueue.kafka.KafkaConsumerConfigFactory;
import com.fanaujie.ripple.communication.msgqueue.kafka.KafkaGenericConsumer;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    private WebhookServiceClientManager clientManager;

    private void run() throws Exception {
        Config config = ConfigFactory.load();

        // Kafka Configuration
        String botWebhookTopic = config.getString("broker.topic.bot-webhook");
        String brokerServer = config.getString("broker.server");
        String groupId = config.getString("consumer.group.id");
        String clientId = config.getString("consumer.client.id");

        // Kafka Consumer Batch Configuration
        int maxPollRecords = config.getInt("kafka.consumer.max-poll-records");
        int fetchMinBytes = config.getInt("kafka.consumer.fetch-min-bytes");
        int fetchMaxWaitMs = config.getInt("kafka.consumer.fetch-max-wait-ms");

        // Webhook Service Configuration
        String webhookServiceAddress = config.getString("webhook-service.address");

        logger.info("Starting Bot Message Consumer...");
        logger.info("Bot Webhook Topic: {}", botWebhookTopic);
        logger.info("Broker Server: {}", brokerServer);
        logger.info("Consumer Group: {}, Client: {}", groupId, clientId);
        logger.info("Webhook Service Address: {}", webhookServiceAddress);

        // Initialize webhook-service client manager (using gRPC DNS-based round-robin)
        clientManager = new WebhookServiceClientManager(webhookServiceAddress);

        // Initialize bot message consumer
        BotMessageConsumer botMessageConsumer = new BotMessageConsumer(clientManager);

        // Create Kafka consumer
        GenericConsumer<String, MessagePayload> consumer = new KafkaGenericConsumer<>(
                KafkaConsumerConfigFactory.createMessagePayloadConsumerConfig(
                        botWebhookTopic,
                        brokerServer,
                        groupId,
                        clientId,
                        maxPollRecords,
                        fetchMinBytes,
                        fetchMaxWaitMs));

        consumer.subscribe(botMessageConsumer::consumeBatch);

        // Start consumer thread
        Thread consumerThread = new Thread(consumer);
        consumerThread.start();

        logger.info("Bot Message Consumer started successfully");

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down Bot Message Consumer...");
            try {
                if (clientManager != null) {
                    clientManager.close();
                }
            } catch (Exception e) {
                logger.error("Error during shutdown", e);
            }
        }));

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
}
