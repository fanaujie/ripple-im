package com.fanaujie.ripple.uploadgateway.service;

import com.fanaujie.ripple.uploadgateway.config.MessageAttachmentProperties;
import com.fanaujie.ripple.uploadgateway.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChunkUploadService {

    private final MinioStorageService minioStorageService;
    private final MessageAttachmentProperties attachmentProperties;

    public InitiateUploadData initiateUpload(InitiateUploadRequest request, String objectName) {

        // Check if file already exists
        if (minioStorageService.objectExists(
                MinioStorageService.BucketType.MESSAGE_ATTACHMENT, objectName)) {
            String fileUrl =
                    minioStorageService.generateFileUrl(
                            MinioStorageService.BucketType.MESSAGE_ATTACHMENT, objectName);
            return InitiateUploadData.alreadyExists(fileUrl);
        }

        // Determine upload mode based on file size
        long chunkSize = attachmentProperties.getChunkSize().toBytes();
        if (request.getFileSize() < chunkSize) {
            return InitiateUploadData.singleMode(objectName);
        }

        // Initiate chunked upload
        int totalChunks = (int) Math.ceil((double) request.getFileSize() / chunkSize);
        int startPartNumber =
                minioStorageService.checkStartUploadPart(
                        MinioStorageService.BucketType.MESSAGE_ATTACHMENT, objectName, totalChunks);
        return InitiateUploadData.chunkMode(objectName, chunkSize, startPartNumber, totalChunks);
    }

    public String uploadChunkPart(String objectName, int partNumber, byte[] chunkData) {
        String partChunkObjectName =
                minioStorageService.chunkPartObjectName(objectName, partNumber);
        return minioStorageService.putObject(
                MinioStorageService.BucketType.MESSAGE_ATTACHMENT, partChunkObjectName, chunkData);
    }

    public CompleteUploadData completeUpload(CompleteUploadRequest request) {
        String fileUrl =
                minioStorageService.composeObject(
                        MinioStorageService.BucketType.MESSAGE_ATTACHMENT,
                        request.getObjectName(),
                        request.getTotalChunks());
        if (fileUrl == null) {
            return null;
        }
        return CompleteUploadData.success(fileUrl);
    }

    public CompleteUploadData singleUpload(String objectName, byte[] fileData) {
        String fileUrl =
                minioStorageService.putObject(
                        MinioStorageService.BucketType.MESSAGE_ATTACHMENT, objectName, fileData);
        if (fileUrl == null) {
            return null;
        }
        return CompleteUploadData.success(fileUrl);
    }

    public void abortUpload(AbortUploadRequest request) {
        minioStorageService.abortUpload(
                MinioStorageService.BucketType.MESSAGE_ATTACHMENT, request.getObjectName());
    }
}
