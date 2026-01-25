package com.fanaujie.ripple.webhookservice.http;

import com.fanaujie.ripple.webhookservice.model.SSEEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SSEEventParser {
    private static final Logger logger = LoggerFactory.getLogger(SSEEventParser.class);
    private final ObjectMapper objectMapper;

    public SSEEventParser() {
        this.objectMapper = new ObjectMapper();
    }

    public SSEEvent parse(String eventType, String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }

        try {
            JsonNode json = objectMapper.readTree(data);

            switch (eventType.toLowerCase()) {
                case "delta":
                    String content = json.has("content") ? json.get("content").asText() : "";
                    return SSEEvent.delta(content);

                case "done":
                    String fullText = json.has("full_text") ? json.get("full_text").asText() : "";
                    return SSEEvent.done(fullText);

                case "error":
                    String errorMsg = json.has("error") ? json.get("error").asText() : "Unknown error";
                    return SSEEvent.error(errorMsg);

                default:
                    logger.warn("Unknown SSE event type: {}", eventType);
                    return null;
            }
        } catch (Exception e) {
            logger.error("Failed to parse SSE event data: {}", data, e);
            return SSEEvent.error("Failed to parse response: " + e.getMessage());
        }
    }
}
