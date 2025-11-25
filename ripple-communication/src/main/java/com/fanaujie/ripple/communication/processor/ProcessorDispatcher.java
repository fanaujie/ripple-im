package com.fanaujie.ripple.communication.processor;

public interface ProcessorDispatcher<CASE_TYPE, REQUEST, RESPONSE> {
    void RegisterProcessor(CASE_TYPE caseType, Processor<REQUEST, RESPONSE> processor);

    void UnregisterProcessor(CASE_TYPE caseType);

    RESPONSE dispatch(CASE_TYPE caseType, REQUEST request) throws Exception;
}
