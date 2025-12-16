package com.fanaujie.ripple.communication.msgqueue.kafka;

public class KafkaProducerConfigFactory {

    // Serializer class names
    private static final String STRING_SERIALIZER =
            "org.apache.kafka.common.serialization.StringSerializer";
    private static final String MESSAGE_PAYLOAD_SERIALIZER =
            "com.fanaujie.ripple.communication.msgqueue.kafka.serializer.MessagePayloadSerializer";
    private static final String PUSH_MESSAGE_SERIALIZER =
            "com.fanaujie.ripple.communication.msgqueue.kafka.serializer.PushMessageSerializer";
    private static final String STORAGE_UPDATE_PAYLOAD_SERIALIZER =
            "com.fanaujie.ripple.communication.msgqueue.kafka.serializer.StorageUpdatePayloadSerializer";

    public static KafkaProducerConfig createMessagePayloadProducerConfig(String bootstrapServers) {
        return KafkaProducerConfig.builder()
                .bootstrapServers(bootstrapServers)
                .keySerializer(STRING_SERIALIZER)
                .valueSerializer(MESSAGE_PAYLOAD_SERIALIZER)
                .build();
    }

    public static KafkaProducerConfig createPushMessageProducerConfig(String bootstrapServers) {
        return KafkaProducerConfig.builder()
                .bootstrapServers(bootstrapServers)
                .keySerializer(STRING_SERIALIZER)
                .valueSerializer(PUSH_MESSAGE_SERIALIZER)
                .build();
    }

    public static KafkaProducerConfig createStorageUpdatePayloadProducerConfig(
            String bootstrapServers) {
        return KafkaProducerConfig.builder()
                .bootstrapServers(bootstrapServers)
                .keySerializer(STRING_SERIALIZER)
                .valueSerializer(STORAGE_UPDATE_PAYLOAD_SERIALIZER)
                .build();
    }
}
