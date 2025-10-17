package com.fanaujie.ripple.communication.messaging;

public interface GenericProducer<K, V> {
    void send(String topic, K key, V value);

    void close();
}
