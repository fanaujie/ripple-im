package com.fanaujie.ripple.communication.messaging.kafka;

public class KafkaConsumerConfig {
    private final String bootstrapServers;
    private final String groupId;
    private final String clientId;
    private final String topic;
    private final String keyDeserializer;
    private final String valueDeserializer;

    private KafkaConsumerConfig(Builder builder) {
        this.bootstrapServers = builder.bootstrapServers;
        this.groupId = builder.groupId;
        this.clientId = builder.clientId;
        this.topic = builder.topic;
        this.keyDeserializer = builder.keyDeserializer;
        this.valueDeserializer = builder.valueDeserializer;
    }

    public String getBootstrapServers() {
        return bootstrapServers;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getClientId() {
        return clientId;
    }

    public String getTopic() {
        return topic;
    }

    public String getKeyDeserializer() {
        return keyDeserializer;
    }

    public String getValueDeserializer() {
        return valueDeserializer;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String bootstrapServers;
        private String groupId;
        private String clientId;
        private String topic;
        private String keyDeserializer;
        private String valueDeserializer;

        public Builder bootstrapServers(String bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
            return this;
        }

        public Builder groupId(String groupId) {
            this.groupId = groupId;
            return this;
        }

        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder topic(String topic) {
            this.topic = topic;
            return this;
        }

        public Builder keyDeserializer(String keyDeserializer) {
            this.keyDeserializer = keyDeserializer;
            return this;
        }

        public Builder valueDeserializer(String valueDeserializer) {
            this.valueDeserializer = valueDeserializer;
            return this;
        }

        public KafkaConsumerConfig build() {
            if (bootstrapServers == null || bootstrapServers.isEmpty()) {
                throw new IllegalArgumentException("bootstrapServers is required");
            }
            if (groupId == null || groupId.isEmpty()) {
                throw new IllegalArgumentException("groupId is required");
            }
            if (topic == null || topic.isEmpty()) {
                throw new IllegalArgumentException("topic is required");
            }
            if (keyDeserializer == null || keyDeserializer.isEmpty()) {
                throw new IllegalArgumentException("keyDeserializer is required");
            }
            if (valueDeserializer == null || valueDeserializer.isEmpty()) {
                throw new IllegalArgumentException("valueDeserializer is required");
            }
            return new KafkaConsumerConfig(this);
        }
    }
}
