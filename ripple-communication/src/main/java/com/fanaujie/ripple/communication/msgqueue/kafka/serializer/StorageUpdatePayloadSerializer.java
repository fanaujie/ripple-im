package com.fanaujie.ripple.communication.msgqueue.kafka.serializer;

import com.fanaujie.ripple.protobuf.storageupdater.StorageUpdatePayload;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;

public class StorageUpdatePayloadSerializer implements Serializer<StorageUpdatePayload> {
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {}

    @Override
    public byte[] serialize(String s, StorageUpdatePayload msg) {
        if (msg == null) {
            return null;
        }
        return msg.toByteArray();
    }

    @Override
    public void close() {}
}
