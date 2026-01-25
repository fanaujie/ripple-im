package com.fanaujie.ripple.webhookservice.http;

import com.fanaujie.ripple.webhookservice.model.SSEEvent;
import com.fanaujie.ripple.webhookservice.model.WebhookRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class WebhookHttpClient {
    private static final Logger logger = LoggerFactory.getLogger(WebhookHttpClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final SSEEventParser sseParser;

    public WebhookHttpClient(int connectTimeoutMs, int readTimeoutMs) {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
        this.sseParser = new SSEEventParser();
    }

    public CompletableFuture<String> sendWithSSE(
            String webhookUrl,
            String apiKey,
            WebhookRequest request,
            Consumer<SSEEvent> onEvent) {

        CompletableFuture<String> future = new CompletableFuture<>();
        StringBuilder fullResponse = new StringBuilder();

        try {
            String jsonBody = objectMapper.writeValueAsString(request);

            Request.Builder requestBuilder = new Request.Builder()
                    .url(webhookUrl)
                    .post(RequestBody.create(jsonBody, JSON))
                    .header("Accept", "text/event-stream")
                    .header("Content-Type", "application/json");

            if (apiKey != null && !apiKey.isEmpty()) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }

            Request httpRequest = requestBuilder.build();

            EventSource.Factory factory = EventSources.createFactory(httpClient);
            EventSource eventSource = factory.newEventSource(httpRequest, new EventSourceListener() {
                @Override
                public void onOpen(EventSource eventSource, Response response) {
                    logger.debug("SSE connection opened for message {}", request.getMessageId());
                }

                @Override
                public void onEvent(EventSource eventSource, String id, String type, String data) {
                    logger.debug("SSE event: type={}, data={}", type, data);
                    SSEEvent event = sseParser.parse(type != null ? type : "delta", data);
                    if (event != null) {
                        if (event.isDelta()) {
                            fullResponse.append(event.getContent());
                        } else if (event.isDone()) {
                            // Use the full_text from done event if available, otherwise use accumulated
                            String finalText = event.getFullText() != null && !event.getFullText().isEmpty()
                                    ? event.getFullText()
                                    : fullResponse.toString();
                            future.complete(finalText);
                        }
                        onEvent.accept(event);
                    }
                }

                @Override
                public void onClosed(EventSource eventSource) {
                    logger.debug("SSE connection closed for message {}", request.getMessageId());
                    if (!future.isDone()) {
                        future.complete(fullResponse.toString());
                    }
                }

                @Override
                public void onFailure(EventSource eventSource, Throwable t, Response response) {
                    String errorMsg = t != null ? t.getMessage() : "Unknown error";
                    if (response != null) {
                        errorMsg += " (HTTP " + response.code() + ")";
                    }
                    logger.error("SSE connection failed for message {}: {}", request.getMessageId(), errorMsg);
                    onEvent.accept(SSEEvent.error(errorMsg));
                    future.completeExceptionally(t != null ? t : new IOException(errorMsg));
                }
            });

        } catch (Exception e) {
            logger.error("Failed to send webhook request: {}", e.getMessage(), e);
            future.completeExceptionally(e);
        }

        return future;
    }

    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
