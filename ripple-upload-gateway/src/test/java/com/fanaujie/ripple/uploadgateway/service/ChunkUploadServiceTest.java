package com.fanaujie.ripple.uploadgateway.service;

import com.fanaujie.ripple.cache.service.ConversationSummaryStorage;
import com.fanaujie.ripple.cache.service.UserProfileStorage;
import com.fanaujie.ripple.communication.msgapi.MessageAPISender;
import com.fanaujie.ripple.snowflakeid.client.SnowflakeIdClient;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import com.fanaujie.ripple.uploadgateway.config.MessageAttachmentProperties;
import com.fanaujie.ripple.uploadgateway.dto.*;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class ChunkUploadServiceTest {

    @Container
    static MinIOContainer minio =
            new MinIOContainer("minio/minio:RELEASE.2023-09-04T19-57-37Z")
                    .withUserName("minioadmin")
                    .withPassword("minioadmin");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // MinIO properties
        registry.add("minio.endpoint", minio::getS3URL);
        registry.add("minio.access-key", minio::getUserName);
        registry.add("minio.secret-key", minio::getPassword);
    }

    @MockitoBean private MessageAPISender messageAPISender;

    @MockitoBean private SnowflakeIdClient snowflakeIdClient;

    @MockitoBean private RippleStorageFacade storageFacade;

    @MockitoBean private RedissonClient redissonClient;

    @MockitoBean private UserProfileStorage userProfileStorage;

    @MockitoBean private ConversationSummaryStorage conversationSummaryStorage;

    @Autowired private ChunkUploadService chunkUploadService;

    @Autowired private MinioStorageService minioStorageService;

    @Autowired private MinioClient minioClient;

    @Autowired private MessageAttachmentProperties attachmentProperties;

    @BeforeEach
    void setUp() {
        // Ensure the message attachment bucket exists before each test
        minioStorageService.ensureBucketExists(MinioStorageService.BucketType.MESSAGE_ATTACHMENT);
    }

    @Test
    void testInitiateUpload_FileAlreadyExists() {
        // Arrange
        String fileSha256 = "abc123def456";
        String originalFilename = "test.pdf";
        String objectName = fileSha256 + ".pdf";
        byte[] fileData = "existing-file-data".getBytes();
        int fileSize = fileData.length;

        // Pre-upload the file
        String existingUrl =
                minioStorageService.putObject(
                        MinioStorageService.BucketType.MESSAGE_ATTACHMENT, objectName, fileData);

        InitiateUploadRequest request = new InitiateUploadRequest();
        request.setFileSize(fileSize);
        request.setFileSha256(fileSha256);
        request.setOriginalFilename(originalFilename);

        // Act
        InitiateUploadData response = chunkUploadService.initiateUpload(request, objectName);

        // Assert
        assertNotNull(response);
        assertEquals(
                InitiateUploadData.UploadMode.AlreadyExits.getCode(), response.getUploadMode());
        assertNotNull(response.getFileUrl());
        assertTrue(response.getFileUrl().contains(objectName));
    }

    @Test
    void testInitiateUpload_SingleMode() {
        // Arrange - small file that doesn't require chunking
        String fileSha256 = "single123";
        String originalFilename = "small.txt";
        int fileSize = 1024; // 1KB - smaller than chunk size
        String objectName = fileSha256 + ".txt";
        InitiateUploadRequest request = new InitiateUploadRequest();
        request.setFileSize(fileSize);
        request.setFileSha256(fileSha256);
        request.setOriginalFilename(originalFilename);

        // Act
        InitiateUploadData response = chunkUploadService.initiateUpload(request, objectName);

        // Assert
        assertNotNull(response);
        assertEquals(InitiateUploadData.UploadMode.SINGLE.getCode(), response.getUploadMode());
        assertNull(response.getFileUrl());
        assertEquals(objectName, response.getObjectName());
    }

    @Test
    void testInitiateUpload_ChunkMode() {
        // Arrange - large file that requires chunking
        String fileSha256 = "chunk123";
        String originalFilename = "large.pdf";
        long chunkSize = attachmentProperties.getChunkSize().toBytes();
        int fileSize = (int) (chunkSize * 3 + 1024); // 3+ chunks
        String objectName = fileSha256 + ".pdf";
        InitiateUploadRequest request = new InitiateUploadRequest();
        request.setFileSize(fileSize);
        request.setFileSha256(fileSha256);
        request.setOriginalFilename(originalFilename);

        // Act
        InitiateUploadData response = chunkUploadService.initiateUpload(request, objectName);

        // Assert
        assertNotNull(response);
        assertEquals(InitiateUploadData.UploadMode.CHUNK.getCode(), response.getUploadMode());
        assertEquals(chunkSize, response.getChunkSize());
        assertNotNull(response.getObjectName());
        assertTrue(response.getObjectName().contains(fileSha256));
        assertEquals(4, response.getTotalChunks()); // 3 full chunks + 1 partial
        assertEquals(1, response.getStartChunkNumber()); // Starting from 1 (1-based)
    }

    @Test
    void testUploadChunkPart_Success() {
        // Arrange
        String objectName = "testchunk.pdf";
        int partNumber = 1;
        byte[] chunkData = "chunk-part-1-data".getBytes();

        // Act
        String chunkUrl = chunkUploadService.uploadChunkPart(objectName, partNumber, chunkData);

        // Assert
        assertNotNull(chunkUrl);
        assertTrue(chunkUrl.contains(objectName + "-part-" + partNumber));

        // Verify the chunk exists in MinIO
        String partChunkObjectName =
                minioStorageService.chunkPartObjectName(objectName, partNumber);
        boolean exists =
                minioStorageService.objectExists(
                        MinioStorageService.BucketType.MESSAGE_ATTACHMENT, partChunkObjectName);
        assertTrue(exists);
    }

    void generateAndUploadChunks(String objectName, int totalChunks, int breakAtPartNumber) {
        // Upload chunks
        for (int i = 1; i <= totalChunks; i++) {
            if (i == breakAtPartNumber) {
                // Simulate interruption
                break;
            }
            if (i == totalChunks) {
                // Last chunk smaller than chunk size
                byte[] lastChunkData = new byte[1024]; // 1KB
                Arrays.fill(lastChunkData, (byte) 'a');
                String fileUrl = chunkUploadService.uploadChunkPart(objectName, i, lastChunkData);
                assertNotNull(fileUrl);
                break;
            }
            byte[] chunkData = new byte[(int) (5 * 1024 * 1024)]; // 5MB
            Arrays.fill(chunkData, (byte) 'a');
            String fileUrl = chunkUploadService.uploadChunkPart(objectName, i, chunkData);
            assertNotNull(fileUrl);
        }
    }

    @Test
    void testCompleteUpload_Success() {
        // Arrange - upload multiple chunks first
        String objectName = "complete-test.pdf";
        int totalChunks = 3;
        generateAndUploadChunks(objectName, totalChunks, 0);
        CompleteUploadRequest request = new CompleteUploadRequest();
        request.setObjectName(objectName);
        request.setTotalChunks(totalChunks);

        // Act
        CompleteUploadData response = chunkUploadService.completeUpload(request);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getFileUrl());
        assertTrue(response.getFileUrl().contains(objectName));

        // Verify the composed object exists
        boolean exists =
                minioStorageService.objectExists(
                        MinioStorageService.BucketType.MESSAGE_ATTACHMENT, objectName);
        assertTrue(exists);

        // Verify chunks are removed
        for (int i = 1; i <= totalChunks; i++) {
            String partChunkObjectName = minioStorageService.chunkPartObjectName(objectName, i);
            boolean chunkExists =
                    minioStorageService.objectExists(
                            MinioStorageService.BucketType.MESSAGE_ATTACHMENT, partChunkObjectName);
            assertFalse(chunkExists);
        }
    }

    @Test
    void testSimpleUpload_Success() {
        // Arrange
        String objectName = "simple-upload.txt";
        byte[] fileData = "simple-file-content".getBytes();

        // Act
        CompleteUploadData response = chunkUploadService.singleUpload(objectName, fileData);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getFileUrl());
        assertTrue(response.getFileUrl().contains(objectName));

        // Verify the object exists in MinIO
        boolean exists =
                minioStorageService.objectExists(
                        MinioStorageService.BucketType.MESSAGE_ATTACHMENT, objectName);
        assertTrue(exists);
    }

    @Test
    void testAbortUpload_Success() throws InterruptedException {
        // Arrange - upload some chunks first
        String objectName = "abort-test.pdf";
        int totalChunks = 2;

        generateAndUploadChunks(objectName, totalChunks, 0);

        // Verify chunks exist before abort
        String partChunkObjectName1 = minioStorageService.chunkPartObjectName(objectName, 1);
        assertTrue(
                minioStorageService.objectExists(
                        MinioStorageService.BucketType.MESSAGE_ATTACHMENT, partChunkObjectName1));

        AbortUploadRequest request = new AbortUploadRequest();
        request.setObjectName(objectName);

        // Act
        chunkUploadService.abortUpload(request);

        // Verify chunks are removed
        boolean exists1 =
                minioStorageService.objectExists(
                        MinioStorageService.BucketType.MESSAGE_ATTACHMENT, partChunkObjectName1);
        assertFalse(exists1);
    }

    @Test
    void testInitiateUpload_ChunkModeWithPartialResume() {
        // Arrange - large file with some chunks already uploaded
        String fileSha256 = "resume123";
        String originalFilename = "resume.pdf";
        long chunkSize = attachmentProperties.getChunkSize().toBytes();
        int fileSize = (int) (chunkSize * 2) + 1023; // Exactly 3 chunks
        String objectName = fileSha256 + ".pdf";

        // Pre-upload first 2 chunks (parts 1 and 2)
        generateAndUploadChunks(objectName, 3, 3);

        InitiateUploadRequest request = new InitiateUploadRequest();
        request.setFileSize(fileSize);
        request.setFileSha256(fileSha256);
        request.setOriginalFilename(originalFilename);

        // Act
        InitiateUploadData response = chunkUploadService.initiateUpload(request, objectName);

        // Assert
        assertNotNull(response);
        assertEquals(InitiateUploadData.UploadMode.CHUNK.getCode(), response.getUploadMode());
        assertEquals(3, response.getStartChunkNumber()); // Should resume from chunk 3 (1-based)
        assertEquals(3, response.getTotalChunks());
    }
}
