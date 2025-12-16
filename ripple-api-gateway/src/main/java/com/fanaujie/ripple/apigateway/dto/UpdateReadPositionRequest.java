package com.fanaujie.ripple.apigateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to update read position in a conversation")
public class UpdateReadPositionRequest {

    @NotBlank(message = "Message ID is required")
    @Schema(description = "Message ID to mark as the last read message", example = "123456789", required = true)
    private String messageId;
}
