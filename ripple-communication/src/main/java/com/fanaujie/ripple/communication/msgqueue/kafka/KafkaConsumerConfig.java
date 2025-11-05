package com.fanaujie.ripple.communication.msgqueue.kafka;

public class KafkaConsumerConfig {
    private final String bootstrapServers;
    private final String groupId;
    private final String clientId;
    private final String topic;
    private final String keyDeserializer;
    private final String valueDeserializer;
    private final int maxPollRecords;
    private final int fetchMinBytes;
    private final int fetchMaxWaitMs;

    private KafkaConsumerConfig(Builder builder) {
        this.bootstrapServers = builder.bootstrapServers;
        this.groupId = builder.groupId;
        this.clientId = builder.clientId;
        this.topic = builder.topic;
        this.keyDeserializer = builder.keyDeserializer;
        this.valueDeserializer = builder.valueDeserializer;
        this.maxPollRecords = builder.maxPollRecords;
        this.fetchMinBytes = builder.fetchMinBytes;
        this.fetchMaxWaitMs = builder.fetchMaxWaitMs;
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

    public int getMaxPollRecords() {
        return maxPollRecords;
    }

    public int getFetchMinBytes() {
        return fetchMinBytes;
    }

    public int getFetchMaxWaitMs() {
        return fetchMaxWaitMs;
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
        private int maxPollRecords;
        private int fetchMinBytes;
        private int fetchMaxWaitMs;

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

        public Builder maxPollRecords(int maxPollRecords) {
            this.maxPollRecords = maxPollRecords;
            return this;
        }

        public Builder fetchMinBytes(int fetchMinBytes) {
            this.fetchMinBytes = fetchMinBytes;
            return this;
        }

        public Builder fetchMaxWaitMs(int fetchMaxWaitMs) {
            this.fetchMaxWaitMs = fetchMaxWaitMs;
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
            if (maxPollRecords <= 0) {
                throw new IllegalArgumentException("maxPollRecords must be greater than 0");
            }
            if (fetchMinBytes < 0) {
                throw new IllegalArgumentException("fetchMinBytes must be non-negative");
            }
            if (fetchMaxWaitMs < 0) {
                throw new IllegalArgumentException("fetchMaxWaitMs must be non-negative");
            }
            return new KafkaConsumerConfig(this);
        }
    }
}
