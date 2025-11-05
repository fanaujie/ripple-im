package com.fanaujie.ripple.msgdispatcher.consumer.processor;

import com.fanaujie.ripple.communication.processor.Processor;
import com.fanaujie.ripple.communication.processor.ProcessorDispatcher;
import com.fanaujie.ripple.msgdispatcher.exception.NotFoundListenerException;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq;
import com.fanaujie.ripple.protobuf.msgdispatcher.EventData;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultEventPayloadProcessorDispatcher
        implements ProcessorDispatcher<SendEventReq.EventCase, EventData, Void> {

    private Map<SendEventReq.EventCase, Processor<EventData, Void>> registry =
            new ConcurrentHashMap<>();

    @Override
    public void RegisterProcessor(
            SendEventReq.EventCase eventCase, Processor<EventData, Void> processor) {
        this.registry.put(eventCase, processor);
    }

    @Override
    public void UnregisterProcessor(SendEventReq.EventCase eventCase) {
        this.registry.remove(eventCase);
    }

    @Override
    public Void dispatch(EventData eventData) throws Exception {
        Processor<EventData, Void> processor = registry.get(eventData.getData().getEventCase());
        if (processor != null) {
            return processor.handle(eventData);
        }
        throw new NotFoundListenerException(
                "No event listener found for event type: " + eventData.getData().getEventCase());
    }
}
