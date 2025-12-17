package com.fanaujie.ripple.uploadgateway.service;

import io.minio.*;
import io.minio.messages.DeleteObject;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioStorageService {

    @ToString
    public enum BucketType {
        AVATAR,
        MESSAGE_ATTACHMENT;
    }

    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String avatarBucketName;

    @Value("${minio.message-bucket-name}")
    private String messageBucketName;

    @Value("${minio.endpoint}")
    private String minioEndpoint;

    private String getBucketName(BucketType bucketType) {
        return switch (bucketType) {
            case AVATAR -> avatarBucketName;
            case MESSAGE_ATTACHMENT -> messageBucketName;
            default -> throw new IllegalArgumentException("Unknown bucket type: " + bucketType);
        };
    }

    public boolean isBucketExists(BucketType bucketType) {
        try {
            return minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(this.getBucketName(bucketType)).build());
        } catch (Exception e) {
            log.error("Error checking if bucket {} exists: {}", bucketType, e.getMessage());
            return false;
        }
    }

    public boolean objectExists(BucketType bucketType, String objectName) {
        try {
            String bucketName = this.getBucketName(bucketType);
            isBucketExists(bucketType);
            minioClient.statObject(
                    StatObjectArgs.builder().bucket(bucketName).object(objectName).build());
            return true;
        } catch (Exception e) {
            log.error(
                    "File does not exist or error occurred while checking file {} in bucket {}: {}",
                    objectName,
                    bucketType,
                    e.getMessage());
            return false;
        }
    }

    public String putObject(BucketType bucketType, String objectName, byte[] fileData) {
        try {
            String bucketName = this.getBucketName(bucketType);
            isBucketExists(bucketType);
            PutObjectArgs.Builder b = PutObjectArgs.builder();
            b.bucket(bucketName).object(objectName).stream(
                    new ByteArrayInputStream(fileData), fileData.length, -1);
            minioClient.putObject(b.build());
            return generateFileUrl(bucketType, objectName);
        } catch (Exception e) {
            log.error(
                    "Failed to upload file {} to bucket {}: {}",
                    objectName,
                    bucketType,
                    e.getMessage());
            return null;
        }
    }

    public String generateFileUrl(BucketType bucketType, String objectName) {
        return minioEndpoint + "/" + this.getBucketName(bucketType) + "/" + objectName;
    }

    public void ensureBucketExists(BucketType bucketType) {
        try {
            String bucketName = this.getBucketName(bucketType);
            if (!isBucketExists(bucketType)) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(this.getBucketName(bucketType)).build());
                log.info("Created message bucket: {}", bucketName);
            }
        } catch (Exception e) {
            log.error("Failed to create bucket {}: {}", bucketType, e.getMessage());
            throw new RuntimeException("Failed to create message bucket", e);
        }
    }

    public int checkStartUploadPart(BucketType bucketType, String objectName, int totalParts) {
        for (int i = 1; i <= totalParts; i++) {
            String partObjectName = objectName + "-part-" + i;
            if (!objectExists(bucketType, partObjectName)) {
                return i;
            }
        }
        return totalParts + 1;
    }

    public String chunkPartObjectName(String objectName, int partNumber) {
        return objectName + "-part-" + partNumber;
    }

    public String composeObject(BucketType bucketType, String objectName, int totalParts) {
        try {
            String bucketName = this.getBucketName(bucketType);
            List<ComposeSource> sourceObjectList = new ArrayList<ComposeSource>();
            for (int i = 1; i <= totalParts; i++) {
                sourceObjectList.add(
                        ComposeSource.builder()
                                .bucket(bucketName)
                                .object(objectName + "-part-" + i)
                                .build());
            }
            minioClient.composeObject(
                    ComposeObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .sources(sourceObjectList)
                            .build());
            // After composing, you might want to delete the part objects
            List<DeleteObject> items = new ArrayList<DeleteObject>();
            for (int i = 1; i <= totalParts; i++) {
                items.add(new DeleteObject(objectName + "-part-" + i));
            }
            var errors =
                    minioClient.removeObjects(
                            RemoveObjectsArgs.builder().bucket(bucketName).objects(items).build());
            for (var error : errors) {
                error.get();
            }
            return generateFileUrl(bucketType, objectName);
        } catch (Exception e) {
            log.error(
                    "Failed to compose object {} in bucket {}: {}",
                    objectName,
                    bucketType,
                    e.getMessage());
            return null;
        }
    }

    public void abortUpload(BucketType bucketType, String objectName) {
        String bucketName = this.getBucketName(bucketType);
        List<DeleteObject> items = new ArrayList<DeleteObject>();
        try {
            for (var item :
                    minioClient.listObjects(
                            ListObjectsArgs.builder()
                                    .bucket(bucketName)
                                    .prefix(objectName)
                                    .build())) {
                items.add(new DeleteObject(item.get().objectName()));
            }
            var errors =
                    minioClient.removeObjects(
                            RemoveObjectsArgs.builder().bucket(bucketName).objects(items).build());
            for (var error : errors) {
                error.get();
            }
        } catch (Exception e) {
            log.error(
                    "Failed to abort multipart upload for object {} in bucket {}: {}",
                    objectName,
                    bucketName,
                    e.getMessage());
        }
    }
}
