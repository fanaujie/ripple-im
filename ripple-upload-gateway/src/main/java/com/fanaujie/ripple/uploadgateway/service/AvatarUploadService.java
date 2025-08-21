package com.fanaujie.ripple.uploadgateway.service;

import com.fanaujie.ripple.database.mapper.UserProfileMapper;
import com.fanaujie.ripple.uploadgateway.dto.AvatarUploadData;
import com.fanaujie.ripple.uploadgateway.dto.AvatarUploadResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AvatarUploadService {

    private final MinioStorageService minioStorageService;
    private final UserProfileMapper userProfileMapper;

    public ResponseEntity<AvatarUploadResponse> uploadAvatar(
            long userId, byte[] fileData, String objectName, String contentType) {
        boolean bucketExists = minioStorageService.isBucketExists();
        if (!bucketExists) {
            return ResponseEntity.status(500)
                    .body(new AvatarUploadResponse(500, "Bucket does not exist", null));
        }
        boolean fileExists = minioStorageService.isFileExists(objectName);
        String avatarUrl;
        if (fileExists) {
            avatarUrl = minioStorageService.generateFileUrl(objectName);
        } else {
            avatarUrl = minioStorageService.uploadSingleFile(objectName, fileData, contentType);
            if (avatarUrl == null) {
                return ResponseEntity.status(500)
                        .body(new AvatarUploadResponse(500, "Failed to upload file", null));
            }
        }
        try {
            userProfileMapper.updateAvatar(userId, avatarUrl);
        } catch (Exception e) {
            log.error("Failed to update user profile for userId {}: {}", userId, e.getMessage());
            return ResponseEntity.status(500)
                    .body(new AvatarUploadResponse(500, "Failed to update user profile", null));
        }
        return ResponseEntity.ok(
                new AvatarUploadResponse(200, "success", new AvatarUploadData(avatarUrl)));
    }
}
