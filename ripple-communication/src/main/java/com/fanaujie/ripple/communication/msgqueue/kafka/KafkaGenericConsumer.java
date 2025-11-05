package com.fanaujie.ripple.communication.msgqueue.kafka;

import com.fanaujie.ripple.communication.msgqueue.GenericConsumer;
import com.fanaujie.ripple.communication.msgqueue.MessageRecord;
import com.fanaujie.ripple.communication.msgqueue.ThrowingBiConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class KafkaGenericConsumer<K, V> implements GenericConsumer<K, V> {
    private static final Logger logger = LoggerFactory.getLogger(KafkaGenericConsumer.class);

    private final KafkaConsumer<K, V> consumer;
    private final String topic;
    private ThrowingBiConsumer<K, V> processor;

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
        props.put(
                org.apache.kafka.clients.consumer.ConsumerConfig.MAX_POLL_RECORDS_CONFIG,
                config.getMaxPollRecords());
        props.put(
                org.apache.kafka.clients.consumer.ConsumerConfig.FETCH_MIN_BYTES_CONFIG,
                config.getFetchMinBytes());
        props.put(
                org.apache.kafka.clients.consumer.ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG,
                config.getFetchMaxWaitMs());
        this.consumer = new KafkaConsumer<>(props);
        this.topic = config.getTopic();
    }

    @Override
    public void subscribe(ThrowingBiConsumer<K, V> processor) {
        this.processor = processor;
        consumer.subscribe(List.of(topic));
    }

    @Override
    public void run() {
        if (processor == null) {
            throw new IllegalStateException(
                    "Processor must be set before running. Call subscribe() first.");
        }
        List<MessageRecord<K, V>> msgRecords = new ArrayList<MessageRecord<K, V>>();
        while (true) {
            try {
                ConsumerRecords<K, V> records = consumer.poll(Duration.ofMillis(1000));
                for (ConsumerRecord<K, V> record : records) {
                    msgRecords.add(new MessageRecord<>(record.key(), record.value()));
                }
                processor.processBatch(msgRecords);
                consumer.commitAsync();
                msgRecords.clear();
            } catch (WakeupException e) {
                // Ignore, we're shutting down
                break;
            } catch (Exception e) {
                logger.error("Unexpected error in consumer", e);
                break;
            }
        }
        consumer.close();
    }

    @Override
    public void close() {
        consumer.wakeup();
    }
}
