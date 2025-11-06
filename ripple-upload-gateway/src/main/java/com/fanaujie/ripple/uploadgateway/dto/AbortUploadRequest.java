package com.fanaujie.ripple.uploadgateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Request to abort multipart upload")
public class AbortUploadRequest {
    @NotBlank(message = "objectName is required")
    @Schema(description = "Object name in storage to abort", example = "uploads/2025/01/abc123.png", required = true)
    private String objectName;
}
