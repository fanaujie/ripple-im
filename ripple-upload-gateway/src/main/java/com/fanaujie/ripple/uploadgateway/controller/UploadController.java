package com.fanaujie.ripple.uploadgateway.controller;

import com.fanaujie.ripple.uploadgateway.constants.SupportedContentTypes;
import com.fanaujie.ripple.uploadgateway.dto.AvatarUploadResponse;
import com.fanaujie.ripple.uploadgateway.service.AvatarUploadService;
import com.fanaujie.ripple.uploadgateway.service.AvatarFileValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
@Slf4j
public class UploadController {

    private final AvatarUploadService avatarUploadService;
    private final AvatarFileValidationService fileValidationService;

    @PostMapping(value = "/avatar")
    public ResponseEntity<AvatarUploadResponse> uploadAvatar(
            @RequestParam("hash") String hash,
            @RequestParam("avatar") MultipartFile avatarFile,
            Authentication authentication) {

        // Validate file type
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
        String fileExtension =
                SupportedContentTypes.getContentType(avatarFile.getContentType()).getExtension();
        String objectName = hash + "." + fileExtension;

        return avatarUploadService.uploadAvatar(
                authentication.getName(), fileData, objectName, avatarFile.getContentType());
    }
}
