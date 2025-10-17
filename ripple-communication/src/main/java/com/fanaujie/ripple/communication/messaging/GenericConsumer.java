package com.fanaujie.ripple.communication.messaging;

import java.util.function.BiConsumer;

public interface GenericConsumer<K, V> extends Runnable {
    void subscribe(BiConsumer<K, V> processor);

    void close();
}
