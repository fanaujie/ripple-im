package com.fanaujie.ripple.uploadgateway.controller;

import com.fanaujie.ripple.uploadgateway.dto.*;
import com.fanaujie.ripple.uploadgateway.exception.FileSizeExceededException;
import com.fanaujie.ripple.uploadgateway.exception.InvalidFileExtensionException;
import com.fanaujie.ripple.uploadgateway.service.ChunkUploadService;
import com.fanaujie.ripple.uploadgateway.utils.FileUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/upload/attachment")
@Slf4j
@Tag(name = "Message Attachment", description = "Message attachment upload APIs")
public class MessageAttachmentController {

    private final ChunkUploadService chunkUploadService;
    private final FileUtils messageAttachmentFileUtils;

    public MessageAttachmentController(
            ChunkUploadService chunkUploadService,
            @Qualifier("messageAttachmentFileUtils") FileUtils messageAttachmentFileUtils) {
        this.chunkUploadService = chunkUploadService;
        this.messageAttachmentFileUtils = messageAttachmentFileUtils;
    }

    @PostMapping("/initiate")
    @Operation(
            summary = "Initiate file upload",
            description =
                    "Initialize file upload by providing file metadata. The server will determine if the file already exists (returns URL), requires single upload (<5MB), or chunked upload (>5MB).",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Upload initiated successfully",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @io.swagger.v3.oas.annotations.media.Schema(
                                                        implementation =
                                                                InitiateAttachmentUploadResponse
                                                                        .class))),
                @ApiResponse(responseCode = "400", description = "Invalid request parameters")
            })
    public ResponseEntity<InitiateAttachmentUploadResponse> initiateUpload(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            description = "File metadata for upload initiation",
                            required = true)
                    @Valid
                    @RequestBody
                    InitiateUploadRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        try {
            messageAttachmentFileUtils.validateFileSize(request.getFileSize());
            String extension =
                    messageAttachmentFileUtils.extractExtension(request.getOriginalFilename());
            String objectName = request.getFileSha256() + extension;
            return ResponseEntity.ok(
                    new InitiateAttachmentUploadResponse(
                            200,
                            "success",
                            chunkUploadService.initiateUpload(request, objectName)));
        } catch (FileSizeExceededException | InvalidFileExtensionException e) {
            return ResponseEntity.badRequest()
                    .body(
                            new InitiateAttachmentUploadResponse(
                                    400, "Invalid file parameters", null));
        }
    }

    @PutMapping(value = "/chunk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload chunk",
            description =
                    "Upload a single chunk of the file during multipart upload. Each chunk is verified using SHA256 hash.",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Chunk uploaded successfully",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                UploadChunkResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Invalid chunk data or hash mismatch"),
                @ApiResponse(
                        responseCode = "500",
                        description = "Failed to upload chunk to storage")
            })
    public ResponseEntity<UploadChunkResponse> uploadChunk(
            @Parameter(
                            description = "Object name in storage (from initiate response)",
                            required = true,
                            example = "uploads/2025/01/abc123.png")
                    @RequestParam("objectName")
                    String objectName,
            @Parameter(
                            description = "Chunk sequence number (starting from 1)",
                            required = true,
                            example = "1")
                    @RequestParam("chunkNumber")
                    int chunkNumber,
            @Parameter(
                            description = "SHA256 hash of the chunk data",
                            required = true,
                            example =
                                    "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e")
                    @RequestParam("chunkSha256")
                    String chunkSha256,
            @Parameter(description = "Chunk file data", required = true) @RequestParam("chunk")
                    MultipartFile chunk,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        byte[] chunkData = null;
        try {
            chunkData = chunk.getBytes();
        } catch (IOException e) {
            log.error("Failed to read chunk data", e);
            return ResponseEntity.badRequest()
                    .body(new UploadChunkResponse(400, "Failed to read chunk data"));
        }
        String calculatedSha256 = DigestUtils.sha256Hex(chunkData);
        if (!calculatedSha256.equalsIgnoreCase(chunkSha256)) {
            return ResponseEntity.badRequest()
                    .body(new UploadChunkResponse(400, "Chunk hash mismatch"));
        }
        if (chunkUploadService.uploadChunkPart(objectName, chunkNumber, chunkData) == null) {
            return ResponseEntity.internalServerError()
                    .body(new UploadChunkResponse(500, " Failed to upload chunk to storage"));
        }
        return ResponseEntity.ok(new UploadChunkResponse(200, "success"));
    }

    @PostMapping("/chunk/complete")
    @Operation(
            summary = "Complete upload",
            description =
                    "Complete multipart upload by merging all chunks. Returns the final file URL.",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Upload completed successfully",
                        content =
                                @io.swagger.v3.oas.annotations.media.Content(
                                        mediaType = "application/json",
                                        schema =
                                                @io.swagger.v3.oas.annotations.media.Schema(
                                                        implementation =
                                                                CompleteAttachmentUploadResponse
                                                                        .class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Invalid request or missing chunks"),
                @ApiResponse(responseCode = "500", description = "Failed to complete upload")
            })
    public ResponseEntity<CompleteAttachmentUploadResponse> completeUpload(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            description =
                                    "Upload completion request with object name and total chunks",
                            required = true)
                    @Valid
                    @RequestBody
                    CompleteUploadRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {

        CompleteUploadData response = chunkUploadService.completeUpload(request);
        if (response == null) {
            return ResponseEntity.badRequest()
                    .body(
                            new CompleteAttachmentUploadResponse(
                                    500, "Failed to complete upload", null));
        }
        return ResponseEntity.ok(new CompleteAttachmentUploadResponse(200, "success", response));
    }

    @PutMapping(value = "/single", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Simple upload",
            description =
                    "Upload small files (<5MB) in a single request. The file is verified using SHA256 hash and uploaded directly without chunking.",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "File uploaded successfully",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                SingleAttachmentUploadResponse
                                                                        .class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Invalid file data or hash mismatch")
            })
    public ResponseEntity<SingleAttachmentUploadResponse> singleUpload(
            @Parameter(
                            description = "Object name in storage (from initiate response)",
                            required = true,
                            example = "uploads/2025/01/abc123.png")
                    @RequestParam("objectName")
                    String objectName,
            @Parameter(
                            description = "SHA256 hash of the file",
                            required = true,
                            example =
                                    "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e")
                    @RequestParam("fileSha256")
                    String fileSha256,
            @Parameter(description = "File data", required = true) @RequestParam("file")
                    MultipartFile file,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        byte[] fileData = null;
        try {
            fileData = file.getBytes();
        } catch (IOException e) {
            log.error("Failed to read file data", e);
            return ResponseEntity.badRequest()
                    .body(
                            new SingleAttachmentUploadResponse(
                                    400, "Failed to read file data", null));
        }
        String calculatedSha256 = DigestUtils.sha256Hex(fileData);
        if (!calculatedSha256.equalsIgnoreCase(fileSha256)) {
            return ResponseEntity.badRequest()
                    .body(new SingleAttachmentUploadResponse(400, "File hash mismatch", null));
        }
        CompleteUploadData response = chunkUploadService.singleUpload(objectName, fileData);
        if (response == null) {
            return ResponseEntity.internalServerError()
                    .body(new SingleAttachmentUploadResponse(500, "Failed to upload file", null));
        }
        return ResponseEntity.ok(new SingleAttachmentUploadResponse(200, "success", response));
    }

    @DeleteMapping("/abort")
    @Operation(
            summary = "Abort upload",
            description =
                    "Cancel ongoing multipart upload and cleanup uploaded chunks. This should be called when the user cancels the upload or an error occurs.",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Upload aborted successfully",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                AbortAttachmentUploadResponse
                                                                        .class))),
                @ApiResponse(responseCode = "400", description = "Invalid request"),
                @ApiResponse(responseCode = "500", description = "Failed to abort upload")
            })
    public ResponseEntity<AbortAttachmentUploadResponse> abortUpload(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            description = "Abort request with object name to cancel",
                            required = true)
                    @Valid
                    @RequestBody
                    AbortUploadRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        try {
            chunkUploadService.abortUpload(request);
            return ResponseEntity.ok(new AbortAttachmentUploadResponse(200, "success"));
        } catch (Exception e) {
            log.error("Failed to abort upload", e);
            return ResponseEntity.internalServerError()
                    .body(new AbortAttachmentUploadResponse(500, "Failed to abort upload"));
        }
    }
}
