package com.fanaujie.ripple.apigateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BotRegistrationResponse {
    private int code;
    private String message;
    private BotRegistrationData data;
}
