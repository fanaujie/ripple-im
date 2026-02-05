package com.fanaujie.ripple.communication.msgqueue.kafka.serializer;

import com.fanaujie.ripple.communication.msgqueue.exception.DeserializerException;
import com.fanaujie.ripple.protobuf.msgdispatcher.BotWebhookEvent;
import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.kafka.common.serialization.Deserializer;

import java.util.Map;

public class BotWebhookEventDeserializer implements Deserializer<BotWebhookEvent> {
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {}

    @Override
    public BotWebhookEvent deserialize(String s, byte[] bytes) {
        try {
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            return BotWebhookEvent.parseFrom(bytes);
        } catch (InvalidProtocolBufferException e) {
            throw new DeserializerException("Error when deserializing byte[] to BotWebhookEvent");
        }
    }

    @Override
    public void close() {}
}
