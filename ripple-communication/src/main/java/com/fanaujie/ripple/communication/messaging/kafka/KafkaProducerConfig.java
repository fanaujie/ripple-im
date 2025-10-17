package com.fanaujie.ripple.communication.messaging.kafka;

public class KafkaProducerConfig {
    private final String bootstrapServers;
    private final String keySerializer;
    private final String valueSerializer;

    private KafkaProducerConfig(Builder builder) {
        this.bootstrapServers = builder.bootstrapServers;
        this.keySerializer = builder.keySerializer;
        this.valueSerializer = builder.valueSerializer;
    }

    public String getBootstrapServers() {
        return bootstrapServers;
    }

    public String getKeySerializer() {
        return keySerializer;
    }

    public String getValueSerializer() {
        return valueSerializer;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String bootstrapServers;
        private String keySerializer;
        private String valueSerializer;

        public Builder bootstrapServers(String bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
            return this;
        }

        public Builder keySerializer(String keySerializer) {
            this.keySerializer = keySerializer;
            return this;
        }

        public Builder valueSerializer(String valueSerializer) {
            this.valueSerializer = valueSerializer;
            return this;
        }

        public KafkaProducerConfig build() {
            if (bootstrapServers == null || bootstrapServers.isEmpty()) {
                throw new IllegalArgumentException("bootstrapServers is required");
            }
            if (keySerializer == null || keySerializer.isEmpty()) {
                throw new IllegalArgumentException("keySerializer is required");
            }
            if (valueSerializer == null || valueSerializer.isEmpty()) {
                throw new IllegalArgumentException("valueSerializer is required");
            }
            return new KafkaProducerConfig(this);
        }
    }
}
