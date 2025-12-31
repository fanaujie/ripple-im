package com.fanaujie.ripple.botexecutor.consumer;

import com.fanaujie.ripple.botexecutor.processor.BotExecutorProcessor;
import com.fanaujie.ripple.communication.processor.ProcessorDispatcher;
import com.fanaujie.ripple.protobuf.msgapiserver.SendMessageReq;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessageData;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;

public class BotPayloadHandler {

    private final ProcessorDispatcher<SendMessageReq.MessageCase, MessageData, Void> messageDispatcher;

    public BotPayloadHandler(BotExecutorProcessor processor) {
        this.messageDispatcher = new com.fanaujie.ripple.communication.processor.DefaultProcessorDispatcher<>();
        this.messageDispatcher.RegisterProcessor(SendMessageReq.MessageCase.SINGLE_MESSAGE_CONTENT, processor);
    }

    public void process(String key, MessagePayload payload) throws Exception {
        if (payload.hasMessageData()) {
            messageDispatcher.dispatch(payload.getMessageData().getData().getMessageCase(), payload.getMessageData());
        }
    }
}
