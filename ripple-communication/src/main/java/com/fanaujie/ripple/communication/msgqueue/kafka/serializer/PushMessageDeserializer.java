package com.fanaujie.ripple.communication.msgqueue.kafka.serializer;

import com.fanaujie.ripple.communication.msgqueue.exception.DeserializerException;
import com.fanaujie.ripple.protobuf.push.PushMessage;
import com.google.protobuf.InvalidProtocolBufferException;

import org.apache.kafka.common.serialization.Deserializer;

import java.util.Map;

public class PushMessageDeserializer implements Deserializer<PushMessage> {
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {}

    @Override
    public PushMessage deserialize(String s, byte[] bytes) {
        try {
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            return PushMessage.parseFrom(bytes);
        } catch (InvalidProtocolBufferException e) {
            throw new DeserializerException("Error when deserializing byte[] to PushMessage");
        }
    }

    @Override
    public void close() {}
}
