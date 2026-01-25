package com.fanaujie.ripple.apigateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BotSessionData {
    private String sessionId;
    private String botId;
    private long createdAt;
    private long lastActiveAt;
}
