package com.fanaujie.ripple.msgapiserver.processor.impl;

import com.fanaujie.ripple.msgapiserver.exception.NotFoundEvnetListenerException;
import com.fanaujie.ripple.msgapiserver.listener.EventListener;
import com.fanaujie.ripple.msgapiserver.processor.EventProcessor;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventResp;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultEventProcessor implements EventProcessor {

    private Map<SendEventReq.EventCase, EventListener> eventListeners = new ConcurrentHashMap<>();

    @Override
    public void RegisterEventListener(SendEventReq.EventCase eventCase, EventListener listener) {
        eventListeners.put(eventCase, listener);
    }

    @Override
    public void UnregisterEventListener(SendEventReq.EventCase eventCase) {
        eventListeners.remove(eventCase);
    }

    @Override
    public SendEventResp processEvent(SendEventReq request) throws Exception {
        EventListener listener = eventListeners.get(request.getEventCase());
        if (listener != null) {
            return listener.handleEvent(request);
        }
        throw new NotFoundEvnetListenerException(
                "No event listener found for event type: " + request.getEventCase());
    }
}
