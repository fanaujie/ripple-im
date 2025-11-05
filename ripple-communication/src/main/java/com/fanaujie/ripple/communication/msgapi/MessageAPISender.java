package com.fanaujie.ripple.communication.msgapi;

import com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq;

public interface MessageAPISender {
    void sendEvent(SendEventReq sendEventReq) throws Exception;
}
