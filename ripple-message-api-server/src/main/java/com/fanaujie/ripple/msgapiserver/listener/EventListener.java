package com.fanaujie.ripple.msgapiserver.listener;

import com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventResp;

public interface EventListener {
    SendEventResp handleEvent(SendEventReq request) throws Exception;
}
