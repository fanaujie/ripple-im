package com.fanaujie.ripple.uploadgateway.service;

import com.fanaujie.ripple.cache.service.ConversationSummaryStorage;
import com.fanaujie.ripple.cache.service.UserProfileStorage;
import com.fanaujie.ripple.communication.msgapi.MessageAPISender;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq;
import com.fanaujie.ripple.protobuf.msgapiserver.SendGroupCommandReq;
import com.fanaujie.ripple.protobuf.snowflakeid.GenerateIdResponse;
import com.fanaujie.ripple.snowflakeid.client.SnowflakeIdClient;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import com.fanaujie.ripple.uploadgateway.config.AvatarProperties;
import com.fanaujie.ripple.uploadgateway.dto.AvatarUploadResponse;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CompletableFuture;

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

    @MockitoBean private SnowflakeIdClient snowflakeIdClient;

    @MockitoBean private RippleStorageFacade storageFacade;

    @MockitoBean private RedissonClient redissonClient;

    @MockitoBean private UserProfileStorage userProfileStorage;

    @MockitoBean private ConversationSummaryStorage conversationSummaryStorage;

    @Autowired private AvatarUploadService avatarUploadService;

    @Autowired private MinioStorageService minioStorageService;

    @Autowired private MinioClient minioClient;

    @Autowired private AvatarProperties avatarProperties;

    @BeforeEach
    void setUp() {
        // Ensure the avatar bucket exists before each test
        minioStorageService.ensureBucketExists(MinioStorageService.BucketType.AVATAR);

        // Reset mocks
        reset(messageAPISender, snowflakeIdClient);
    }

    // ==================== User Avatar Tests ====================

    @Test
    void testUploadUserAvatar_Success() throws Exception {
        // Arrange
        long userId = 123L;
        byte[] fileData = "test-avatar-data".getBytes();
        String objectName = "user-123.png";

        // Act
        ResponseEntity<AvatarUploadResponse> response =
                avatarUploadService.uploadUserAvatar(userId, fileData, objectName);

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
    void testUploadUserAvatar_FileAlreadyExists() throws Exception {
        // Arrange
        long userId = 456L;
        byte[] fileData = "existing-avatar-data".getBytes();
        String objectName = "user-456.png";

        // First upload to create the file
        minioStorageService.putObject(MinioStorageService.BucketType.AVATAR, objectName, fileData);

        // Act - upload again with the same object name
        ResponseEntity<AvatarUploadResponse> response =
                avatarUploadService.uploadUserAvatar(userId, fileData, objectName);

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
    void testUploadUserAvatar_SendEventFails() throws Exception {
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
                avatarUploadService.uploadUserAvatar(userId, fileData, objectName);

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
    void testUploadUserAvatar_UploadFails() throws Exception {
        // Arrange
        long userId = 999L;
        byte[] fileData = new byte[0]; // Empty data that might cause issues
        String objectName = ""; // Invalid object name

        // Act
        ResponseEntity<AvatarUploadResponse> response =
                avatarUploadService.uploadUserAvatar(userId, fileData, objectName);

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

    // ==================== Group Avatar Tests ====================

    @Test
    void testUploadGroupAvatar_Success() throws Exception {
        // Arrange
        long groupId = 1001L;
        long senderId = 123L;
        byte[] fileData = "test-group-avatar-data".getBytes();
        String objectName = "group-1001.png";

        // Mock snowflake ID generation
        GenerateIdResponse mockIdResponse =
                GenerateIdResponse.newBuilder().setId(999999L).build();
        when(snowflakeIdClient.requestSnowflakeId())
                .thenReturn(CompletableFuture.completedFuture(mockIdResponse));

        // Act
        ResponseEntity<AvatarUploadResponse> response =
                avatarUploadService.uploadGroupAvatar(groupId, senderId, fileData, objectName);

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertEquals("success", response.getBody().getMessage());
        assertNotNull(response.getBody().getData());
        assertNotNull(response.getBody().getData().getAvatarUrl());
        assertTrue(response.getBody().getData().getAvatarUrl().contains(objectName));

        // Verify that group command was sent
        verify(messageAPISender, times(1)).sendGroupCommand(any(SendGroupCommandReq.class));
    }

    @Test
    void testUploadGroupAvatar_FileAlreadyExists() throws Exception {
        // Arrange
        long groupId = 1002L;
        long senderId = 456L;
        byte[] fileData = "existing-group-avatar-data".getBytes();
        String objectName = "group-1002.png";

        // Mock snowflake ID generation
        GenerateIdResponse mockIdResponse =
                GenerateIdResponse.newBuilder().setId(999998L).build();
        when(snowflakeIdClient.requestSnowflakeId())
                .thenReturn(CompletableFuture.completedFuture(mockIdResponse));

        // First upload to create the file
        minioStorageService.putObject(MinioStorageService.BucketType.AVATAR, objectName, fileData);

        // Act - upload again with the same object name
        ResponseEntity<AvatarUploadResponse> response =
                avatarUploadService.uploadGroupAvatar(groupId, senderId, fileData, objectName);

        // Assert
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertEquals("success", response.getBody().getMessage());
        assertNotNull(response.getBody().getData());
        assertNotNull(response.getBody().getData().getAvatarUrl());
        assertTrue(response.getBody().getData().getAvatarUrl().contains(objectName));

        // Verify that group command was sent
        verify(messageAPISender, times(1)).sendGroupCommand(any(SendGroupCommandReq.class));
    }

    @Test
    void testUploadGroupAvatar_SendCommandFails() throws Exception {
        // Arrange
        long groupId = 1003L;
        long senderId = 789L;
        byte[] fileData = "test-group-avatar-data".getBytes();
        String objectName = "group-1003.png";

        // Mock snowflake ID generation
        GenerateIdResponse mockIdResponse =
                GenerateIdResponse.newBuilder().setId(999997L).build();
        when(snowflakeIdClient.requestSnowflakeId())
                .thenReturn(CompletableFuture.completedFuture(mockIdResponse));

        // Mock sendGroupCommand to throw exception
        doThrow(new RuntimeException("Failed to send group command"))
                .when(messageAPISender)
                .sendGroupCommand(any(SendGroupCommandReq.class));

        // Act
        ResponseEntity<AvatarUploadResponse> response =
                avatarUploadService.uploadGroupAvatar(groupId, senderId, fileData, objectName);

        // Assert
        assertNotNull(response);
        assertEquals(500, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().getCode());
        assertEquals("Failed to update group avatar", response.getBody().getMessage());
        assertNull(response.getBody().getData());

        // Verify that group command sending was attempted
        verify(messageAPISender, times(1)).sendGroupCommand(any(SendGroupCommandReq.class));
    }

    @Test
    void testUploadGroupAvatar_UploadFails() throws Exception {
        // Arrange
        long groupId = 1004L;
        long senderId = 999L;
        byte[] fileData = new byte[0]; // Empty data that might cause issues
        String objectName = ""; // Invalid object name

        // Act
        ResponseEntity<AvatarUploadResponse> response =
                avatarUploadService.uploadGroupAvatar(groupId, senderId, fileData, objectName);

        // Assert - should fail during upload
        assertNotNull(response);
        assertEquals(500, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().getCode());
        assertEquals("Failed to upload file", response.getBody().getMessage());
        assertNull(response.getBody().getData());

        // Verify that group command was never sent since upload failed
        verify(messageAPISender, never()).sendGroupCommand(any(SendGroupCommandReq.class));
    }
}
