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
@Schema(description = "Upload initiation data")
public class InitiateUploadData {

    public enum UploadMode {
        AlreadyExits(0),
        SINGLE(1),
        CHUNK(2);

        UploadMode(int code) {
            this.code = code;
        }

        private final int code;

        public int getCode() {
            return code;
        }
    }

    @Schema(
            description = "Upload mode: 0=Already exists, 1=Single upload, 2=Chunk upload",
            example = "2")
    private int uploadMode;

    @Schema(description = "Size of each chunk in bytes (for chunk mode)", example = "5242880")
    private long chunkSize;

    @Schema(description = "Total number of chunks (for chunk mode)", example = "10")
    private int totalChunks;

    @Schema(description = "Starting chunk number (for chunk mode)", example = "1")
    private int startChunkNumber;

    @Schema(
            description = "Object name in storage (for chunk mode)",
            example = "uploads/2025/01/abc123.png")
    private String objectName;

    @Schema(
            description = "File URL (if file already exists)",
            example = "https://cdn.example.com/files/abc123.png")
    private String fileUrl;

    public static InitiateUploadData alreadyExists(String fileUrl) {
        return InitiateUploadData.builder()
                .uploadMode(UploadMode.AlreadyExits.getCode())
                .fileUrl(fileUrl)
                .build();
    }

    public static InitiateUploadData singleMode(String objectName) {
        return InitiateUploadData.builder()
                .uploadMode(UploadMode.SINGLE.getCode())
                .objectName(objectName)
                .build();
    }

    public static InitiateUploadData chunkMode(
            String objectName, long chunkSize, int startChunkNumber, int totalChunks) {
        return InitiateUploadData.builder()
                .uploadMode(UploadMode.CHUNK.getCode())
                .objectName(objectName)
                .chunkSize(chunkSize)
                .startChunkNumber(startChunkNumber)
                .totalChunks(totalChunks)
                .build();
    }
}
