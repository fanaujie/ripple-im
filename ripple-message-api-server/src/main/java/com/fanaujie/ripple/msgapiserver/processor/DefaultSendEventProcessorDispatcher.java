package com.fanaujie.ripple.msgapiserver.processor;

import com.fanaujie.ripple.communication.processor.Processor;
import com.fanaujie.ripple.communication.processor.ProcessorDispatcher;
import com.fanaujie.ripple.msgapiserver.exception.NotFoundListenerException;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventResp;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultSendEventProcessorDispatcher
        implements ProcessorDispatcher<SendEventReq.EventCase, SendEventReq, SendEventResp> {

    private Map<SendEventReq.EventCase, Processor<SendEventReq, SendEventResp>> registry =
            new ConcurrentHashMap<>();

    @Override
    public void RegisterProcessor(
            SendEventReq.EventCase eventCase, Processor<SendEventReq, SendEventResp> listener) {
        registry.put(eventCase, listener);
    }

    @Override
    public void UnregisterProcessor(SendEventReq.EventCase eventCase) {
        registry.remove(eventCase);
    }

    @Override
    public SendEventResp dispatch(SendEventReq request) throws Exception {
        Processor<SendEventReq, SendEventResp> processor = registry.get(request.getEventCase());
        if (processor != null) {
            return processor.handle(request);
        }
        throw new NotFoundListenerException(
                "No event listener found for event type: " + request.getEventCase());
    }
}
