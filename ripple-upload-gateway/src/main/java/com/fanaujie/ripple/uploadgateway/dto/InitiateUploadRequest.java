package com.fanaujie.ripple.uploadgateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
@Schema(description = "Request to initiate file upload")
public class InitiateUploadRequest {
    @Positive(message = "fileSize must be positive")
    @Schema(description = "File size in bytes", example = "10485760", required = true)
    private int fileSize;

    @NotBlank(message = "fileSha256 is required")
    @Schema(
            description = "SHA256 hash of the file",
            example = "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e",
            required = true)
    private String fileSha256;

    @NotBlank(message = "originalFilename is required")
    @Schema(
            description = "Original filename with extension (used to determine file extension)",
            example = "document.pdf",
            required = true)
    private String originalFilename;
}
