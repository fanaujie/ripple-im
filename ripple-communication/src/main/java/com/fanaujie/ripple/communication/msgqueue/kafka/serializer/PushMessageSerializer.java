package com.fanaujie.ripple.communication.msgqueue.kafka.serializer;

import com.fanaujie.ripple.protobuf.push.PushMessage;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;

public class PushMessageSerializer implements Serializer<PushMessage> {
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {}

    @Override
    public byte[] serialize(String s, PushMessage msg) {
        if (msg == null) {
            return null;
        }
        return msg.toByteArray();
    }

    @Override
    public void close() {}
}
