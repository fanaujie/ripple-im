package com.fanaujie.ripple.communication.msgqueue.kafka.serializer;

import com.fanaujie.ripple.communication.msgqueue.exception.DeserializerException;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;
import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.kafka.common.serialization.Deserializer;

import java.util.Map;

public class MessagePayloadDeserializer implements Deserializer<MessagePayload> {
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {}

    @Override
    public MessagePayload deserialize(String s, byte[] bytes) {
        try {
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            return MessagePayload.parseFrom(bytes);
        } catch (InvalidProtocolBufferException e) {
            throw new DeserializerException("Error when deserializing byte[] to MessagePayload");
        }
    }

    @Override
    public void close() {}
}
