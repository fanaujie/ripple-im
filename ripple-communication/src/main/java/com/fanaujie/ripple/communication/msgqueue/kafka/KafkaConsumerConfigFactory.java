package com.fanaujie.ripple.communication.msgqueue.kafka;

public class KafkaConsumerConfigFactory {

    private static final String STRING_DESERIALIZER =
            "org.apache.kafka.common.serialization.StringDeserializer";
    private static final String MESSAGE_PAYLOAD_DESERIALIZER =
            "com.fanaujie.ripple.communication.msgqueue.kafka.serializer.MessagePayloadDeserializer";
    private static final String PUSH_MESSAGE_DESERIALIZER =
            "com.fanaujie.ripple.communication.msgqueue.kafka.serializer.PushMessageDeserializer";
    private static final String STORAGE_UPDATE_PAYLOAD_DESERIALIZER =
            "com.fanaujie.ripple.communication.msgqueue.kafka.serializer.StorageUpdatePayloadDeserializer";

    public static KafkaConsumerConfig createMessagePayloadConsumerConfig(
            String topic,
            String bootstrapServers,
            String groupId,
            String clientId,
            int maxPollRecords,
            int fetchMinBytes,
            int fetchMaxWaitMs) {
        return KafkaConsumerConfig.builder()
                .topic(topic)
                .bootstrapServers(bootstrapServers)
                .groupId(groupId)
                .clientId(clientId)
                .keyDeserializer(STRING_DESERIALIZER)
                .valueDeserializer(MESSAGE_PAYLOAD_DESERIALIZER)
                .maxPollRecords(maxPollRecords)
                .fetchMinBytes(fetchMinBytes)
                .fetchMaxWaitMs(fetchMaxWaitMs)
                .build();
    }

    public static KafkaConsumerConfig createPushMessageConsumerConfig(
            String topic,
            String bootstrapServers,
            String groupId,
            String clientId,
            int maxPollRecords,
            int fetchMinBytes,
            int fetchMaxWaitMs) {
        return KafkaConsumerConfig.builder()
                .topic(topic)
                .bootstrapServers(bootstrapServers)
                .groupId(groupId)
                .clientId(clientId)
                .keyDeserializer(STRING_DESERIALIZER)
                .valueDeserializer(PUSH_MESSAGE_DESERIALIZER)
                .maxPollRecords(maxPollRecords)
                .fetchMinBytes(fetchMinBytes)
                .fetchMaxWaitMs(fetchMaxWaitMs)
                .build();
    }

    public static KafkaConsumerConfig createStorageUpdatePayloadConsumerConfig(
            String topic,
            String bootstrapServers,
            String groupId,
            String clientId,
            int maxPollRecords,
            int fetchMinBytes,
            int fetchMaxWaitMs) {
        return KafkaConsumerConfig.builder()
                .topic(topic)
                .bootstrapServers(bootstrapServers)
                .groupId(groupId)
                .clientId(clientId)
                .keyDeserializer(STRING_DESERIALIZER)
                .valueDeserializer(STORAGE_UPDATE_PAYLOAD_DESERIALIZER)
                .maxPollRecords(maxPollRecords)
                .fetchMinBytes(fetchMinBytes)
                .fetchMaxWaitMs(fetchMaxWaitMs)
                .build();
    }
}
