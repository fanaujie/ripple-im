package com.fanaujie.ripple.storage.model;

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
    private Instant createdAt;
    private Instant updatedAt;
}
