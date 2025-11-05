package com.fanaujie.ripple.communication.msgqueue.kafka;

import com.fanaujie.ripple.communication.msgqueue.GenericProducer;
import com.fanaujie.ripple.communication.msgqueue.exception.ProducerException;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.util.Properties;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

public class KafkaGenericProducer<K, V> implements GenericProducer<K, V> {
    private final KafkaProducer<K, V> producer;

    public KafkaGenericProducer(KafkaProducerConfig config) {
        Properties props = new Properties();
        props.put(
                org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                config.getBootstrapServers());
        props.put(
                org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                config.getKeySerializer());
        props.put(
                org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                config.getValueSerializer());
        this.producer = new KafkaProducer<>(props);
    }

    @Override
    public void send(String topic, K key, V value) {
        try {
            producer.send(new ProducerRecord<>(topic, key, value)).get();
        } catch (CancellationException | InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new ProducerException("Failed to send message to topic: " + topic, e);
        }
    }

    @Override
    public void close() {
        if (producer != null) {
            producer.close();
        }
    }
}
