package com.fanaujie.ripple.communication.messaging.kafka;

public class KafkaConsumerConfigFactory {

    private static final String STRING_DESERIALIZER =
            "org.apache.kafka.common.serialization.StringDeserializer";
    private static final String MESSAGE_PAYLOAD_DESERIALIZER =
            "com.fanaujie.ripple.communication.messaging.kafka.serializer.MessagePayloadDeserializer";

    public static KafkaConsumerConfig createMessagePayloadConsumerConfig(
            String topic, String bootstrapServers, String groupId, String clientId) {
        return KafkaConsumerConfig.builder()
                .topic(topic)
                .bootstrapServers(bootstrapServers)
                .groupId(groupId)
                .clientId(clientId)
                .keyDeserializer(STRING_DESERIALIZER)
                .valueDeserializer(MESSAGE_PAYLOAD_DESERIALIZER)
                .build();
    }
}
