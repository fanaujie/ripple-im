package com.fanaujie.ripple.uploadgateway.controller;

import com.fanaujie.ripple.uploadgateway.config.AvatarProperties;
import com.fanaujie.ripple.uploadgateway.dto.AvatarUploadResponse;
import com.fanaujie.ripple.uploadgateway.service.AvatarUploadService;
import com.fanaujie.ripple.uploadgateway.service.AvatarFileValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "File Upload", description = "APIs for uploading avatars and multimedia files")
@SecurityRequirement(name = "bearerAuth")
public class UploadController {

    private final AvatarUploadService avatarUploadService;
    private final AvatarFileValidationService fileValidationService;
    private final AvatarProperties avatarProperties;

    @PutMapping(value = "/avatar", consumes = "multipart/form-data", produces = "application/json")
    @Operation(
        summary = "Upload user avatar",
        description = "Upload and set user avatar, supports JPEG and PNG formats, file size limit 5MB, maximum resolution 460x460"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully uploaded avatar",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = AvatarUploadResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400", 
            description = "Unsupported file format, file size exceeds limit, file hash validation failed, or file resolution exceeds limit",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = AvatarUploadResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized access",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = AvatarUploadResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = AvatarUploadResponse.class)
            )
        )
    })
    public ResponseEntity<AvatarUploadResponse> uploadAvatar(
            @Parameter(description = "File SHA-256 hash value for file integrity verification") @RequestPart("hash") String hash,
            @Parameter(description = "Avatar file (JPEG/PNG, max 5MB, max resolution 460x460)") @RequestPart("avatar") MultipartFile avatarFile,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {

        // Validate file type３
        AvatarUploadResponse fileTypeError = fileValidationService.validateFileType(avatarFile);
        if (fileTypeError != null) {
            return ResponseEntity.badRequest().body(fileTypeError);
        }

        // Validate file size before reading file data
        AvatarUploadResponse fileSizeError = fileValidationService.validateFileSize(avatarFile);
        if (fileSizeError != null) {
            return ResponseEntity.badRequest().body(fileSizeError);
        }

        byte[] fileData;
        try {
            fileData = avatarFile.getBytes();
        } catch (IOException e) {
            return ResponseEntity.badRequest()
                    .body(new AvatarUploadResponse(400, "Failed to read file", null));
        }
        // Validate and read image file with dimension check
        AvatarUploadResponse imageResult =
                fileValidationService.validateAndReadImageWithDimensions(fileData);
        if (imageResult != null) {
            return ResponseEntity.badRequest().body(imageResult);
        }

        // Validate file hash
        AvatarUploadResponse hashError = fileValidationService.validateFileHash(hash, fileData);
        if (hashError != null) {
            return ResponseEntity.badRequest().body(hashError);
        }

        // Generate object name with hash + file extension
        Optional<String> fileExtension = avatarProperties.getExtension(avatarFile.getContentType());
        if (fileExtension.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new AvatarUploadResponse(400, "Unsupported file type", null));
        }
        String objectName = hash + "." + fileExtension.get();

        return avatarUploadService.uploadAvatar(
                Long.parseLong(jwt.getSubject()),
                fileData,
                objectName,
                avatarFile.getContentType());
    }
}
