package com.fanaujie.ripple.msgdispatcher.processor;

public interface Processor<T> {
    void process(String key, T data);
}
