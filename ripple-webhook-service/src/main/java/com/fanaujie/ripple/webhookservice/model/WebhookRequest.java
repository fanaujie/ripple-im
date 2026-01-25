package com.fanaujie.ripple.webhookservice.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class WebhookRequest {
    @JsonProperty("event")
    private String event = "message";

    @JsonProperty("message_id")
    private long messageId;

    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("user")
    private UserInfo user;

    @JsonProperty("message")
    private MessageInfo message;

    public static class UserInfo {
        @JsonProperty("id")
        private String id;

        public UserInfo() {}

        public UserInfo(String id) {
            this.id = id;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
    }

    public static class MessageInfo {
        @JsonProperty("text")
        private String text;

        @JsonProperty("timestamp")
        private long timestamp;

        public MessageInfo() {}

        public MessageInfo(String text, long timestamp) {
            this.text = text;
            this.timestamp = timestamp;
        }

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }

    public WebhookRequest() {}

    public static WebhookRequest create(
            long messageId,
            String sessionId,
            String userId,
            String messageText,
            long timestamp) {
        WebhookRequest request = new WebhookRequest();
        request.messageId = messageId;
        request.sessionId = sessionId;
        request.user = new UserInfo(userId);
        request.message = new MessageInfo(messageText, timestamp);
        return request;
    }

    public String getEvent() { return event; }
    public void setEvent(String event) { this.event = event; }
    public long getMessageId() { return messageId; }
    public void setMessageId(long messageId) { this.messageId = messageId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public UserInfo getUser() { return user; }
    public void setUser(UserInfo user) { this.user = user; }
    public MessageInfo getMessage() { return message; }
    public void setMessage(MessageInfo message) { this.message = message; }
}
