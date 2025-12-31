package com.fanaujie.ripple.botexecutor.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Webhook request payload sent to bot endpoints.
 */
public class WebhookRequest {
    @JsonProperty("bot_id")
    private long botId;

    @JsonProperty("user_id")
    private long userId;

    @JsonProperty("user_name")
    private String userName;

    @JsonProperty("conversation_id")
    private String conversationId;

    @JsonProperty("message_id")
    private long messageId;

    @JsonProperty("content")
    private String content;

    @JsonProperty("history")
    private List<HistoryMessage> history;

    public WebhookRequest() {}

    public static Builder builder() {
        return new Builder();
    }

    public long getBotId() { return botId; }
    public long getUserId() { return userId; }
    public String getUserName() { return userName; }
    public String getConversationId() { return conversationId; }
    public long getMessageId() { return messageId; }
    public String getContent() { return content; }
    public List<HistoryMessage> getHistory() { return history; }

    public static class Builder {
        private final WebhookRequest request = new WebhookRequest();

        public Builder botId(long botId) { request.botId = botId; return this; }
        public Builder userId(long userId) { request.userId = userId; return this; }
        public Builder userName(String userName) { request.userName = userName; return this; }
        public Builder conversationId(String conversationId) { request.conversationId = conversationId; return this; }
        public Builder messageId(long messageId) { request.messageId = messageId; return this; }
        public Builder content(String content) { request.content = content; return this; }
        public Builder history(List<HistoryMessage> history) { request.history = history; return this; }

        public WebhookRequest build() { return request; }
    }

    public static class HistoryMessage {
        @JsonProperty("role")
        private String role;

        @JsonProperty("content")
        private String content;

        public HistoryMessage() {}

        public HistoryMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() { return role; }
        public String getContent() { return content; }
    }
}
