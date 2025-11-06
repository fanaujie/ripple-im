package com.fanaujie.ripple.uploadgateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Upload completion data")
public class CompleteUploadData {
    @Schema(
            description = "URL of the uploaded file",
            example = "https://cdn.example.com/files/abc123.png")
    private String fileUrl;

    public static CompleteUploadData success(String fileUrl) {
        return CompleteUploadData.builder().fileUrl(fileUrl).build();
    }
}
