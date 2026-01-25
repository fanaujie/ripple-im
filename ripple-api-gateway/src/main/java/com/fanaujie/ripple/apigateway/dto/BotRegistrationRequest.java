package com.fanaujie.ripple.apigateway.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BotRegistrationRequest {
    @NotBlank(message = "Account is required")
    private String account;

    @NotBlank(message = "Display name is required")
    private String displayName;

    private String avatar;

    @NotBlank(message = "Webhook URL is required")
    private String webhookUrl;

    private String apiKey;

    private String description;
}
