package com.fanaujie.ripple.communication.msgqueue.kafka.serializer;

import com.fanaujie.ripple.communication.msgqueue.exception.DeserializerException;
import com.fanaujie.ripple.protobuf.storageupdater.StorageUpdatePayload;
import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.kafka.common.serialization.Deserializer;

import java.util.Map;

public class StorageUpdatePayloadDeserializer implements Deserializer<StorageUpdatePayload> {
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {}

    @Override
    public StorageUpdatePayload deserialize(String s, byte[] bytes) {
        try {
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            return StorageUpdatePayload.parseFrom(bytes);
        } catch (InvalidProtocolBufferException e) {
            throw new DeserializerException("Error when deserializing byte[] to StorageUpdatePayload");
        }
    }

    @Override
    public void close() {}
}
