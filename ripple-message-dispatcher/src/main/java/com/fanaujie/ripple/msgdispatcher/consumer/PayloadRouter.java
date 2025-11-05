package com.fanaujie.ripple.msgdispatcher.consumer;

public interface PayloadRouter<T> {
    void handle(String key, T data) throws Exception;
}
