package com.fanaujie.ripple.uploadgateway.controller;
//
// import com.fanaujie.ripple.uploadgateway.config.AvatarProperties;
// import com.fanaujie.ripple.uploadgateway.config.SecurityConfig;
// import com.fanaujie.ripple.uploadgateway.dto.AvatarUploadData;
// import com.fanaujie.ripple.uploadgateway.dto.AvatarUploadResponse;
// import com.fanaujie.ripple.uploadgateway.service.AvatarFileValidationService;
// import com.fanaujie.ripple.uploadgateway.service.AvatarUploadService;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.extension.ExtendWith;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
// import org.springframework.http.HttpMethod;
// import org.springframework.http.MediaType;
// import org.springframework.http.ResponseEntity;
// import org.springframework.mock.web.MockMultipartFile;
// import org.springframework.test.context.ContextConfiguration;
// import org.springframework.test.context.bean.override.mockito.MockitoBean;
// import org.springframework.test.context.junit.jupiter.SpringExtension;
// import org.springframework.test.web.servlet.MockMvc;
// import org.springframework.test.web.servlet.setup.MockMvcBuilders;
// import org.springframework.web.context.WebApplicationContext;
//
// import java.io.IOException;
//
// import static org.mockito.ArgumentMatchers.*;
// import static org.mockito.Mockito.when;
// import org.springframework.security.core.authority.SimpleGrantedAuthority;
// import org.springframework.test.web.servlet.request.RequestPostProcessor;
// import static
// org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
// import static
// org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
// import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
// import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
//
// @ExtendWith(SpringExtension.class)
// @ContextConfiguration(
//        classes = {SecurityConfig.class, UploadController.class, AvatarProperties.class})
// @WebMvcTest
// class UploadControllerTest {
//
//    private MockMvc mockMvc;
//
//    @Autowired private WebApplicationContext context;
//    @Autowired private AvatarProperties avatarProperties;
//
//    @MockitoBean private AvatarUploadService avatarUploadService;
//
//    @MockitoBean private AvatarFileValidationService fileValidationService;
//
//    // Test data
//    private static final long TEST_USER_ID = 100;
//    private static final String TEST_HASH =
//            "a665a45920422f9d417e4867efdc4fb8a04a1f3fff1fa07e998e86f7f7a27ae3";
//    private static final String TEST_AVATAR_URL = "http://minio:9000/ripple-avatars/test.jpg";
//
//    @BeforeEach
//    void setup() {
//        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
//    }
//
//    private MockMultipartFile createTestImageFile() {
//        return new MockMultipartFile(
//                "avatar",
//                "test.jpg",
//                avatarProperties.getAllowedContentTypes()[0],
//                "test image content".getBytes());
//    }
//
//    private MockMultipartFile createInvalidTypeFile() {
//        return new MockMultipartFile("avatar", "test.txt", "text/plain", "test
// content".getBytes());
//    }
//
//    private MockMultipartFile createLargeFile() {
//        return new MockMultipartFile(
//                "avatar",
//                "large.jpg",
//                avatarProperties.getAllowedContentTypes()[0],
//                new byte[6 * 1024 * 1024]);
//    }
//
//    private RequestPostProcessor authenticatedUser() {
//        return jwt().authorities(new SimpleGrantedAuthority("ROLE_user"))
//                .jwt(jwt -> jwt.header("alg", "HS256").claim("sub", TEST_USER_ID));
//    }
//
//    @Test
//    void uploadAvatar_Success() throws Exception {
//        // Given
//        MockMultipartFile file = createTestImageFile();
//        AvatarUploadResponse successResponse =
//                new AvatarUploadResponse(200, "success", new AvatarUploadData(TEST_AVATAR_URL));
//
//        // Mock all validation services to return null (no error)
//        when(fileValidationService.validateFileType(any())).thenReturn(null);
//        when(fileValidationService.validateFileSize(any())).thenReturn(null);
//        when(fileValidationService.validateAndReadImageWithDimensions(any())).thenReturn(null);
//        when(fileValidationService.validateFileHash(anyString(), any())).thenReturn(null);
//
//        when(avatarUploadService.uploadAvatar(eq(TEST_USER_ID), any(), anyString(), anyString()))
//                .thenReturn(ResponseEntity.ok(successResponse));
//
//        // When & Then
//        MockMultipartFile hashPart =
//                new MockMultipartFile("hash", "", "text/plain", TEST_HASH.getBytes());
//        mockMvc.perform(
//                        multipart(HttpMethod.PUT, "/api/upload/avatar")
//                                .file(file)
//                                .file(hashPart)
//                                .contentType(MediaType.MULTIPART_FORM_DATA)
//                                .accept(MediaType.APPLICATION_JSON)
//                                .with(authenticatedUser()))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.code").value(200))
//                .andExpect(jsonPath("$.message").value("success"))
//                .andExpect(jsonPath("$.data.avatarUrl").value(TEST_AVATAR_URL));
//    }
//
//    @Test
//    void uploadAvatar_Unauthorized_NoAuthentication() throws Exception {
//        // Given - No authentication set
//        MockMultipartFile file = createTestImageFile();
//
//        // When & Then
//        mockMvc.perform(
//                        multipart(HttpMethod.PUT, "/api/upload/avatar")
//                                .file(file)
//                                .param("hash", TEST_HASH))
//                .andExpect(status().isUnauthorized());
//    }
//
//    @Test
//    void uploadAvatar_FileTypeValidationError() throws Exception {
//        // Given
//        MockMultipartFile file = createInvalidTypeFile();
//        AvatarUploadResponse errorResponse =
//                new AvatarUploadResponse(400, "Invalid file type", null);
//
//        when(fileValidationService.validateFileType(any())).thenReturn(errorResponse);
//
//        // When & Then
//        MockMultipartFile hashPart =
//                new MockMultipartFile("hash", "", "text/plain", TEST_HASH.getBytes());
//        mockMvc.perform(
//                        multipart(HttpMethod.PUT, "/api/upload/avatar")
//                                .file(file)
//                                .file(hashPart)
//                                .accept(MediaType.APPLICATION_JSON)
//                                .with(authenticatedUser()))
//                .andExpect(status().isBadRequest())
//                .andExpect(jsonPath("$.code").value(400))
//                .andExpect(jsonPath("$.message").value("Invalid file type"));
//    }
//
//    @Test
//    void uploadAvatar_FileSizeValidationError() throws Exception {
//        // Given
//        MockMultipartFile file = createLargeFile();
//        AvatarUploadResponse errorResponse =
//                new AvatarUploadResponse(400, "File size exceeds maximum allowed size", null);
//
//        when(fileValidationService.validateFileType(any())).thenReturn(null);
//        when(fileValidationService.validateFileSize(any())).thenReturn(errorResponse);
//
//        // When & Then
//        MockMultipartFile hashPart =
//                new MockMultipartFile("hash", "", "text/plain", TEST_HASH.getBytes());
//        mockMvc.perform(
//                        multipart(HttpMethod.PUT, "/api/upload/avatar")
//                                .file(file)
//                                .file(hashPart)
//                                .accept(MediaType.APPLICATION_JSON)
//                                .with(authenticatedUser()))
//                .andExpect(status().isBadRequest())
//                .andExpect(jsonPath("$.code").value(400))
//                .andExpect(jsonPath("$.message").value("File size exceeds maximum allowed size"));
//    }
//
//    @Test
//    void uploadAvatar_ImageDimensionValidationError() throws Exception {
//        // Given
//        MockMultipartFile file = createTestImageFile();
//        AvatarUploadResponse errorResponse =
//                new AvatarUploadResponse(400, "Image dimensions exceed maximum allowed size",
// null);
//
//        when(fileValidationService.validateFileType(any())).thenReturn(null);
//        when(fileValidationService.validateFileSize(any())).thenReturn(null);
//        when(fileValidationService.validateAndReadImageWithDimensions(any()))
//                .thenReturn(errorResponse);
//
//        // When & Then
//        MockMultipartFile hashPart =
//                new MockMultipartFile("hash", "", "text/plain", TEST_HASH.getBytes());
//        mockMvc.perform(
//                        multipart(HttpMethod.PUT, "/api/upload/avatar")
//                                .file(file)
//                                .file(hashPart)
//                                .accept(MediaType.APPLICATION_JSON)
//                                .with(authenticatedUser()))
//                .andExpect(status().isBadRequest())
//                .andExpect(jsonPath("$.code").value(400))
//                .andExpect(
//                        jsonPath("$.message")
//                                .value("Image dimensions exceed maximum allowed size"));
//    }
//
//    @Test
//    void uploadAvatar_HashValidationError() throws Exception {
//        // Given
//        MockMultipartFile file = createTestImageFile();
//        AvatarUploadResponse errorResponse =
//                new AvatarUploadResponse(400, "File hash does not match", null);
//
//        when(fileValidationService.validateFileType(any())).thenReturn(null);
//        when(fileValidationService.validateFileSize(any())).thenReturn(null);
//        when(fileValidationService.validateAndReadImageWithDimensions(any())).thenReturn(null);
//        when(fileValidationService.validateFileHash(anyString(),
// any())).thenReturn(errorResponse);
//
//        // When & Then
//        MockMultipartFile hashPart =
//                new MockMultipartFile("hash", "", "text/plain", "invalid_hash".getBytes());
//        mockMvc.perform(
//                        multipart(HttpMethod.PUT, "/api/upload/avatar")
//                                .file(file)
//                                .file(hashPart)
//                                .accept(MediaType.APPLICATION_JSON)
//                                .with(authenticatedUser()))
//                .andExpect(status().isBadRequest())
//                .andExpect(jsonPath("$.code").value(400))
//                .andExpect(jsonPath("$.message").value("File hash does not match"));
//    }
//
//    @Test
//    void uploadAvatar_IOException() throws Exception {
//        // Given
//        MockMultipartFile file =
//                new MockMultipartFile(
//                        "avatar",
//                        "test.jpg",
//                        avatarProperties.getAllowedContentTypes()[0],
//                        (byte[]) null) {
//                    @Override
//                    public byte[] getBytes() throws IOException {
//                        throw new IOException("Failed to read file");
//                    }
//                };
//
//        when(fileValidationService.validateFileType(any())).thenReturn(null);
//        when(fileValidationService.validateFileSize(any())).thenReturn(null);
//
//        // When & Then
//        MockMultipartFile hashPart =
//                new MockMultipartFile("hash", "", "text/plain", TEST_HASH.getBytes());
//        mockMvc.perform(
//                        multipart(HttpMethod.PUT, "/api/upload/avatar")
//                                .file(file)
//                                .file(hashPart)
//                                .accept(MediaType.APPLICATION_JSON)
//                                .with(authenticatedUser()))
//                .andExpect(status().isBadRequest())
//                .andExpect(jsonPath("$.code").value(400))
//                .andExpect(jsonPath("$.message").value("Failed to read file"));
//    }
//
//    @Test
//    void uploadAvatar_UploadServiceError() throws Exception {
//        // Given
//        MockMultipartFile file = createTestImageFile();
//        AvatarUploadResponse errorResponse =
//                new AvatarUploadResponse(500, "Failed to upload file", null);
//
//        // Mock all validation services to return null (no error)
//        when(fileValidationService.validateFileType(any())).thenReturn(null);
//        when(fileValidationService.validateFileSize(any())).thenReturn(null);
//        when(fileValidationService.validateAndReadImageWithDimensions(any())).thenReturn(null);
//        when(fileValidationService.validateFileHash(anyString(), any())).thenReturn(null);
//
//        when(avatarUploadService.uploadAvatar(eq(TEST_USER_ID), any(), anyString(), anyString()))
//                .thenReturn(ResponseEntity.status(500).body(errorResponse));
//
//        // When & Then
//        MockMultipartFile hashPart =
//                new MockMultipartFile("hash", "", "text/plain", TEST_HASH.getBytes());
//        mockMvc.perform(
//                        multipart(HttpMethod.PUT, "/api/upload/avatar")
//                                .file(file)
//                                .file(hashPart)
//                                .accept(MediaType.APPLICATION_JSON)
//                                .with(authenticatedUser()))
//                .andExpect(status().isInternalServerError())
//                .andExpect(jsonPath("$.code").value(500))
//                .andExpect(jsonPath("$.message").value("Failed to upload file"));
//    }
//
//    @Test
//    void uploadAvatar_MissingHashParameter() throws Exception {
//        // Given
//        MockMultipartFile file = createTestImageFile();
//
//        // When & Then
//        mockMvc.perform(
//                        multipart(HttpMethod.PUT, "/api/upload/avatar")
//                                .file(file)
//                                .accept(MediaType.APPLICATION_JSON)
//                                .with(authenticatedUser()))
//                .andExpect(status().isBadRequest());
//    }
//
//    @Test
//    void uploadAvatar_MissingFileParameter() throws Exception {
//        // When & Then
//        MockMultipartFile hashPart =
//                new MockMultipartFile("hash", "", "text/plain", TEST_HASH.getBytes());
//        mockMvc.perform(
//                        multipart(HttpMethod.PUT, "/api/upload/avatar")
//                                .file(hashPart)
//                                .accept(MediaType.APPLICATION_JSON)
//                                .with(authenticatedUser()))
//                .andExpect(status().isBadRequest());
//    }
// }
