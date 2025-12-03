package com.fanaujie.ripple.communication.msgqueue.kafka.serializer;

import com.fanaujie.ripple.communication.msgqueue.exception.DeserializerException;
import com.fanaujie.ripple.protobuf.profileupdater.ProfileUpdatePayload;
import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.kafka.common.serialization.Deserializer;

import java.util.Map;

public class ProfileUpdatePayloadDeserializer implements Deserializer<ProfileUpdatePayload> {
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {}

    @Override
    public ProfileUpdatePayload deserialize(String s, byte[] bytes) {
        try {
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            return ProfileUpdatePayload.parseFrom(bytes);
        } catch (InvalidProtocolBufferException e) {
            throw new DeserializerException("Error when deserializing byte[] to ProfileUpdatePayload");
        }
    }

    @Override
    public void close() {}
}
