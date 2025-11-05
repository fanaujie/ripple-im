package com.fanaujie.ripple.communication.processor;

public interface ProcessorDispatcher<EVENT_TYPE, REQUEST, RESPONSE> {
    void RegisterProcessor(EVENT_TYPE eventCase, Processor<REQUEST, RESPONSE> processor);

    void UnregisterProcessor(EVENT_TYPE eventCase);

    RESPONSE dispatch(REQUEST request) throws Exception;
}
