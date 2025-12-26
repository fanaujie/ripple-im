package com.fanaujie.ripple.integration.mock;

import com.fanaujie.ripple.communication.msgqueue.GenericProducer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class MockProducer<K, V> implements GenericProducer<K, V> {

    private final List<CapturedMessage<K, V>> capturedMessages = new CopyOnWriteArrayList<>();

    @Override
    public void send(String topic, K key, V value) {
        capturedMessages.add(new CapturedMessage<>(topic, key, value));
    }

    @Override
    public void close() {
        // No-op for mock
    }

    public List<CapturedMessage<K, V>> getCapturedMessages() {
        return new ArrayList<>(capturedMessages);
    }

    public List<CapturedMessage<K, V>> getMessagesByTopic(String topic) {
        return capturedMessages.stream().filter(msg -> msg.topic().equals(topic)).toList();
    }

    public void clear() {
        capturedMessages.clear();
    }

    public boolean hasMessages() {
        return !capturedMessages.isEmpty();
    }

    public int messageCount() {
        return capturedMessages.size();
    }

    public record CapturedMessage<K, V>(String topic, K key, V value) {}
}
