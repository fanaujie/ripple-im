package com.fanaujie.ripple.communication.msgqueue.kafka.serializer;

import com.fanaujie.ripple.protobuf.msgdispatcher.BotWebhookEvent;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;

public class BotWebhookEventSerializer implements Serializer<BotWebhookEvent> {
    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {}

    @Override
    public byte[] serialize(String s, BotWebhookEvent event) {
        if (event == null) {
            return null;
        }
        return event.toByteArray();
    }

    @Override
    public void close() {}
}
