package com.fanaujie.ripple.uploadgateway.service;

import com.fanaujie.ripple.communication.msgapi.MessageAPISender;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq;
import com.fanaujie.ripple.uploadgateway.config.AvatarProperties;
import com.fanaujie.ripple.uploadgateway.dto.AvatarUploadResponse;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
class AvatarUploadServiceTest {

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

    @Autowired private AvatarUploadService avatarUploadService;

    @Autowired private MinioStorageService minioStorageService;

    @Autowired private MinioClient minioClient;

    @Autowired private AvatarProperties avatarProperties;

    @BeforeEach
    void setUp() {
        // Ensure the avatar bucket exists before each test
        minioStorageService.ensureBucketExists(MinioStorageService.BucketType.AVATAR);
    }

    @Test
    void testUploadAvatar_Success() throws Exception {
        // Arrange
        long userId = 123L;
        byte[] fileData = "test-avatar-data".getBytes();
        String objectName = "user-123.png";

        // Act
        ResponseEntity<AvatarUploadResponse> response =
                avatarUploadService.uploadAvatar(userId, fileData, objectName);

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertEquals("success", response.getBody().getMessage());
        assertNotNull(response.getBody().getData());
        assertNotNull(response.getBody().getData().getAvatarUrl());
        assertTrue(response.getBody().getData().getAvatarUrl().contains(objectName));

        // Verify that event was sent
        verify(messageAPISender, times(1)).sendEvent(any(SendEventReq.class));
    }

    @Test
    void testUploadAvatar_FileAlreadyExists() throws Exception {
        // Arrange
        long userId = 456L;
        byte[] fileData = "existing-avatar-data".getBytes();
        String objectName = "user-456.png";

        // First upload to create the file
        minioStorageService.putObject(MinioStorageService.BucketType.AVATAR, objectName, fileData);

        // Act - upload again with the same object name
        ResponseEntity<AvatarUploadResponse> response =
                avatarUploadService.uploadAvatar(userId, fileData, objectName);

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertEquals("success", response.getBody().getMessage());
        assertNotNull(response.getBody().getData());
        assertNotNull(response.getBody().getData().getAvatarUrl());
        assertTrue(response.getBody().getData().getAvatarUrl().contains(objectName));

        // Verify that event was sent
        verify(messageAPISender, times(1)).sendEvent(any(SendEventReq.class));
    }

    @Test
    void testUploadAvatar_SendEventFails() throws Exception {
        // Arrange
        long userId = 789L;
        byte[] fileData = "test-avatar-data".getBytes();
        String objectName = "user-789.png";

        // Mock sendEvent to throw exception
        doThrow(new RuntimeException("Failed to send event"))
                .when(messageAPISender)
                .sendEvent(any(SendEventReq.class));

        // Act
        ResponseEntity<AvatarUploadResponse> response =
                avatarUploadService.uploadAvatar(userId, fileData, objectName);

        // Assert
        assertNotNull(response);
        assertEquals(500, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().getCode());
        assertEquals("Failed to update user profile", response.getBody().getMessage());
        assertNull(response.getBody().getData());

        // Verify that event sending was attempted
        verify(messageAPISender, times(1)).sendEvent(any(SendEventReq.class));
    }

    @Test
    void testUploadAvatar_UploadFails() throws Exception {
        // Arrange
        long userId = 999L;
        byte[] fileData = new byte[0]; // Empty data that might cause issues
        String objectName = ""; // Invalid object name

        // Act
        ResponseEntity<AvatarUploadResponse> response =
                avatarUploadService.uploadAvatar(userId, fileData, objectName);

        // Assert - should fail during upload
        assertNotNull(response);
        assertEquals(500, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().getCode());
        assertEquals("Failed to upload file", response.getBody().getMessage());
        assertNull(response.getBody().getData());

        // Verify that event was never sent since upload failed
        verify(messageAPISender, never()).sendEvent(any(SendEventReq.class));
    }
}
