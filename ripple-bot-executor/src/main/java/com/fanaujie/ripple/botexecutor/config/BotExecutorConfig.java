package com.fanaujie.ripple.botexecutor.config;

/**
 * Configuration for bot executor service.
 */
public class BotExecutorConfig {
    private final int webhookMaxRetries;
    private final long webhookRetryBaseDelayMs;
    private final double webhookRetryMultiplier;
    private final long sseTimeoutSeconds;
    private final long pendingMessageTtlSeconds;

    public BotExecutorConfig(int webhookMaxRetries, long webhookRetryBaseDelayMs, double webhookRetryMultiplier,
                             long sseTimeoutSeconds, long pendingMessageTtlSeconds) {
        this.webhookMaxRetries = webhookMaxRetries;
        this.webhookRetryBaseDelayMs = webhookRetryBaseDelayMs;
        this.webhookRetryMultiplier = webhookRetryMultiplier;
        this.sseTimeoutSeconds = sseTimeoutSeconds;
        this.pendingMessageTtlSeconds = pendingMessageTtlSeconds;
    }

    public int getWebhookMaxRetries() { return webhookMaxRetries; }
    public long getWebhookRetryBaseDelayMs() { return webhookRetryBaseDelayMs; }
    public double getWebhookRetryMultiplier() { return webhookRetryMultiplier; }
    public long getSseTimeoutSeconds() { return sseTimeoutSeconds; }
    public long getPendingMessageTtlSeconds() { return pendingMessageTtlSeconds; }

    public static BotExecutorConfig fromTypesafeConfig(com.typesafe.config.Config config) {
        return new BotExecutorConfig(
            config.getInt("bot-executor.webhook.max-retries"),
            config.getLong("bot-executor.webhook.retry-base-delay-ms"),
            config.getDouble("bot-executor.webhook.retry-multiplier"),
            config.getLong("bot-executor.sse.timeout-seconds"),
            config.getLong("bot-executor.pending-message.ttl-seconds")
        );
    }
}
