package com.fanaujie.ripple.uploadgateway.service;

import com.fanaujie.ripple.communication.msgapi.MessageAPISender;
import com.fanaujie.ripple.protobuf.msgapiserver.SelfInfoUpdateEvent;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq;

import com.fanaujie.ripple.uploadgateway.dto.AvatarUploadData;
import com.fanaujie.ripple.uploadgateway.dto.AvatarUploadResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AvatarUploadService {

    private final MinioStorageService minioStorageService;
    private final MessageAPISender messageAPISender;

    public AvatarUploadService(
            MinioStorageService minioStorageService, MessageAPISender messageAPISender) {
        this.minioStorageService = minioStorageService;
        this.messageAPISender = messageAPISender;
    }

    public ResponseEntity<AvatarUploadResponse> uploadAvatar(
            long userId, byte[] fileData, String objectName) {
        boolean bucketExists =
                minioStorageService.isBucketExists(MinioStorageService.BucketType.AVATAR);
        if (!bucketExists) {
            return ResponseEntity.status(500)
                    .body(new AvatarUploadResponse(500, "Bucket does not exist", null));
        }
        boolean fileExists =
                minioStorageService.objectExists(MinioStorageService.BucketType.AVATAR, objectName);
        String avatarUrl;
        if (fileExists) {
            avatarUrl =
                    minioStorageService.generateFileUrl(
                            MinioStorageService.BucketType.AVATAR, objectName);
        } else {
            avatarUrl =
                    minioStorageService.putObject(
                            MinioStorageService.BucketType.AVATAR, objectName, fileData);
            if (avatarUrl == null) {
                return ResponseEntity.status(500)
                        .body(new AvatarUploadResponse(500, "Failed to upload file", null));
            }
        }
        try {
            SendEventReq req =
                    SendEventReq.newBuilder()
                            .setSelfInfoUpdateEvent(
                                    SelfInfoUpdateEvent.newBuilder()
                                            .setEventType(
                                                    SelfInfoUpdateEvent.EventType.UPDATE_AVATAR)
                                            .setUserId(userId)
                                            .setAvatar(avatarUrl)
                                            .build())
                            .build();
            this.messageAPISender.sendEvent(req);
        } catch (Exception e) {
            log.error("Failed to update user profile for userId {}: {}", userId, e.getMessage());
            return ResponseEntity.status(500)
                    .body(new AvatarUploadResponse(500, "Failed to update user profile", null));
        }
        return ResponseEntity.ok(
                new AvatarUploadResponse(200, "success", new AvatarUploadData(avatarUrl)));
    }
}
