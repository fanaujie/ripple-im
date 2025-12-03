package com.fanaujie.ripple.communication.msgqueue;

public interface KeyedPayloadHandler<T> {
    void handle(String key, T data) throws Exception;
}
