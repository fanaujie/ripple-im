package com.fanaujie.ripple.uploadgateway.controller;

import com.fanaujie.ripple.storage.model.GroupMemberInfo;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import com.fanaujie.ripple.uploadgateway.config.AvatarProperties;
import com.fanaujie.ripple.uploadgateway.config.SecurityConfig;
import com.fanaujie.ripple.uploadgateway.dto.AvatarUploadData;
import com.fanaujie.ripple.uploadgateway.dto.AvatarUploadResponse;
import com.fanaujie.ripple.uploadgateway.exception.InvalidFileExtensionException;
import com.fanaujie.ripple.uploadgateway.service.AvatarFileValidationService;
import com.fanaujie.ripple.uploadgateway.service.AvatarUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UploadController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {"oauth2.jwk.secret=dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdG9rZW4tMTIzNA=="})
class UploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AvatarUploadService avatarUploadService;

    @MockitoBean
    private AvatarFileValidationService fileValidationService;

    @MockitoBean
    private RippleStorageFacade storageFacade;

    private static final String USER_ID = "1";
    private static final String GROUP_ID = "100";
    private static final String TEST_HASH = "a665a45920422f9d417e4867efdc4fb8a04a1f3fff1fa07e998e86f7f7a27ae3";
    private static final String TEST_AVATAR_URL = "http://minio:9000/ripple-avatars/test.jpg";

    @BeforeEach
    void setUp() {
        reset(avatarUploadService, fileValidationService, storageFacade);
    }

    private MockMultipartFile createTestImageFile() {
        return new MockMultipartFile(
                "avatar",
                "test.jpg",
                "image/jpeg",
                "test image content".getBytes());
    }

    // ==================== uploadUserAvatar Tests ====================

    @Test
    void uploadUserAvatar_Success() throws Exception {
        AvatarUploadResponse successResponse =
                new AvatarUploadResponse(200, "success", new AvatarUploadData(TEST_AVATAR_URL));

        when(fileValidationService.validateFileSize(any())).thenReturn(null);
        when(fileValidationService.validateAndReadImageWithDimensions(any())).thenReturn(null);
        when(fileValidationService.validateFileHash(anyString(), any())).thenReturn(null);
        when(fileValidationService.extractFileExtension(anyString())).thenReturn(".jpg");
        when(avatarUploadService.uploadUserAvatar(eq(1L), any(), anyString()))
                .thenReturn(ResponseEntity.ok(successResponse));

        MockMultipartFile avatarFile = createTestImageFile();
        MockMultipartFile hashPart = new MockMultipartFile("hash", "", "text/plain", TEST_HASH.getBytes());
        MockMultipartFile filenamePart = new MockMultipartFile("originalFilename", "", "text/plain", "test.jpg".getBytes());

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/upload/avatar")
                        .file(avatarFile)
                        .file(hashPart)
                        .file(filenamePart)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .with(jwt().jwt(builder -> builder.subject(USER_ID).claim("scope", "user"))
                                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.avatarUrl").value(TEST_AVATAR_URL));

        verify(avatarUploadService).uploadUserAvatar(eq(1L), any(), eq(TEST_HASH + ".jpg"));
    }

    @Test
    void uploadUserAvatar_Unauthorized() throws Exception {
        MockMultipartFile avatarFile = createTestImageFile();
        MockMultipartFile hashPart = new MockMultipartFile("hash", "", "text/plain", TEST_HASH.getBytes());
        MockMultipartFile filenamePart = new MockMultipartFile("originalFilename", "", "text/plain", "test.jpg".getBytes());

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/upload/avatar")
                        .file(avatarFile)
                        .file(hashPart)
                        .file(filenamePart)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(avatarUploadService);
    }

    @Test
    void uploadUserAvatar_FileSizeValidationError() throws Exception {
        AvatarUploadResponse errorResponse = new AvatarUploadResponse(400, "File size exceeds maximum allowed size", null);
        when(fileValidationService.validateFileSize(any())).thenReturn(errorResponse);

        MockMultipartFile avatarFile = createTestImageFile();
        MockMultipartFile hashPart = new MockMultipartFile("hash", "", "text/plain", TEST_HASH.getBytes());
        MockMultipartFile filenamePart = new MockMultipartFile("originalFilename", "", "text/plain", "test.jpg".getBytes());

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/upload/avatar")
                        .file(avatarFile)
                        .file(hashPart)
                        .file(filenamePart)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .with(jwt().jwt(builder -> builder.subject(USER_ID).claim("scope", "user"))
                                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("File size exceeds maximum allowed size"));

        verifyNoInteractions(avatarUploadService);
    }

    @Test
    void uploadUserAvatar_ImageDimensionValidationError() throws Exception {
        AvatarUploadResponse errorResponse = new AvatarUploadResponse(400, "Image dimensions exceed maximum allowed size", null);

        when(fileValidationService.validateFileSize(any())).thenReturn(null);
        when(fileValidationService.validateAndReadImageWithDimensions(any())).thenReturn(errorResponse);

        MockMultipartFile avatarFile = createTestImageFile();
        MockMultipartFile hashPart = new MockMultipartFile("hash", "", "text/plain", TEST_HASH.getBytes());
        MockMultipartFile filenamePart = new MockMultipartFile("originalFilename", "", "text/plain", "test.jpg".getBytes());

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/upload/avatar")
                        .file(avatarFile)
                        .file(hashPart)
                        .file(filenamePart)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .with(jwt().jwt(builder -> builder.subject(USER_ID).claim("scope", "user"))
                                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Image dimensions exceed maximum allowed size"));

        verifyNoInteractions(avatarUploadService);
    }

    @Test
    void uploadUserAvatar_HashValidationError() throws Exception {
        AvatarUploadResponse errorResponse = new AvatarUploadResponse(400, "File hash does not match", null);

        when(fileValidationService.validateFileSize(any())).thenReturn(null);
        when(fileValidationService.validateAndReadImageWithDimensions(any())).thenReturn(null);
        when(fileValidationService.validateFileHash(anyString(), any())).thenReturn(errorResponse);

        MockMultipartFile avatarFile = createTestImageFile();
        MockMultipartFile hashPart = new MockMultipartFile("hash", "", "text/plain", "invalid_hash".getBytes());
        MockMultipartFile filenamePart = new MockMultipartFile("originalFilename", "", "text/plain", "test.jpg".getBytes());

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/upload/avatar")
                        .file(avatarFile)
                        .file(hashPart)
                        .file(filenamePart)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .with(jwt().jwt(builder -> builder.subject(USER_ID).claim("scope", "user"))
                                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("File hash does not match"));

        verifyNoInteractions(avatarUploadService);
    }

    @Test
    void uploadUserAvatar_InvalidFileExtension() throws Exception {
        when(fileValidationService.validateFileSize(any())).thenReturn(null);
        when(fileValidationService.validateAndReadImageWithDimensions(any())).thenReturn(null);
        when(fileValidationService.validateFileHash(anyString(), any())).thenReturn(null);
        when(fileValidationService.extractFileExtension(anyString()))
                .thenThrow(new InvalidFileExtensionException("Invalid file extension"));

        MockMultipartFile avatarFile = createTestImageFile();
        MockMultipartFile hashPart = new MockMultipartFile("hash", "", "text/plain", TEST_HASH.getBytes());
        MockMultipartFile filenamePart = new MockMultipartFile("originalFilename", "", "text/plain", "test.exe".getBytes());

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/upload/avatar")
                        .file(avatarFile)
                        .file(hashPart)
                        .file(filenamePart)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .with(jwt().jwt(builder -> builder.subject(USER_ID).claim("scope", "user"))
                                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Invalid file extension"));

        verifyNoInteractions(avatarUploadService);
    }

    @Test
    void uploadUserAvatar_ServiceError() throws Exception {
        AvatarUploadResponse errorResponse = new AvatarUploadResponse(500, "Failed to upload file", null);

        when(fileValidationService.validateFileSize(any())).thenReturn(null);
        when(fileValidationService.validateAndReadImageWithDimensions(any())).thenReturn(null);
        when(fileValidationService.validateFileHash(anyString(), any())).thenReturn(null);
        when(fileValidationService.extractFileExtension(anyString())).thenReturn(".jpg");
        when(avatarUploadService.uploadUserAvatar(eq(1L), any(), anyString()))
                .thenReturn(ResponseEntity.status(500).body(errorResponse));

        MockMultipartFile avatarFile = createTestImageFile();
        MockMultipartFile hashPart = new MockMultipartFile("hash", "", "text/plain", TEST_HASH.getBytes());
        MockMultipartFile filenamePart = new MockMultipartFile("originalFilename", "", "text/plain", "test.jpg".getBytes());

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/upload/avatar")
                        .file(avatarFile)
                        .file(hashPart)
                        .file(filenamePart)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .with(jwt().jwt(builder -> builder.subject(USER_ID).claim("scope", "user"))
                                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user")))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("Failed to upload file"));
    }

    @Test
    void uploadUserAvatar_MissingHashParameter() throws Exception {
        MockMultipartFile avatarFile = createTestImageFile();
        MockMultipartFile filenamePart = new MockMultipartFile("originalFilename", "", "text/plain", "test.jpg".getBytes());

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/upload/avatar")
                        .file(avatarFile)
                        .file(filenamePart)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .with(jwt().jwt(builder -> builder.subject(USER_ID).claim("scope", "user"))
                                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user")))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(avatarUploadService);
    }

    @Test
    void uploadUserAvatar_MissingAvatarFile() throws Exception {
        MockMultipartFile hashPart = new MockMultipartFile("hash", "", "text/plain", TEST_HASH.getBytes());
        MockMultipartFile filenamePart = new MockMultipartFile("originalFilename", "", "text/plain", "test.jpg".getBytes());

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/upload/avatar")
                        .file(hashPart)
                        .file(filenamePart)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .with(jwt().jwt(builder -> builder.subject(USER_ID).claim("scope", "user"))
                                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user")))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(avatarUploadService);
    }

    // ==================== uploadGroupAvatar Tests ====================

    @Test
    void uploadGroupAvatar_Success() throws Exception {
        AvatarUploadResponse successResponse =
                new AvatarUploadResponse(200, "success", new AvatarUploadData(TEST_AVATAR_URL));

        GroupMemberInfo memberInfo = new GroupMemberInfo(100L, 1L, "User1", "avatar.jpg");
        when(storageFacade.getGroupMembersInfo(100L)).thenReturn(List.of(memberInfo));
        when(fileValidationService.validateFileSize(any())).thenReturn(null);
        when(fileValidationService.validateAndReadImageWithDimensions(any())).thenReturn(null);
        when(fileValidationService.validateFileHash(anyString(), any())).thenReturn(null);
        when(fileValidationService.extractFileExtension(anyString())).thenReturn(".jpg");
        when(avatarUploadService.uploadGroupAvatar(eq(100L), eq(1L), any(), anyString()))
                .thenReturn(ResponseEntity.ok(successResponse));

        MockMultipartFile avatarFile = createTestImageFile();
        MockMultipartFile hashPart = new MockMultipartFile("hash", "", "text/plain", TEST_HASH.getBytes());
        MockMultipartFile filenamePart = new MockMultipartFile("originalFilename", "", "text/plain", "test.jpg".getBytes());

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/upload/groups/{groupId}/avatar", GROUP_ID)
                        .file(avatarFile)
                        .file(hashPart)
                        .file(filenamePart)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .with(jwt().jwt(builder -> builder.subject(USER_ID).claim("scope", "user"))
                                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.avatarUrl").value(TEST_AVATAR_URL));

        verify(avatarUploadService).uploadGroupAvatar(eq(100L), eq(1L), any(), eq(TEST_HASH + ".jpg"));
    }

    @Test
    void uploadGroupAvatar_NotGroupMember_ReturnsForbidden() throws Exception {
        // User 1 is not a member of the group
        GroupMemberInfo otherMember = new GroupMemberInfo(100L, 999L, "Other", "avatar.jpg");
        when(storageFacade.getGroupMembersInfo(100L)).thenReturn(List.of(otherMember));

        MockMultipartFile avatarFile = createTestImageFile();
        MockMultipartFile hashPart = new MockMultipartFile("hash", "", "text/plain", TEST_HASH.getBytes());
        MockMultipartFile filenamePart = new MockMultipartFile("originalFilename", "", "text/plain", "test.jpg".getBytes());

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/upload/groups/{groupId}/avatar", GROUP_ID)
                        .file(avatarFile)
                        .file(hashPart)
                        .file(filenamePart)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .with(jwt().jwt(builder -> builder.subject(USER_ID).claim("scope", "user"))
                                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user")))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("Forbidden: Not a group member"));

        verifyNoInteractions(avatarUploadService);
    }

    @Test
    void uploadGroupAvatar_InvalidGroupId() throws Exception {
        MockMultipartFile avatarFile = createTestImageFile();
        MockMultipartFile hashPart = new MockMultipartFile("hash", "", "text/plain", TEST_HASH.getBytes());
        MockMultipartFile filenamePart = new MockMultipartFile("originalFilename", "", "text/plain", "test.jpg".getBytes());

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/upload/groups/{groupId}/avatar", "invalid")
                        .file(avatarFile)
                        .file(hashPart)
                        .file(filenamePart)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .with(jwt().jwt(builder -> builder.subject(USER_ID).claim("scope", "user"))
                                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Invalid group ID format"));

        verifyNoInteractions(avatarUploadService);
    }

    @Test
    void uploadGroupAvatar_FileSizeValidationError() throws Exception {
        AvatarUploadResponse errorResponse = new AvatarUploadResponse(400, "File size exceeds maximum allowed size", null);

        GroupMemberInfo memberInfo = new GroupMemberInfo(100L, 1L, "User1", "avatar.jpg");
        when(storageFacade.getGroupMembersInfo(100L)).thenReturn(List.of(memberInfo));
        when(fileValidationService.validateFileSize(any())).thenReturn(errorResponse);

        MockMultipartFile avatarFile = createTestImageFile();
        MockMultipartFile hashPart = new MockMultipartFile("hash", "", "text/plain", TEST_HASH.getBytes());
        MockMultipartFile filenamePart = new MockMultipartFile("originalFilename", "", "text/plain", "test.jpg".getBytes());

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/upload/groups/{groupId}/avatar", GROUP_ID)
                        .file(avatarFile)
                        .file(hashPart)
                        .file(filenamePart)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .with(jwt().jwt(builder -> builder.subject(USER_ID).claim("scope", "user"))
                                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("File size exceeds maximum allowed size"));

        verifyNoInteractions(avatarUploadService);
    }

    @Test
    void uploadGroupAvatar_MembershipCheckError() throws Exception {
        when(storageFacade.getGroupMembersInfo(100L)).thenThrow(new RuntimeException("Database error"));

        MockMultipartFile avatarFile = createTestImageFile();
        MockMultipartFile hashPart = new MockMultipartFile("hash", "", "text/plain", TEST_HASH.getBytes());
        MockMultipartFile filenamePart = new MockMultipartFile("originalFilename", "", "text/plain", "test.jpg".getBytes());

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/upload/groups/{groupId}/avatar", GROUP_ID)
                        .file(avatarFile)
                        .file(hashPart)
                        .file(filenamePart)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .with(jwt().jwt(builder -> builder.subject(USER_ID).claim("scope", "user"))
                                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user")))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("Failed to verify group membership"));

        verifyNoInteractions(avatarUploadService);
    }

    @Test
    void uploadGroupAvatar_Unauthorized() throws Exception {
        MockMultipartFile avatarFile = createTestImageFile();
        MockMultipartFile hashPart = new MockMultipartFile("hash", "", "text/plain", TEST_HASH.getBytes());
        MockMultipartFile filenamePart = new MockMultipartFile("originalFilename", "", "text/plain", "test.jpg".getBytes());

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/upload/groups/{groupId}/avatar", GROUP_ID)
                        .file(avatarFile)
                        .file(hashPart)
                        .file(filenamePart)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(avatarUploadService);
    }
}
