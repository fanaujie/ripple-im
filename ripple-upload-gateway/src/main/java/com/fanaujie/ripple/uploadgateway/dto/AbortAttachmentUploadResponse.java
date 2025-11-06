package com.fanaujie.ripple.uploadgateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response for aborting attachment upload")
public class AbortAttachmentUploadResponse {

    @Schema(description = "Response code", example = "200")
    private int code;

    @Schema(description = "Response message", example = "Upload aborted successfully")
    private String message;
}
