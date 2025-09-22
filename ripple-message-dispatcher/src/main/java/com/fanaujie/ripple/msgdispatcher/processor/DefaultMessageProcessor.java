package com.fanaujie.ripple.msgdispatcher.processor;

import com.fanaujie.ripple.protobuf.msgdispatcher.DispatchMessageReq;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class DefaultMessageProcessor implements MessageProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DefaultMessageProcessor.class);

    @Override
    public String processMessage(DispatchMessageReq request) {
        logger.info("Processing message for user {} with type {}",
                request.getUserId(), request.getMessageType());

        String dispatchId = UUID.randomUUID().toString();

        logger.info("Generated dispatch ID: {} for user {}", dispatchId, request.getUserId());

        return dispatchId;
    }
}