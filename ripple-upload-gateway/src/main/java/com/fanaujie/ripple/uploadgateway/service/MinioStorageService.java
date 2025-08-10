package com.fanaujie.ripple.uploadgateway.service;

import io.minio.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioStorageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Value("${minio.endpoint}")
    private String minioEndpoint;

    public boolean isBucketExists() {
        try {
            return minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
        } catch (Exception e) {
            log.error("Error checking if bucket exists: {}", e.getMessage());
            return false;
        }
    }

    public boolean isFileExists(String objectName) {
        try {
            isBucketExists();
            minioClient.statObject(
                    StatObjectArgs.builder().bucket(bucketName).object(objectName).build());
            return true;
        } catch (Exception e) {
            log.error(
                    "File does not exist or error occurred while checking file: {}",
                    e.getMessage());
            return false;
        }
    }

    public String uploadSingleFile(String objectName, byte[] fileData, String contentType) {
        try {
            isBucketExists();
            minioClient.putObject(
                    PutObjectArgs.builder().bucket(bucketName).object(objectName).stream(
                                    new ByteArrayInputStream(fileData), fileData.length, -1)
                            .contentType(contentType)
                            .build());

            return generateFileUrl(objectName);
        } catch (Exception e) {
            log.error("Failed to upload file to MinIO: {}", e.getMessage());
            return null;
        }
    }

    public String generateFileUrl(String objectName) {
        return minioEndpoint + "/" + bucketName + "/" + objectName;
    }
}
