package com.fanaujie.ripple.webhookservice.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SSEEvent {
    public enum EventType {
        DELTA,
        DONE,
        ERROR,
        UNKNOWN
    }

    private EventType eventType;
    private String content;
    private String fullText;
    private String errorMessage;

    public SSEEvent() {}

    public static SSEEvent delta(String content) {
        SSEEvent event = new SSEEvent();
        event.eventType = EventType.DELTA;
        event.content = content;
        return event;
    }

    public static SSEEvent done(String fullText) {
        SSEEvent event = new SSEEvent();
        event.eventType = EventType.DONE;
        event.fullText = fullText;
        return event;
    }

    public static SSEEvent error(String errorMessage) {
        SSEEvent event = new SSEEvent();
        event.eventType = EventType.ERROR;
        event.errorMessage = errorMessage;
        return event;
    }

    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getFullText() { return fullText; }
    public void setFullText(String fullText) { this.fullText = fullText; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public boolean isDelta() { return eventType == EventType.DELTA; }
    public boolean isDone() { return eventType == EventType.DONE; }
    public boolean isError() { return eventType == EventType.ERROR; }

    // JSON data format from SSE
    public static class DeltaData {
        @JsonProperty("content")
        private String content;

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    public static class DoneData {
        @JsonProperty("full_text")
        private String fullText;

        public String getFullText() { return fullText; }
        public void setFullText(String fullText) { this.fullText = fullText; }
    }
}
