package com.fanaujie.ripple.communication.msgqueue;

public record MessageRecord<K, V>(K key, V value) {}
