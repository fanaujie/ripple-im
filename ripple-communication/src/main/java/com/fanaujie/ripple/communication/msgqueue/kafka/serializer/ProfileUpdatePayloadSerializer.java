package com.fanaujie.ripple.communication.msgqueue.kafka.serializer;

import com.fanaujie.ripple.protobuf.profileupdater.ProfileUpdatePayload;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;

public class ProfileUpdatePayloadSerializer implements Serializer<ProfileUpdatePayload> {
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {}

    @Override
    public byte[] serialize(String s, ProfileUpdatePayload msg) {
        if (msg == null) {
            return null;
        }
        return msg.toByteArray();
    }

    @Override
    public void close() {}
}
