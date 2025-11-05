package com.fanaujie.ripple.communication.msgqueue;

import java.util.List;

@FunctionalInterface
public interface ThrowingBiConsumer<K, V> {
    void processBatch(List<MessageRecord<K, V>> records) throws Exception;
}
