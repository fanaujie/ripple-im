package com.fanaujie.ripple.integration.mock;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Mock HTTP server for testing bot webhooks.
 * Captures incoming requests and returns configurable responses.
 */
public class MockWebhookServer implements AutoCloseable {

    private HttpServer server;
    private int port;
    private final List<CapturedRequest> capturedRequests = new CopyOnWriteArrayList<>();
    private Function<CapturedRequest, WebhookResponse> responseHandler;

    public MockWebhookServer() {
        this.responseHandler = req -> WebhookResponse.text("OK");
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.createContext("/webhook", new WebhookHandler());
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Override
    public void close() {
        stop();
    }

    public String getEndpoint() {
        return "http://localhost:" + port + "/webhook";
    }

    public int getPort() {
        return port;
    }

    /**
     * Set the response handler for incoming requests.
     */
    public void setResponseHandler(Function<CapturedRequest, WebhookResponse> handler) {
        this.responseHandler = handler;
    }

    /**
     * Configure to return a simple text response.
     */
    public void respondWith(String text) {
        this.responseHandler = req -> WebhookResponse.text(text);
    }

    /**
     * Configure to return an SSE stream response.
     */
    public void respondWithSSE(List<String> chunks) {
        this.responseHandler = req -> WebhookResponse.sse(chunks);
    }

    /**
     * Configure to return an error response.
     */
    public void respondWithError(int statusCode, String message) {
        this.responseHandler = req -> WebhookResponse.error(statusCode, message);
    }

    /**
     * Get all captured requests.
     */
    public List<CapturedRequest> getCapturedRequests() {
        return new ArrayList<>(capturedRequests);
    }

    /**
     * Get the last captured request.
     */
    public CapturedRequest getLastRequest() {
        if (capturedRequests.isEmpty()) return null;
        return capturedRequests.get(capturedRequests.size() - 1);
    }

    /**
     * Clear captured requests.
     */
    public void clear() {
        capturedRequests.clear();
    }

    private class WebhookHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Capture request
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String signature = exchange.getRequestHeaders().getFirst("X-Ripple-Signature");
            String timestamp = exchange.getRequestHeaders().getFirst("X-Ripple-Timestamp");
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");

            CapturedRequest request = new CapturedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().toString(),
                    body,
                    signature,
                    timestamp,
                    authorization
            );
            capturedRequests.add(request);

            // Generate response
            WebhookResponse response = responseHandler.apply(request);

            if (response.isSSE()) {
                // SSE response
                exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
                exchange.getResponseHeaders().add("Cache-Control", "no-cache");
                exchange.sendResponseHeaders(200, 0);

                try (OutputStream os = exchange.getResponseBody();
                     PrintWriter writer = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8), true)) {
                    for (String chunk : response.getSseChunks()) {
                        writer.println("data: " + chunk);
                        writer.println();
                        writer.flush();
                        Thread.sleep(10); // Small delay between chunks
                    }
                    writer.println("data: [DONE]");
                    writer.println();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else {
                // Regular response
                byte[] responseBytes = response.getBody().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(response.getStatusCode(), responseBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
            }
        }
    }

    /**
     * Captured webhook request.
     */
    public record CapturedRequest(
            String method,
            String uri,
            String body,
            String signature,
            String timestamp,
            String authorization
    ) {
        public boolean hasAuthorization() {
            return authorization != null && !authorization.isEmpty();
        }
    }

    /**
     * Webhook response configuration.
     */
    public static class WebhookResponse {
        private final int statusCode;
        private final String body;
        private final boolean sse;
        private final List<String> sseChunks;

        private WebhookResponse(int statusCode, String body, boolean sse, List<String> sseChunks) {
            this.statusCode = statusCode;
            this.body = body;
            this.sse = sse;
            this.sseChunks = sseChunks;
        }

        public static WebhookResponse text(String text) {
            return new WebhookResponse(200, text, false, null);
        }

        public static WebhookResponse sse(List<String> chunks) {
            return new WebhookResponse(200, null, true, chunks);
        }

        public static WebhookResponse error(int statusCode, String message) {
            return new WebhookResponse(statusCode, message, false, null);
        }

        public int getStatusCode() { return statusCode; }
        public String getBody() { return body; }
        public boolean isSSE() { return sse; }
        public List<String> getSseChunks() { return sseChunks; }
    }
}
