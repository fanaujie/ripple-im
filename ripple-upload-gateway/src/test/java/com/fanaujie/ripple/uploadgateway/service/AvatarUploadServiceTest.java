package com.fanaujie.ripple.uploadgateway.service;

import com.datastax.oss.driver.api.core.CqlSession;
import com.fanaujie.ripple.storage.exception.NotFoundUserProfileException;
import com.fanaujie.ripple.storage.model.User;
import com.fanaujie.ripple.storage.model.UserProfile;
import com.fanaujie.ripple.storage.repository.UserRepository;
import com.fanaujie.ripple.uploadgateway.config.AvatarProperties;
import com.fanaujie.ripple.uploadgateway.dto.AvatarUploadResponse;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class AvatarUploadServiceTest {

    private static final String MINIO_BUCKET_NAME = "ripple-avatars";

    @Container
    static CassandraContainer<?> cassandra =
            new CassandraContainer<>("cassandra:5.0.5").withInitScript("ripple.cql");

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

        // Cassandra properties
        String contactPoint =
                String.format(
                        "%s:%d",
                        cassandra.getContactPoint().getHostName(),
                        cassandra.getContactPoint().getPort());
        System.out.println(contactPoint);
        registry.add("cassandra.contact-points", () -> List.of(contactPoint));
        registry.add("cassandra.keyspace-name", () -> "ripple");
        registry.add("cassandra.local-datacenter", cassandra::getLocalDatacenter);
    }

    @Autowired private AvatarUploadService avatarUploadService;

    @Autowired private UserRepository userRepository;

    @Autowired private MinioClient minioClient;

    @Autowired private AvatarProperties avatarProperties;

    private CqlSession session;

    private final long testUserId = 100;
    private final String testObjectName = "avatar_" + testUserId + ".jpg";
    private final byte[] testImageData = createTestImageData();

    @BeforeEach
    void setUp() throws Exception {
        // Setup Cassandra session
        session =
                CqlSession.builder()
                        .addContactPoint(cassandra.getContactPoint())
                        .withLocalDatacenter(cassandra.getLocalDatacenter())
                        .build();

        // Create test user profile
        User testUser =
                new User(
                        testUserId,
                        "test_account_" + testUserId,
                        "password",
                        User.DEFAULT_ROLE_USER,
                        (byte) 0);
        userRepository.insertUser(testUser, "Test User", "default_avatar.jpg");

        // Ensure MinIO bucket exists
        if (!minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(MINIO_BUCKET_NAME).build())) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(MINIO_BUCKET_NAME).build());
        }
    }

    @AfterEach
    void tearDown() {
        // Clean Cassandra tables
        if (session != null) {
            session.execute("TRUNCATE ripple.user");
            session.execute("TRUNCATE ripple.user_profile");
            session.close();
        }
    }

    @Test
    void testUploadAvatar_Success_NewFile() throws NotFoundUserProfileException {
        // Given
        String objectName = testObjectName;

        // When
        ResponseEntity<AvatarUploadResponse> response =
                avatarUploadService.uploadAvatar(
                        testUserId,
                        testImageData,
                        objectName,
                        avatarProperties.getAllowedContentTypes()[0]);

        // Then
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertEquals("success", response.getBody().getMessage());
        assertNotNull(response.getBody().getData());
        assertNotNull(response.getBody().getData().getAvatarUrl());
        assertTrue(response.getBody().getData().getAvatarUrl().contains(objectName));

        // Verify database was updated
        UserProfile updatedProfile = userRepository.getUserProfile(testUserId);
        assertNotNull(updatedProfile);
        assertEquals(response.getBody().getData().getAvatarUrl(), updatedProfile.getAvatar());
    }

    @Test
    void testUploadAvatar_Success_FileExists() throws Exception {
        // Given - First upload the file to MinIO
        String objectName = testObjectName;

        // Pre-upload the file to simulate existing file
        avatarUploadService.uploadAvatar(
                testUserId,
                testImageData,
                objectName,
                avatarProperties.getAllowedContentTypes()[0]);

        // When - Upload again with same objectName
        ResponseEntity<AvatarUploadResponse> response =
                avatarUploadService.uploadAvatar(
                        testUserId,
                        testImageData,
                        objectName,
                        avatarProperties.getAllowedContentTypes()[0]);

        // Then
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertEquals("success", response.getBody().getMessage());
        assertNotNull(response.getBody().getData().getAvatarUrl());
        assertTrue(response.getBody().getData().getAvatarUrl().contains(objectName));
    }

    @Test
    void testUploadAvatar_DatabaseUpdateFails() {
        // Given - Cassandra repository will throw exception for non-existent users
        // This test verifies that the service properly handles the exception
        long invalidAccount = 123;
        String objectName = "avatar_invalid.jpg";

        // When
        ResponseEntity<AvatarUploadResponse> response =
                avatarUploadService.uploadAvatar(
                        invalidAccount,
                        testImageData,
                        objectName,
                        avatarProperties.getAllowedContentTypes()[0]);

        // Then - Cassandra repository throws exception for non-existent users, so this should fail
        assertNotNull(response);
        assertEquals(500, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().getCode());
        assertEquals("Failed to update user profile", response.getBody().getMessage());
        assertNull(response.getBody().getData());
    }

    @Test
    void testUploadAvatar_WithNullData() {
        // Given
        String objectName = "null_data_test.jpg"; // Use different object name to avoid conflicts

        // When
        ResponseEntity<AvatarUploadResponse> response =
                avatarUploadService.uploadAvatar(
                        testUserId, null, objectName, avatarProperties.getAllowedContentTypes()[0]);

        // Then - Based on logs, null data should cause upload failure and return 500
        assertNotNull(response);
        assertEquals(500, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().getCode());
        assertEquals("Failed to upload file", response.getBody().getMessage());
        assertNull(response.getBody().getData());
    }

    @Test
    void testFileExistenceCheck() throws Exception {
        // Given - Upload a file first
        avatarUploadService.uploadAvatar(
                testUserId,
                testImageData,
                testObjectName,
                avatarProperties.getAllowedContentTypes()[0]);

        // When - Check if file exists using MinIO client directly
        boolean exists = false;
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(MINIO_BUCKET_NAME)
                            .object(testObjectName)
                            .build());
            exists = true;
        } catch (Exception ignored) {
        }

        // Then
        assertTrue(exists);
    }

    @Test
    void testGenerateAvatarUrl() {
        // Given
        String objectName = "test_avatar.jpg";

        // When
        ResponseEntity<AvatarUploadResponse> response =
                avatarUploadService.uploadAvatar(
                        testUserId,
                        testImageData,
                        objectName,
                        avatarProperties.getAllowedContentTypes()[0]);

        // Then
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getData());
        String avatarUrl = response.getBody().getData().getAvatarUrl();

        // Verify URL format
        assertTrue(avatarUrl.contains(MINIO_BUCKET_NAME));
        assertTrue(avatarUrl.contains(objectName));
        assertTrue(avatarUrl.startsWith("http"));
    }

    @Test
    void testUploadAvatar_FullWorkflow() throws NotFoundUserProfileException {
        // Given
        String objectName = "full_workflow_test.jpg";

        // Verify user exists before upload
        UserProfile beforeUpload = userRepository.getUserProfile(testUserId);
        assertNotNull(beforeUpload);
        String originalAvatar = beforeUpload.getAvatar();

        // When
        ResponseEntity<AvatarUploadResponse> response =
                avatarUploadService.uploadAvatar(
                        testUserId,
                        testImageData,
                        objectName,
                        avatarProperties.getAllowedContentTypes()[0]);

        // Then
        // Verify HTTP response
        assertEquals(200, response.getStatusCode().value());
        assertEquals(200, response.getBody().getCode());
        assertEquals("success", response.getBody().getMessage());

        // Verify avatar URL
        String newAvatarUrl = response.getBody().getData().getAvatarUrl();
        assertNotNull(newAvatarUrl);
        assertNotEquals(originalAvatar, newAvatarUrl);

        // Verify database update
        UserProfile afterUpload = userRepository.getUserProfile(testUserId);
        assertEquals(newAvatarUrl, afterUpload.getAvatar());

        // Verify file exists in MinIO
        assertDoesNotThrow(
                () -> {
                    minioClient.statObject(
                            StatObjectArgs.builder()
                                    .bucket(MINIO_BUCKET_NAME)
                                    .object(objectName)
                                    .build());
                });
    }

    @Test
    void testUploadAvatar_MultipleUsers() throws NotFoundUserProfileException {
        // Given
        long userId2 = 200;
        User testUser2 =
                new User(
                        userId2,
                        "test_account_" + userId2,
                        "password",
                        User.DEFAULT_ROLE_USER,
                        (byte) 0);

        userRepository.insertUser(testUser2, "Test User 2", "default_avatar.jpg");

        // When
        ResponseEntity<AvatarUploadResponse> response1 =
                avatarUploadService.uploadAvatar(
                        testUserId,
                        testImageData,
                        "avatar1.jpg",
                        avatarProperties.getAllowedContentTypes()[0]);
        ResponseEntity<AvatarUploadResponse> response2 =
                avatarUploadService.uploadAvatar(
                        userId2,
                        testImageData,
                        "avatar2.jpg",
                        avatarProperties.getAllowedContentTypes()[0]);

        // Then
        assertEquals(200, response1.getStatusCode().value());
        assertEquals(200, response2.getStatusCode().value());

        String url1 = response1.getBody().getData().getAvatarUrl();
        String url2 = response2.getBody().getData().getAvatarUrl();

        assertNotEquals(url1, url2);
        assertTrue(url1.contains("avatar1.jpg"));
        assertTrue(url2.contains("avatar2.jpg"));

        // Verify both users have different avatar URLs in database
        UserProfile user1Profile = userRepository.getUserProfile(testUserId);
        UserProfile user2Profile = userRepository.getUserProfile(userId2);

        assertEquals(url1, user1Profile.getAvatar());
        assertEquals(url2, user2Profile.getAvatar());
    }

    private static byte[] createTestImageData() {
        // Create a simple test "image" data (not a real image, just test bytes)
        String testData = "This is test image data for avatar upload testing";
        return testData.getBytes();
    }
}
