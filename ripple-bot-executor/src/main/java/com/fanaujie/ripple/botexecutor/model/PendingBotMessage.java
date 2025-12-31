package com.fanaujie.ripple.botexecutor.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a message pending delivery due to authentication requirements.
 */
public class PendingBotMessage {
    @JsonProperty("bot_id")
    private long botId;

    @JsonProperty("user_id")
    private long userId;

    @JsonProperty("message_data")
    private byte[] messageData; // Serialized MessageData protobuf

    @JsonProperty("created_at")
    private long createdAt;

    public PendingBotMessage() {}

    public PendingBotMessage(long botId, long userId, byte[] messageData) {
        this.botId = botId;
        this.userId = userId;
        this.messageData = messageData;
        this.createdAt = System.currentTimeMillis();
    }

    public long getBotId() { return botId; }
    public long getUserId() { return userId; }
    public byte[] getMessageData() { return messageData; }
    public long getCreatedAt() { return createdAt; }

    public void setBotId(long botId) { this.botId = botId; }
    public void setUserId(long userId) { this.userId = userId; }
    public void setMessageData(byte[] messageData) { this.messageData = messageData; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
