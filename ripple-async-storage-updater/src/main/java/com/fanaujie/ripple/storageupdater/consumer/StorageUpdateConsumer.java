package com.fanaujie.ripple.storageupdater.consumer;

import com.fanaujie.ripple.communication.msgqueue.MessageRecord;
import com.fanaujie.ripple.communication.processor.ProcessorDispatcher;
import com.fanaujie.ripple.protobuf.storageupdater.StorageUpdatePayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class StorageUpdateConsumer {
    private static final Logger logger = LoggerFactory.getLogger(StorageUpdateConsumer.class);

    private final ProcessorDispatcher<StorageUpdatePayload.PayloadCase, StorageUpdatePayload, Void>
            payloadRouter;

    public StorageUpdateConsumer(
            ProcessorDispatcher<StorageUpdatePayload.PayloadCase, StorageUpdatePayload, Void>
                    payloadRouter) {
        this.payloadRouter = payloadRouter;
    }

    public void consumeBatch(List<MessageRecord<String, StorageUpdatePayload>> records)
            throws Exception {
        for (MessageRecord<String, StorageUpdatePayload> record : records) {
            try {
                consume(record.key(), record.value());
            } catch (Exception e) {
                logger.error("Error consuming message with key: {}", record.key(), e);
            }
        }
    }

    public void consume(String key, StorageUpdatePayload payload) throws Exception {
        this.payloadRouter.dispatch(payload.getPayloadCase(), payload);
    }
}
