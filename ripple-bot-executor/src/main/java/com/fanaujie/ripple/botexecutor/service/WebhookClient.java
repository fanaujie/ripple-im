package com.fanaujie.ripple.botexecutor.service;

import com.fanaujie.ripple.botexecutor.config.BotExecutorConfig;
import com.fanaujie.ripple.botexecutor.model.WebhookRequest;
import com.fanaujie.ripple.botexecutor.model.WebhookResponse;
import com.fanaujie.ripple.botexecutor.sse.SseEventHandler;
import com.fanaujie.ripple.botexecutor.sse.SseParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * HTTP client for calling bot webhooks with retry and SSE support.
 */
public class WebhookClient {
    private static final Logger logger = LoggerFactory.getLogger(WebhookClient.class);
    private static final String CONTENT_TYPE_SSE = "text/event-stream";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final BotExecutorConfig config;

    public WebhookClient(ObjectMapper objectMapper, BotExecutorConfig config) {
        this.objectMapper = objectMapper;
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Call a webhook with retry logic.
     */
    public WebhookResponse callWebhook(String endpoint, String secret, String authToken,
                                        WebhookRequest payload, SseEventHandler sseHandler) {
        Exception lastException = null;

        for (int attempt = 0; attempt <= config.getWebhookMaxRetries(); attempt++) {
            if (attempt > 0) {
                long delay = calculateBackoffDelay(attempt);
                logger.info("Retrying webhook call, attempt {} after {}ms delay", attempt, delay);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return WebhookResponse.failure(0, "Interrupted during retry");
                }
            }

            try {
                WebhookResponse response = doCallWebhook(endpoint, secret, authToken, payload, sseHandler);
                if (response.isSuccess()) {
                    return response;
                }
                // Don't retry 4xx errors except 429
                if (response.getStatusCode() >= 400 && response.getStatusCode() < 500 && response.getStatusCode() != 429) {
                    return response;
                }
                lastException = new RuntimeException("HTTP " + response.getStatusCode() + ": " + response.getErrorMessage());
            } catch (Exception e) {
                lastException = e;
                logger.warn("Webhook call failed on attempt {}: {}", attempt, e.getMessage());
            }
        }

        logger.error("Webhook call failed after {} retries", config.getWebhookMaxRetries(), lastException);
        return WebhookResponse.failure(0, "Max retries exceeded: " +
                (lastException != null ? lastException.getMessage() : "Unknown error"));
    }

    private WebhookResponse doCallWebhook(String endpoint, String secret, String authToken,
                                           WebhookRequest payload, SseEventHandler sseHandler) throws Exception {
        String jsonBody = objectMapper.writeValueAsString(payload);
        long timestamp = System.currentTimeMillis() / 1000;
        String signature = calculateHmac(secret, timestamp + jsonBody);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(config.getSseTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("X-Ripple-Signature", "sha256=" + signature)
                .header("X-Ripple-Timestamp", String.valueOf(timestamp))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

        if (authToken != null && !authToken.isEmpty()) {
            requestBuilder.header("Authorization", "Bearer " + authToken);
        }

        HttpRequest request = requestBuilder.build();

        // First, get headers to check content type
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            return WebhookResponse.failure(response.statusCode(), errorBody);
        }

        String contentType = response.headers().firstValue("Content-Type").orElse("");

        if (contentType.contains(CONTENT_TYPE_SSE)) {
            // Handle SSE streaming
            handleSseResponse(response.body(), sseHandler);
            return WebhookResponse.streaming();
        } else {
            // Handle regular response
            String body = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            return WebhookResponse.success(body);
        }
    }

    private void handleSseResponse(InputStream inputStream, SseEventHandler handler) {
        SseParser parser = new SseParser(objectMapper);

        // Run parsing in a separate thread with timeout
        CompletableFuture<Void> parseFuture = CompletableFuture.runAsync(() -> {
            parser.parse(inputStream, handler);
        });

        try {
            parseFuture.get(config.getSseTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.warn("SSE stream timed out or failed", e);
            parser.cancel();
            handler.onError(e);
        }
    }

    private long calculateBackoffDelay(int attempt) {
        return (long) (config.getWebhookRetryBaseDelayMs() * Math.pow(config.getWebhookRetryMultiplier(), attempt - 1));
    }

    private String calculateHmac(String secret, String data) throws Exception {
        if (secret == null || secret.isEmpty()) return "";
        Mac sha256HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256HMAC.init(secretKey);
        byte[] hash = sha256HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
