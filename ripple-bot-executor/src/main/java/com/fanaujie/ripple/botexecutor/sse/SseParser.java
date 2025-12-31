package com.fanaujie.ripple.botexecutor.sse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Parser for Server-Sent Events (SSE) streams.
 */
public class SseParser {
    private static final Logger logger = LoggerFactory.getLogger(SseParser.class);
    private static final String DATA_PREFIX = "data:";
    private static final String DONE_MARKER = "[DONE]";

    private final ObjectMapper objectMapper;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public SseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parse an SSE stream and invoke the handler for each chunk.
     */
    public void parse(InputStream inputStream, SseEventHandler handler) {
        StringBuilder fullContent = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while (!cancelled.get() && (line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue; // SSE uses blank lines as event separators
                }

                if (line.startsWith(DATA_PREFIX)) {
                    String data = line.substring(DATA_PREFIX.length()).trim();

                    if (DONE_MARKER.equals(data)) {
                        handler.onComplete(fullContent.toString());
                        return;
                    }

                    String chunk = extractChunk(data);
                    if (chunk != null && !chunk.isEmpty()) {
                        fullContent.append(chunk);
                        handler.onChunk(chunk);
                    }
                }
                // Ignore other SSE fields like "event:", "id:", "retry:"
            }

            // Stream ended without [DONE] marker
            handler.onComplete(fullContent.toString());

        } catch (Exception e) {
            logger.error("Error parsing SSE stream", e);
            handler.onError(e);
        }
    }

    /**
     * Extract the chunk content from the data field.
     * Handles both raw text and JSON format: {"chunk": "text"}
     */
    private String extractChunk(String data) {
        if (data.startsWith("{")) {
            try {
                JsonNode node = objectMapper.readTree(data);
                if (node.has("chunk")) {
                    return node.get("chunk").asText();
                }
                if (node.has("content")) {
                    return node.get("content").asText();
                }
                if (node.has("text")) {
                    return node.get("text").asText();
                }
                // Return raw JSON if no known field
                return data;
            } catch (Exception e) {
                logger.debug("Failed to parse chunk as JSON, using raw: {}", data);
                return data;
            }
        }
        return data;
    }

    /**
     * Cancel the parsing operation.
     */
    public void cancel() {
        cancelled.set(true);
    }
}
