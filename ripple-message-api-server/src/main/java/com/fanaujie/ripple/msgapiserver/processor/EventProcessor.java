package com.fanaujie.ripple.msgapiserver.processor;

import com.fanaujie.ripple.msgapiserver.listener.EventListener;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventResp;

public interface EventProcessor {
    void RegisterEventListener(SendEventReq.EventCase eventCase, EventListener listener);

    void UnregisterEventListener(SendEventReq.EventCase eventCase);

    SendEventResp processEvent(SendEventReq request) throws Exception;
}
