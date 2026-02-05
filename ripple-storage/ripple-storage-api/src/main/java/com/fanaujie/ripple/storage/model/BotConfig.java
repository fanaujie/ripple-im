package com.fanaujie.ripple.storage.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BotConfig {
    private long userId;
    private String webhookUrl;
    private String apiKey;
    private String description;
    private BotResponseMode responseMode;
    private Instant createdAt;
    private Instant updatedAt;

    @JsonIgnore
    public BotResponseMode getResponseModeOrDefault() {
        return responseMode != null ? responseMode : BotResponseMode.STREAMING;
    }
}
