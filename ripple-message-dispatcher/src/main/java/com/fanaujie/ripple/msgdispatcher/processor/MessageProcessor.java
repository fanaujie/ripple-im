package com.fanaujie.ripple.msgdispatcher.processor;

import com.fanaujie.ripple.protobuf.msgdispatcher.DispatchMessageReq;

public interface MessageProcessor {

    String processMessage(DispatchMessageReq request);
}