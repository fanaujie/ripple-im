package com.fanaujie.ripple.communication.messaging.kafka;

import com.fanaujie.ripple.communication.messaging.GenericConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.function.BiConsumer;

public class KafkaGenericConsumer<K, V> implements GenericConsumer<K, V> {
    private static final Logger logger = LoggerFactory.getLogger(KafkaGenericConsumer.class);

    private final KafkaConsumer<K, V> consumer;
    private final String topic;
    private BiConsumer<K, V> processor;

    public KafkaGenericConsumer(KafkaConsumerConfig config) {
        Properties props = new Properties();
        props.put(
                org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                config.getBootstrapServers());
        props.put(
                org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG,
                config.getGroupId());
        props.put(
                org.apache.kafka.clients.consumer.ConsumerConfig.CLIENT_ID_CONFIG,
                config.getClientId());
        props.put(
                org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                config.getKeyDeserializer());
        props.put(
                org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                config.getValueDeserializer());
        this.consumer = new KafkaConsumer<>(props);
        this.topic = config.getTopic();
    }

    @Override
    public void subscribe(BiConsumer<K, V> processor) {
        this.processor = processor;
        consumer.subscribe(List.of(topic));
    }

    @Override
    public void run() {
        if (processor == null) {
            throw new IllegalStateException(
                    "Processor must be set before running. Call subscribe() first.");
        }

        try {
            while (true) {
                ConsumerRecords<K, V> records = consumer.poll(Duration.ofMillis(Long.MAX_VALUE));
                records.forEach(record -> processor.accept(record.key(), record.value()));
                consumer.commitAsync();
            }
        } catch (WakeupException e) {
            // Ignore, we're shutting down
            logger.debug("Consumer wakeup called", e);
        } catch (Exception e) {
            logger.error("Unexpected error in consumer", e);
        } finally {
            consumer.close();
        }
    }

    @Override
    public void close() {
        consumer.wakeup();
    }
}
