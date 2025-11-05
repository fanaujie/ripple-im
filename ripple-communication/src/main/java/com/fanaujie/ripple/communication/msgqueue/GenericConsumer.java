package com.fanaujie.ripple.communication.msgqueue;

public interface GenericConsumer<K, V> extends Runnable {
    void subscribe(ThrowingBiConsumer<K, V> processor);

    void close();
}
