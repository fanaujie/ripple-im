package com.fanaujie.ripple.communication.msgapi;

import com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq;
import com.fanaujie.ripple.protobuf.msgapiserver.SendGroupCommandReq;
import com.fanaujie.ripple.protobuf.msgapiserver.SendMessageReq;

public interface MessageAPISender {
    void sendEvent(SendEventReq sendEventReq) throws Exception;

    void seenMessage(SendMessageReq sendMessageReq) throws Exception;

    void sendGroupCommand(SendGroupCommandReq sendGroupCommandReq) throws Exception;
}
