package com.fanaujie.ripple.uploadgateway.controller;

import com.fanaujie.ripple.storage.model.GroupMemberInfo;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import com.fanaujie.ripple.uploadgateway.dto.AvatarUploadResponse;
import com.fanaujie.ripple.uploadgateway.exception.InvalidFileExtensionException;
import com.fanaujie.ripple.uploadgateway.service.AvatarFileValidationService;
import com.fanaujie.ripple.uploadgateway.service.AvatarUploadService;
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
import java.util.List;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "File Upload", description = "APIs for uploading avatars and multimedia files")
@SecurityRequirement(name = "bearerAuth")
public class UploadController {

    private final AvatarUploadService avatarUploadService;
    private final AvatarFileValidationService fileValidationService;
    private final RippleStorageFacade storageFacade;

    @PutMapping(value = "/avatar", consumes = "multipart/form-data", produces = "application/json")
    @Operation(
            summary = "Upload user avatar",
            description =
                    "Upload and set user avatar, supports JPEG and PNG formats, file size limit 5MB, maximum resolution 460x460")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully uploaded avatar",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                AvatarUploadResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description =
                                "Unsupported file format, file size exceeds limit, file hash validation failed, or file resolution exceeds limit",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                AvatarUploadResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized access",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                AvatarUploadResponse.class))),
                @ApiResponse(
                        responseCode = "500",
                        description = "Internal server error",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                AvatarUploadResponse.class)))
            })
    public ResponseEntity<AvatarUploadResponse> uploadUserAvatar(
            @Parameter(
                            description =
                                    "SHA-256 hash value of the avatar file for integrity verification",
                            required = true,
                            example =
                                    "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e")
                    @RequestPart("hash")
                    String hash,
            @Parameter(
                            description =
                                    "Original filename with extension (used to determine file extension)",
                            required = true,
                            example = "avatar.jpg")
                    @RequestPart("originalFilename")
                    String originalFilename,
            @Parameter(
                            description =
                                    "Avatar image file. Maximum file size: 5MB. Maximum resolution: 460x460 pixels.",
                            required = true)
                    @RequestPart("avatar")
                    MultipartFile avatarFile,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {

        // Validate file size before reading file data
        AvatarUploadResponse fileSizeError = fileValidationService.validateFileSize(avatarFile);
        if (fileSizeError != null) {
            return ResponseEntity.badRequest().body(fileSizeError);
        }

        // Read file data
        byte[] fileData;
        try {
            fileData = avatarFile.getBytes();
        } catch (IOException e) {
            return ResponseEntity.badRequest()
                    .body(new AvatarUploadResponse(400, "Failed to read file", null));
        }

        // Validate and read image file with dimension check
        // ImageIO.read() will validate that the file is a valid image
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

        try {
            String extension = fileValidationService.extractFileExtension(originalFilename);
            String objectName = hash + extension;
            return avatarUploadService.uploadUserAvatar(
                    Long.parseLong(jwt.getSubject()), fileData, objectName);
        } catch (InvalidFileExtensionException e) {
            return ResponseEntity.badRequest()
                    .body(new AvatarUploadResponse(400, "Invalid file extension", null));
        }
    }

    @PutMapping(
            value = "/groups/{groupId}/avatar",
            consumes = "multipart/form-data",
            produces = "application/json")
    @Operation(
            summary = "Upload group avatar",
            description =
                    "Upload and set group avatar, supports JPEG and PNG formats, file size limit 5MB, maximum resolution 460x460. Only group members can upload.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully uploaded group avatar",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                AvatarUploadResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description =
                                "Unsupported file format, file size exceeds limit, file hash validation failed, or file resolution exceeds limit",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                AvatarUploadResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized access",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                AvatarUploadResponse.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "Forbidden: Not a group member",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                AvatarUploadResponse.class))),
                @ApiResponse(
                        responseCode = "500",
                        description = "Internal server error",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                AvatarUploadResponse.class)))
            })
    public ResponseEntity<AvatarUploadResponse> uploadGroupAvatar(
            @PathVariable("groupId") @Parameter(description = "Group ID", required = true)
                    String groupId,
            @Parameter(
                            description =
                                    "SHA-256 hash value of the avatar file for integrity verification",
                            required = true,
                            example =
                                    "a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e")
                    @RequestPart("hash")
                    String hash,
            @Parameter(
                            description =
                                    "Original filename with extension (used to determine file extension)",
                            required = true,
                            example = "avatar.jpg")
                    @RequestPart("originalFilename")
                    String originalFilename,
            @Parameter(
                            description =
                                    "Avatar image file. Maximum file size: 5MB. Maximum resolution: 460x460 pixels.",
                            required = true)
                    @RequestPart("avatar")
                    MultipartFile avatarFile,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {

        long groupIdLong;
        long senderId;
        try {
            groupIdLong = Long.parseLong(groupId);
            senderId = Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest()
                    .body(new AvatarUploadResponse(400, "Invalid group ID format", null));
        }

        // Verify requester is a group member
        try {
            List<GroupMemberInfo> members = storageFacade.getGroupMembersInfo(groupIdLong);
            boolean isMember = members.stream().anyMatch(m -> m.getUserId() == senderId);
            if (!isMember) {
                log.warn(
                        "uploadGroupAvatar: User {} is not a member of group {}",
                        senderId,
                        groupIdLong);
                return ResponseEntity.status(403)
                        .body(new AvatarUploadResponse(403, "Forbidden: Not a group member", null));
            }
        } catch (Exception e) {
            log.error(
                    "uploadGroupAvatar: Error verifying membership for group {}", groupIdLong, e);
            return ResponseEntity.status(500)
                    .body(new AvatarUploadResponse(500, "Failed to verify group membership", null));
        }

        // Validate file size before reading file data
        AvatarUploadResponse fileSizeError = fileValidationService.validateFileSize(avatarFile);
        if (fileSizeError != null) {
            return ResponseEntity.badRequest().body(fileSizeError);
        }

        // Read file data
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

        try {
            String extension = fileValidationService.extractFileExtension(originalFilename);
            String objectName = hash + extension;
            return avatarUploadService.uploadGroupAvatar(
                    groupIdLong, senderId, fileData, objectName);
        } catch (InvalidFileExtensionException e) {
            return ResponseEntity.badRequest()
                    .body(new AvatarUploadResponse(400, "Invalid file extension", null));
        }
    }
}
