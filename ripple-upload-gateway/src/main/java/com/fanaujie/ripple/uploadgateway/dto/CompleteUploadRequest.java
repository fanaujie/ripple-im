package com.fanaujie.ripple.uploadgateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
@Schema(description = "Request to complete multipart upload")
public class CompleteUploadRequest {
    @NotBlank(message = "objectName is required")
    @Schema(description = "Object name in storage", example = "uploads/2025/01/abc123.png", required = true)
    private String objectName;

    @Positive(message = "totalChunks must be positive")
    @Schema(description = "Total number of chunks uploaded", example = "10", required = true)
    private int totalChunks;
}
