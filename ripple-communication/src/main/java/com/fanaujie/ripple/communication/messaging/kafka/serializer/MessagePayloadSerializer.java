package com.fanaujie.ripple.communication.messaging.kafka.serializer;

import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;

public class MessagePayloadSerializer implements Serializer<MessagePayload> {
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {}

    @Override
    public byte[] serialize(String s, MessagePayload msgPayload) {
        if (msgPayload == null) {
            return null;
        }
        return msgPayload.toByteArray();
    }

    @Override
    public void close() {}
}
