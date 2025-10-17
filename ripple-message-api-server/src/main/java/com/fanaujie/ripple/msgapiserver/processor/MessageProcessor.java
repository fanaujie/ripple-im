package com.fanaujie.ripple.msgapiserver.processor;

import com.fanaujie.ripple.protobuf.msgapiserver.SendMessageReq;

public interface MessageProcessor {

    String processMessage(SendMessageReq request);
}
