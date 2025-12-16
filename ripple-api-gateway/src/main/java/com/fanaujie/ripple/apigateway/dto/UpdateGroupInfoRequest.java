package com.fanaujie.ripple.apigateway.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateGroupInfoRequest {
    @NotNull(message = "senderId is required")
    private String senderId;

    @NotEmpty(message = "value is required")
    private String value;
}
