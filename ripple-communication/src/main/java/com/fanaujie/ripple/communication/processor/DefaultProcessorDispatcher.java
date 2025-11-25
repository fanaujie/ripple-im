package com.fanaujie.ripple.communication.processor;

import com.fanaujie.ripple.communication.exception.NotFoundListenerException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultProcessorDispatcher<CASE, REQ, RESP>
        implements ProcessorDispatcher<CASE, REQ, RESP> {

    private final Map<CASE, Processor<REQ, RESP>> registry = new ConcurrentHashMap<>();

    @Override
    public void RegisterProcessor(CASE caseType, Processor<REQ, RESP> listener) {
        registry.put(caseType, listener);
    }

    @Override
    public void UnregisterProcessor(CASE caseType) {
        registry.remove(caseType);
    }

    @Override
    public RESP dispatch(CASE c, REQ request) throws Exception {
        Processor<REQ, RESP> processor = registry.get(c);
        if (processor != null) {
            return processor.handle(request);
        }
        throw new NotFoundListenerException("No event listener found for event type: " + c);
    }
}
