package com.fanaujie.ripple.profileupdater.consumer;

import com.fanaujie.ripple.communication.msgqueue.MessageRecord;
import com.fanaujie.ripple.communication.processor.ProcessorDispatcher;
import com.fanaujie.ripple.protobuf.profileupdater.ProfileUpdatePayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ProfileUpdateConsumer {
    private static final Logger logger = LoggerFactory.getLogger(ProfileUpdateConsumer.class);

    private final ProcessorDispatcher<ProfileUpdatePayload.PayloadCase, ProfileUpdatePayload, Void>
            payloadRouter;

    public ProfileUpdateConsumer(
            ProcessorDispatcher<ProfileUpdatePayload.PayloadCase, ProfileUpdatePayload, Void>
                    payloadRouter) {
        this.payloadRouter = payloadRouter;
    }

    public void consumeBatch(List<MessageRecord<String, ProfileUpdatePayload>> records)
            throws Exception {
        for (MessageRecord<String, ProfileUpdatePayload> record : records) {
            try {
                consume(record.key(), record.value());
            } catch (Exception e) {
                logger.error("Error consuming message with key: {}", record.key(), e);
            }
        }
    }

    public void consume(String key, ProfileUpdatePayload payload) throws Exception {
        this.payloadRouter.dispatch(payload.getPayloadCase(), payload);
    }
}
