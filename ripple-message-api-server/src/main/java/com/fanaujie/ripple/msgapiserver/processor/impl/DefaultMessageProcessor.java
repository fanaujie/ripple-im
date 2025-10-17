package com.fanaujie.ripple.msgapiserver.processor.impl;

import com.fanaujie.ripple.msgapiserver.processor.MessageProcessor;
import com.fanaujie.ripple.protobuf.msgapiserver.SendMessageReq;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class DefaultMessageProcessor implements MessageProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DefaultMessageProcessor.class);

    @Override
    public String processMessage(SendMessageReq request) {
        logger.info(
                "Processing message for user {} with type {}",
                request.getUserId(),
                request.getMessageType());

        String dispatchId = UUID.randomUUID().toString();

        logger.info("Generated dispatch ID: {} for user {}", dispatchId, request.getUserId());

        return dispatchId;
    }
}
