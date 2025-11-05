package com.fanaujie.ripple.communication.processor;

public interface Processor<REQUEST, RESPONSE> {
    RESPONSE handle(REQUEST request) throws Exception;
}
