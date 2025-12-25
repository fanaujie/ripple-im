package com.fanaujie.ripple.uploadgateway.controller;

import com.fanaujie.ripple.uploadgateway.config.SecurityConfig;
import com.fanaujie.ripple.uploadgateway.dto.*;
import com.fanaujie.ripple.uploadgateway.exception.FileSizeExceededException;
import com.fanaujie.ripple.uploadgateway.exception.InvalidFileExtensionException;
import com.fanaujie.ripple.uploadgateway.service.ChunkUploadService;
import com.fanaujie.ripple.uploadgateway.utils.FileUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MessageAttachmentController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {"oauth2.jwk.secret=dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdG9rZW4tMTIzNA=="})
class MessageAttachmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ChunkUploadService chunkUploadService;

    @MockitoBean(name = "messageAttachmentFileUtils")
    private FileUtils messageAttachmentFileUtils;

    private static final String USER_ID = "1";
    private static final String TEST_HASH = "a665a45920422f9d417e4867efdc4fb8a04a1f3fff1fa07e998e86f7f7a27ae3";
    private static final String TEST_OBJECT_NAME = "a665a45920422f9d417e4867efdc4fb8a04a1f3fff1fa07e998e86f7f7a27ae3.pdf";
    private static final String TEST_FILE_URL = "http://minio:9000/ripple-attachments/test.pdf";

    @BeforeEach
    void setUp() {
        reset(chunkUploadService, messageAttachmentFileUtils);
    }

    // ==================== initiateUpload Tests ====================

    @Test
    void initiateUpload_Success_SingleMode() throws Exception {
        InitiateUploadData data = InitiateUploadData.singleMode(TEST_OBJECT_NAME);

        when(messageAttachmentFileUtils.extractExtension("test.pdf")).thenReturn(".pdf");
        doNothing().when(messageAttachmentFileUtils).validateFileSize(anyLong());
        when(chunkUploadService.initiateUpload(any(InitiateUploadRequest.class), eq(TEST_OBJECT_NAME)))
                .thenReturn(data);

        InitiateUploadRequest request = new InitiateUploadRequest();
        request.setFileSize(1024);
        request.setFileSha256(TEST_HASH);
        request.setOriginalFilename("test.pdf");

        mockMvc.perform(post("/api/upload/attachment/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt().jwt(builder -> builder.subject(USER_ID).claim("scope", "user"))
                                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.uploadMode").value(1))
                .andExpect(jsonPath("$.data.objectName").value(TEST_OBJECT_NAME));

        verify(chunkUploadService).initiateUpload(any(InitiateUploadRequest.class), eq(TEST_OBJECT_NAME));
    }

    @Test
    void initiateUpload_Success_ChunkMode() throws Exception {
        InitiateUploadData data = InitiateUploadData.chunkMode(TEST_OBJECT_NAME, 5242880L, 1, 10);

        when(messageAttachmentFileUtils.extractExtension("test.pdf")).thenReturn(".pdf");
        doNothing().when(messageAttachmentFileUtils).validateFileSize(anyLong());
        when(chunkUploadService.initiateUpload(any(InitiateUploadRequest.class), eq(TEST_OBJECT_NAME)))
                .thenReturn(data);

        InitiateUploadRequest request = new InitiateUploadRequest();
        request.setFileSize(52428800); // 50MB
        request.setFileSha256(TEST_HASH);
        request.setOriginalFilename("test.pdf");

        mockMvc.perform(post("/api/upload/attachment/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt().jwt(builder -> builder.subject(USER_ID).claim("scope", "user"))
                                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.uploadMode").value(2))
                .andExpect(jsonPath("$.data.totalChunks").value(10));
    }

    @Test
    void initiateUpload_FileSizeExceeded() throws Exception {
        doThrow(new FileSizeExceededException("File size exceeds limit"))
                .when(messageAttachmentFileUtils).validateFileSize(anyLong());

        InitiateUploadRequest request = new InitiateUploadRequest();
        request.setFileSize(1000000000); // Very large file
        request.setFileSha256(TEST_HASH);
        request.setOriginalFilename("test.pdf");

        mockMvc.perform(post("/api/upload/attachment/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt().jwt(builder -> builder.subject(USER_ID).claim("scope", "user"))
                                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Invalid file parameters"));

        verifyNoInteractions(chunkUploadService);
    }

    @Test
    void initiateUpload_InvalidFileExtension() throws Exception {
        doNothing().when(messageAttachmentFileUtils).validateFileSize(anyLong());
        when(messageAttachmentFileUtils.extractExtension("test.exe"))
                .thenThrow(new InvalidFileExtensionException("Invalid extension"));

        InitiateUploadRequest request = new InitiateUploadRequest();
        request.setFileSize(1024);
        request.setFileSha256(TEST_HASH);
        request.setOriginalFilename("test.exe");

        mockMvc.perform(post("/api/upload/attachment/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt().jwt(builder -> builder.subject(USER_ID).claim("scope", "user"))
                                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Invalid file parameters"));

        verifyNoInteractions(chunkUploadService);
    }

    @Test
    void initiateUpload_Unauthorized() throws Exception {
        InitiateUploadRequest request = new InitiateUploadRequest();
        request.setFileSize(1024);
        request.setFileSha256(TEST_HASH);
        request.setOriginalFilename("test.pdf");

        mockMvc.perform(post("/api/upload/attachment/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(chunkUploadService);
    }

    // ==================== uploadChunk Tests ====================

    @Test
    void uploadChunk_Success() throws Exception {
        byte[] chunkData = "test chunk data".getBytes();
        String chunkSha256 = DigestUtils.sha256Hex(chunkData);

        when(chunkUploadService.uploadChunkPart(eq(TEST_OBJECT_NAME), eq(1), any(byte[].class)))
                .thenReturn("chunk-1");

        MockMultipartFile chunk = new MockMultipartFile("chunk", "chunk-1", "application/octet-stream", chunkData);

        mockMvc.perform(multipart("/api/upload/attachment/chunk")
                        .file(chunk)
                        .param("objectName", TEST_OBJECT_NAME)
                        .param("chunkNumber", "1")
                        .param("chunkSha256", chunkSha256)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .with(jwt().jwt(builder -> builder.subject(USER_ID).claim("scope", "user"))
                                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"));

        verify(chunkUploadService).uploadChunkPart(eq(TEST_OBJECT_NAME), eq(1), any(byte[].class));
    }

    @Test
    void uploadChunk_HashMismatch() throws Exception {
        byte[] chunkData = "test chunk data".getBytes();

        MockMultipartFile chunk = new MockMultipartFile("chunk", "chunk-1", "application/octet-stream", chunkData);

        mockMvc.perform(multipart("/api/upload/attachment/chunk")
                        .file(chunk)
                        .param("objectName", TEST_OBJECT_NAME)
                        .param("chunkNumber", "1")
                        .param("chunkSha256", "wrong_hash")
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .with(jwt().jwt(builder -> builder.subject(USER_ID).claim("scope", "user"))
                                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Chunk hash mismatch"));

        verifyNoInteractions(chunkUploadService);
    }

    @Test
    void uploadChunk_StorageFailure() throws Exception {
        byte[] chunkData = "test chunk data".getBytes();
        String chunkSha256 = DigestUtils.sha256Hex(chunkData);

        when(chunkUploadService.uploadChunkPart(eq(TEST_OBJECT_NAME), eq(1), any(byte[].class)))
                .thenReturn(null);

        MockMultipartFile chunk = new MockMultipartFile("chunk", "chunk-1", "application/octet-stream", chunkData);

        mockMvc.perform(multipart("/api/upload/attachment/chunk")
                        .file(chunk)
                        .param("objectName", TEST_OBJECT_NAME)
                        .param("chunkNumber", "1")
                        .param("chunkSha256", chunkSha256)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .with(jwt().jwt(builder -> builder.subject(USER_ID).claim("scope", "user"))
                                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user")))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500));

        verify(chunkUploadService).uploadChunkPart(eq(TEST_OBJECT_NAME), eq(1), any(byte[].class));
    }

    @Test
    void uploadChunk_Unauthorized() throws Exception {
        byte[] chunkData = "test chunk data".getBytes();
        String chunkSha256 = DigestUtils.sha256Hex(chunkData);

        MockMultipartFile chunk = new MockMultipartFile("chunk", "chunk-1", "application/octet-stream", chunkData);

        mockMvc.perform(multipart("/api/upload/attachment/chunk")
                        .file(chunk)
                        .param("objectName", TEST_OBJECT_NAME)
                        .param("chunkNumber", "1")
                        .param("chunkSha256", chunkSha256)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        }))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(chunkUploadService);
    }

    // ==================== completeUpload Tests ====================

    @Test
    void completeUpload_Success() throws Exception {
        CompleteUploadData data = CompleteUploadData.success(TEST_FILE_URL);
        when(chunkUploadService.completeUpload(any(CompleteUploadRequest.class))).thenReturn(data);

        CompleteUploadRequest request = new CompleteUploadRequest();
        request.setObjectName(TEST_OBJECT_NAME);
        request.setTotalChunks(5);

        mockMvc.perform(post("/api/upload/attachment/chunk/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt().jwt(builder -> builder.subject(USER_ID).claim("scope", "user"))
                                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.fileUrl").value(TEST_FILE_URL));

        verify(chunkUploadService).completeUpload(any(CompleteUploadRequest.class));
    }

    @Test
    void completeUpload_Failure() throws Exception {
        when(chunkUploadService.completeUpload(any(CompleteUploadRequest.class))).thenReturn(null);

        CompleteUploadRequest request = new CompleteUploadRequest();
        request.setObjectName(TEST_OBJECT_NAME);
        request.setTotalChunks(5);

        mockMvc.perform(post("/api/upload/attachment/chunk/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt().jwt(builder -> builder.subject(USER_ID).claim("scope", "user"))
                                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("Failed to complete upload"));

        verify(chunkUploadService).completeUpload(any(CompleteUploadRequest.class));
    }

    @Test
    void completeUpload_Unauthorized() throws Exception {
        CompleteUploadRequest request = new CompleteUploadRequest();
        request.setObjectName(TEST_OBJECT_NAME);
        request.setTotalChunks(5);

        mockMvc.perform(post("/api/upload/attachment/chunk/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(chunkUploadService);
    }

    // ==================== singleUpload Tests ====================

    @Test
    void singleUpload_Success() throws Exception {
        byte[] fileData = "test file data".getBytes();
        String fileSha256 = DigestUtils.sha256Hex(fileData);

        CompleteUploadData data = CompleteUploadData.success(TEST_FILE_URL);
        when(chunkUploadService.singleUpload(eq(TEST_OBJECT_NAME), any(byte[].class))).thenReturn(data);

        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", fileData);

        mockMvc.perform(multipart("/api/upload/attachment/single")
                        .file(file)
                        .param("objectName", TEST_OBJECT_NAME)
                        .param("fileSha256", fileSha256)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .with(jwt().jwt(builder -> builder.subject(USER_ID).claim("scope", "user"))
                                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.fileUrl").value(TEST_FILE_URL));

        verify(chunkUploadService).singleUpload(eq(TEST_OBJECT_NAME), any(byte[].class));
    }

    @Test
    void singleUpload_HashMismatch() throws Exception {
        byte[] fileData = "test file data".getBytes();

        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", fileData);

        mockMvc.perform(multipart("/api/upload/attachment/single")
                        .file(file)
                        .param("objectName", TEST_OBJECT_NAME)
                        .param("fileSha256", "wrong_hash")
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .with(jwt().jwt(builder -> builder.subject(USER_ID).claim("scope", "user"))
                                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("File hash mismatch"));

        verifyNoInteractions(chunkUploadService);
    }

    @Test
    void singleUpload_UploadFailure() throws Exception {
        byte[] fileData = "test file data".getBytes();
        String fileSha256 = DigestUtils.sha256Hex(fileData);

        when(chunkUploadService.singleUpload(eq(TEST_OBJECT_NAME), any(byte[].class))).thenReturn(null);

        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", fileData);

        mockMvc.perform(multipart("/api/upload/attachment/single")
                        .file(file)
                        .param("objectName", TEST_OBJECT_NAME)
                        .param("fileSha256", fileSha256)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .with(jwt().jwt(builder -> builder.subject(USER_ID).claim("scope", "user"))
                                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user")))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("Failed to upload file"));

        verify(chunkUploadService).singleUpload(eq(TEST_OBJECT_NAME), any(byte[].class));
    }

    @Test
    void singleUpload_Unauthorized() throws Exception {
        byte[] fileData = "test file data".getBytes();
        String fileSha256 = DigestUtils.sha256Hex(fileData);

        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", fileData);

        mockMvc.perform(multipart("/api/upload/attachment/single")
                        .file(file)
                        .param("objectName", TEST_OBJECT_NAME)
                        .param("fileSha256", fileSha256)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        }))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(chunkUploadService);
    }

    // ==================== abortUpload Tests ====================

    @Test
    void abortUpload_Success() throws Exception {
        doNothing().when(chunkUploadService).abortUpload(any(AbortUploadRequest.class));

        AbortUploadRequest request = new AbortUploadRequest();
        request.setObjectName(TEST_OBJECT_NAME);

        mockMvc.perform(delete("/api/upload/attachment/abort")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt().jwt(builder -> builder.subject(USER_ID).claim("scope", "user"))
                                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"));

        verify(chunkUploadService).abortUpload(any(AbortUploadRequest.class));
    }

    @Test
    void abortUpload_Failure() throws Exception {
        doThrow(new RuntimeException("Storage error"))
                .when(chunkUploadService).abortUpload(any(AbortUploadRequest.class));

        AbortUploadRequest request = new AbortUploadRequest();
        request.setObjectName(TEST_OBJECT_NAME);

        mockMvc.perform(delete("/api/upload/attachment/abort")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt().jwt(builder -> builder.subject(USER_ID).claim("scope", "user"))
                                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user")))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("Failed to abort upload"));

        verify(chunkUploadService).abortUpload(any(AbortUploadRequest.class));
    }

    @Test
    void abortUpload_Unauthorized() throws Exception {
        AbortUploadRequest request = new AbortUploadRequest();
        request.setObjectName(TEST_OBJECT_NAME);

        mockMvc.perform(delete("/api/upload/attachment/abort")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(chunkUploadService);
    }
}
