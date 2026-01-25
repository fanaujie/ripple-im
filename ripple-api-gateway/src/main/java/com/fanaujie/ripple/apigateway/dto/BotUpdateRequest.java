package com.fanaujie.ripple.apigateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BotUpdateRequest {
    private String webhookUrl;
    private String apiKey;
    private String description;
}
