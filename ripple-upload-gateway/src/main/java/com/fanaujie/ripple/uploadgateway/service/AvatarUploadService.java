package com.fanaujie.ripple.uploadgateway.service;

import com.fanaujie.ripple.communication.msgapi.MessageAPISender;
import com.fanaujie.ripple.protobuf.msgapiserver.GroupUpdateInfoCommand;
import com.fanaujie.ripple.protobuf.msgapiserver.SelfInfoUpdateEvent;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq;
import com.fanaujie.ripple.protobuf.msgapiserver.SendGroupCommandReq;
import com.fanaujie.ripple.protobuf.snowflakeid.GenerateIdResponse;
import com.fanaujie.ripple.snowflakeid.client.SnowflakeIdClient;
import com.fanaujie.ripple.uploadgateway.dto.AvatarUploadData;
import com.fanaujie.ripple.uploadgateway.dto.AvatarUploadResponse;
import com.fanaujie.ripple.uploadgateway.exception.AvatarUploadException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@Slf4j
public class AvatarUploadService {

    private final MinioStorageService minioStorageService;
    private final MessageAPISender messageAPISender;
    private final SnowflakeIdClient snowflakeIdClient;

    public AvatarUploadService(
            MinioStorageService minioStorageService,
            MessageAPISender messageAPISender,
            SnowflakeIdClient snowflakeIdClient) {
        this.minioStorageService = minioStorageService;
        this.messageAPISender = messageAPISender;
        this.snowflakeIdClient = snowflakeIdClient;
    }

    public String uploadAvatarToStorage(byte[] fileData, String objectName) {
        boolean bucketExists =
                minioStorageService.isBucketExists(MinioStorageService.BucketType.AVATAR);
        if (!bucketExists) {
            throw new AvatarUploadException("Bucket does not exist");
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
                throw new AvatarUploadException("Failed to upload file");
            }
        }
        return avatarUrl;
    }

    public ResponseEntity<AvatarUploadResponse> uploadUserAvatar(
            long userId, byte[] fileData, String objectName) {
        String avatarUrl;
        try {
            avatarUrl = uploadAvatarToStorage(fileData, objectName);
        } catch (AvatarUploadException e) {
            return ResponseEntity.status(500)
                    .body(new AvatarUploadResponse(500, e.getMessage(), null));
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
                            .setSendTimestamp(Instant.now().toEpochMilli())
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

    public ResponseEntity<AvatarUploadResponse> uploadGroupAvatar(
            long groupId, long senderId, byte[] fileData, String objectName) {
        String avatarUrl;
        try {
            avatarUrl = uploadAvatarToStorage(fileData, objectName);
        } catch (AvatarUploadException e) {
            return ResponseEntity.status(500)
                    .body(new AvatarUploadResponse(500, e.getMessage(), null));
        }

        try {
            GenerateIdResponse messageIdResponse = snowflakeIdClient.requestSnowflakeId().get();
            long messageId = messageIdResponse.getId();

            GroupUpdateInfoCommand.Builder updateCommandBuilder =
                    GroupUpdateInfoCommand.newBuilder();
            updateCommandBuilder.setUpdateType(GroupUpdateInfoCommand.UpdateType.UPDATE_AVATAR);
            updateCommandBuilder.setNewAvatar(avatarUrl);

            SendGroupCommandReq.Builder commandReqBuilder = SendGroupCommandReq.newBuilder();
            commandReqBuilder.setSenderId(senderId);
            commandReqBuilder.setGroupId(groupId);
            commandReqBuilder.setMessageId(messageId);
            commandReqBuilder.setSendTimestamp(Instant.now().toEpochMilli());
            commandReqBuilder.setGroupUpdateInfoCommand(updateCommandBuilder.build());

            messageAPISender.sendGroupCommand(commandReqBuilder.build());
        } catch (Exception e) {
            log.error(
                    "Failed to update group avatar for groupId {}: {}", groupId, e.getMessage(), e);
            return ResponseEntity.status(500)
                    .body(new AvatarUploadResponse(500, "Failed to update group avatar", null));
        }
        return ResponseEntity.ok(
                new AvatarUploadResponse(200, "success", new AvatarUploadData(avatarUrl)));
    }
}
