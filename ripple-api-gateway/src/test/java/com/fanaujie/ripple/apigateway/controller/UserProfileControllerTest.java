package com.fanaujie.ripple.apigateway.controller;
//
// import com.fanaujie.ripple.apigateway.config.SecurityConfig;
// import com.fanaujie.ripple.apigateway.dto.CommonResponse;
// import com.fanaujie.ripple.apigateway.dto.UpdateNickNameRequest;
// import com.fanaujie.ripple.apigateway.dto.UserProfileData;
// import com.fanaujie.ripple.apigateway.dto.UserProfileResponse;
// import com.fanaujie.ripple.apigateway.service.UserProfileService;
// import com.fasterxml.jackson.databind.ObjectMapper;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.extension.ExtendWith;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
// import org.springframework.boot.test.context.SpringBootTest;
// import org.springframework.http.HttpStatus;
// import org.springframework.http.MediaType;
// import org.springframework.http.ResponseEntity;
// import org.springframework.security.core.authority.SimpleGrantedAuthority;
// import org.springframework.test.context.ContextConfiguration;
// import org.springframework.test.context.TestPropertySource;
// import org.springframework.test.context.bean.override.mockito.MockitoBean;
// import org.springframework.test.context.junit.jupiter.SpringExtension;
// import org.springframework.test.web.servlet.MockMvc;
// import org.springframework.test.web.servlet.request.RequestPostProcessor;
// import org.springframework.test.web.servlet.setup.MockMvcBuilders;
// import org.springframework.web.context.WebApplicationContext;
//
// import static org.mockito.Mockito.verify;
// import static org.mockito.Mockito.when;
// import static
// org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
// import static
// org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
// import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
// import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
//
// @ContextConfiguration(classes = {SecurityConfig.class, UserProfileController.class})
// @WebMvcTest
// @TestPropertySource(
//        properties = {
//            "oauth2.jwk.secret=dGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQ="
//        })
// class UserProfileControllerTest {
//
//    private MockMvc mockMvc;
//
//    @Autowired private WebApplicationContext context;
//
//    @MockitoBean private UserProfileService userProfileService;
//
//    @Autowired private ObjectMapper objectMapper;
//
//    private static final Long TEST_ID = 1L;
//    private static final String TEST_NICKNAME = "Test User";
//    private static final String TEST_PORTRAIT = "avatar.jpg";
//
//    private RequestPostProcessor authenticatedUser() {
//        return jwt().authorities(new SimpleGrantedAuthority("ROLE_user"))
//                .jwt(jwt -> jwt.header("alg", "HS256").claim("sub", TEST_ID.toString()));
//    }
//
//    @BeforeEach
//    void setup() {
//        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
//    }
//
//    @Test
//    void getUserProfile_Success() throws Exception {
//        // Given
//        UserProfileData profileData = new UserProfileData(String.valueOf(TEST_ID), TEST_NICKNAME,
// TEST_PORTRAIT);
//        UserProfileResponse response = new UserProfileResponse(200, "success", profileData);
//
//        when(userProfileService.getUserProfile(TEST_ID)).thenReturn(ResponseEntity.ok(response));
//
//        // When & Then
//        mockMvc.perform(
//                        get("/api/profile")
//                                .with(authenticatedUser())
//                                .accept(MediaType.APPLICATION_JSON))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.code").value(200))
//                .andExpect(jsonPath("$.message").value("success"))
//                .andExpect(jsonPath("$.data.userId").value(TEST_ID))
//                .andExpect(jsonPath("$.data.nickName").value(TEST_NICKNAME))
//                .andExpect(jsonPath("$.data.avatar").value(TEST_PORTRAIT));
//
//        verify(userProfileService).getUserProfile(TEST_ID);
//    }
//
//    @Test
//    void getUserProfile_UserNotFound() throws Exception {
//        // Given
//        UserProfileResponse response = new UserProfileResponse(401, "User profile not found",
// null);
//
//        when(userProfileService.getUserProfile(TEST_ID))
//                .thenReturn(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response));
//
//        // When & Then
//        mockMvc.perform(
//                        get("/api/profile")
//                                .with(authenticatedUser())
//                                .accept(MediaType.APPLICATION_JSON))
//                .andExpect(status().isUnauthorized())
//                .andExpect(jsonPath("$.code").value(401))
//                .andExpect(jsonPath("$.message").value("User profile not found"))
//                .andExpect(jsonPath("$.data").isEmpty());
//
//        verify(userProfileService).getUserProfile(TEST_ID);
//    }
//
//    @Test
//    void getUserProfile_Unauthorized() throws Exception {
//        // When & Then - No authentication
//        mockMvc.perform(get("/api/profile").accept(MediaType.APPLICATION_JSON))
//                .andExpect(status().isUnauthorized());
//    }
//
//    @Test
//    void getUserProfile_WithUserId_Success() throws Exception {
//        // Given
//        Long targetUserId = 2L;
//        UserProfileData profileData = new UserProfileData(String.valueOf(targetUserId), "Target
// User", "target.jpg");
//        UserProfileResponse response = new UserProfileResponse(200, "success", profileData);
//
//
// when(userProfileService.getUserProfile(targetUserId)).thenReturn(ResponseEntity.ok(response));
//
//        // When & Then
//        mockMvc.perform(
//                        get("/api/profile")
//                                .param("userId", targetUserId.toString())
//                                .with(authenticatedUser())
//                                .accept(MediaType.APPLICATION_JSON))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.code").value(200))
//                .andExpect(jsonPath("$.message").value("success"))
//                .andExpect(jsonPath("$.data.userId").value(targetUserId))
//                .andExpect(jsonPath("$.data.nickName").value("Target User"))
//                .andExpect(jsonPath("$.data.avatar").value("target.jpg"));
//
//        verify(userProfileService).getUserProfile(targetUserId);
//    }
//
//    @Test
//    void getUserProfile_WithUserId_UserNotFound() throws Exception {
//        // Given
//        Long targetUserId = 999L;
//        UserProfileResponse response = new UserProfileResponse(404, "User profile not found",
// null);
//
//        when(userProfileService.getUserProfile(targetUserId))
//                .thenReturn(ResponseEntity.status(HttpStatus.NOT_FOUND).body(response));
//
//        // When & Then
//        mockMvc.perform(
//                        get("/api/profile")
//                                .param("userId", targetUserId.toString())
//                                .with(authenticatedUser())
//                                .accept(MediaType.APPLICATION_JSON))
//                .andExpect(status().isNotFound())
//                .andExpect(jsonPath("$.code").value(404))
//                .andExpect(jsonPath("$.message").value("User profile not found"))
//                .andExpect(jsonPath("$.data").isEmpty());
//
//        verify(userProfileService).getUserProfile(targetUserId);
//    }
//
//    @Test
//    void getUserProfile_WithInvalidUserId() throws Exception {
//        // When & Then - Invalid userId parameter
//        mockMvc.perform(
//                        get("/api/profile")
//                                .param("userId", "invalid")
//                                .with(authenticatedUser())
//                                .accept(MediaType.APPLICATION_JSON))
//                .andExpect(status().isBadRequest());
//    }
//
//    @Test
//    void getUserProfile_WithNegativeUserId() throws Exception {
//        // Given
//        Long negativeUserId = -1L;
//        UserProfileResponse response = new UserProfileResponse(404, "User profile not found",
// null);
//
//        when(userProfileService.getUserProfile(negativeUserId))
//                .thenReturn(ResponseEntity.status(HttpStatus.NOT_FOUND).body(response));
//
//        // When & Then
//        mockMvc.perform(
//                        get("/api/profile")
//                                .param("userId", negativeUserId.toString())
//                                .with(authenticatedUser())
//                                .accept(MediaType.APPLICATION_JSON))
//                .andExpect(status().isNotFound())
//                .andExpect(jsonPath("$.code").value(404))
//                .andExpect(jsonPath("$.message").value("User profile not found"));
//
//        verify(userProfileService).getUserProfile(negativeUserId);
//    }
//
//    @Test
//    void updateNickName_Success() throws Exception {
//        // Given
//        String newNickName = "New Nickname";
//        UpdateNickNameRequest request = new UpdateNickNameRequest(newNickName);
//        CommonResponse response = new CommonResponse(200, "success");
//
//        when(userProfileService.updateNickName(TEST_ID, newNickName))
//                .thenReturn(ResponseEntity.ok(response));
//
//        // When & Then
//        mockMvc.perform(
//                        put("/api/profile/nickname")
//                                .with(authenticatedUser())
//                                .contentType(MediaType.APPLICATION_JSON)
//                                .content(objectMapper.writeValueAsString(request))
//                                .accept(MediaType.APPLICATION_JSON))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.code").value(200))
//                .andExpect(jsonPath("$.message").value("success"));
//
//        verify(userProfileService).updateNickName(TEST_ID, newNickName);
//    }
//
//    @Test
//    void updateNickName_UserNotFound() throws Exception {
//        // Given
//        String newNickName = "New Nickname";
//        UpdateNickNameRequest request = new UpdateNickNameRequest(newNickName);
//        CommonResponse response = new CommonResponse(401, "User profile not found");
//
//        when(userProfileService.updateNickName(TEST_ID, newNickName))
//                .thenReturn(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response));
//
//        // When & Then
//        mockMvc.perform(
//                        put("/api/profile/nickname")
//                                .with(authenticatedUser())
//                                .contentType(MediaType.APPLICATION_JSON)
//                                .content(objectMapper.writeValueAsString(request))
//                                .accept(MediaType.APPLICATION_JSON))
//                .andExpect(status().isUnauthorized())
//                .andExpect(jsonPath("$.code").value(401))
//                .andExpect(jsonPath("$.message").value("User profile not found"));
//
//        verify(userProfileService).updateNickName(TEST_ID, newNickName);
//    }
//
//    @Test
//    void updateNickName_Unauthorized() throws Exception {
//        // Given
//        UpdateNickNameRequest request = new UpdateNickNameRequest("New Nickname");
//
//        // When & Then - No authentication
//        mockMvc.perform(
//                        put("/api/profile/nickname")
//                                .contentType(MediaType.APPLICATION_JSON)
//                                .content(objectMapper.writeValueAsString(request))
//                                .accept(MediaType.APPLICATION_JSON))
//                .andExpect(status().isUnauthorized());
//    }
//
//    @Test
//    void updateNickName_ValidationError_BlankNickName() throws Exception {
//        // Given
//        UpdateNickNameRequest request = new UpdateNickNameRequest("");
//
//        // When & Then
//        mockMvc.perform(
//                        put("/api/profile/nickname")
//                                .with(authenticatedUser())
//                                .contentType(MediaType.APPLICATION_JSON)
//                                .content(objectMapper.writeValueAsString(request))
//                                .accept(MediaType.APPLICATION_JSON))
//                .andExpect(status().isBadRequest());
//    }
//
//    @Test
//    void updateNickName_ValidationError_NullNickName() throws Exception {
//        // Given
//        UpdateNickNameRequest request = new UpdateNickNameRequest(null);
//
//        // When & Then
//        mockMvc.perform(
//                        put("/api/profile/nickname")
//                                .with(authenticatedUser())
//                                .contentType(MediaType.APPLICATION_JSON)
//                                .content(objectMapper.writeValueAsString(request))
//                                .accept(MediaType.APPLICATION_JSON))
//                .andExpect(status().isBadRequest());
//    }
//
//    @Test
//    void updateNickName_ValidationError_TooLongNickName() throws Exception {
//        // Given - Create a nickname longer than 50 characters
//        String longNickName =
//                "This is a very long nickname that exceeds the maximum allowed length of fifty
// characters";
//        UpdateNickNameRequest request = new UpdateNickNameRequest(longNickName);
//
//        // When & Then
//        mockMvc.perform(
//                        put("/api/profile/nickname")
//                                .with(authenticatedUser())
//                                .contentType(MediaType.APPLICATION_JSON)
//                                .content(objectMapper.writeValueAsString(request))
//                                .accept(MediaType.APPLICATION_JSON))
//                .andExpect(status().isBadRequest());
//    }
//
//    @Test
//    void updateNickName_InvalidJsonFormat() throws Exception {
//        // Given
//        String invalidJson = "{invalid json}";
//
//        // When & Then
//        mockMvc.perform(
//                        put("/api/profile/nickname")
//                                .with(authenticatedUser())
//                                .contentType(MediaType.APPLICATION_JSON)
//                                .content(invalidJson)
//                                .accept(MediaType.APPLICATION_JSON))
//                .andExpect(status().isBadRequest());
//    }
//
//    @Test
//    void updateNickName_MissingContentType() throws Exception {
//        // Given
//        UpdateNickNameRequest request = new UpdateNickNameRequest("Valid Nickname");
//
//        // When & Then
//        mockMvc.perform(
//                        put("/api/profile/nickname")
//                                .with(authenticatedUser())
//                                .content(objectMapper.writeValueAsString(request))
//                                .accept(MediaType.APPLICATION_JSON))
//                .andExpect(status().isUnsupportedMediaType());
//    }
//
//    @Test
//    void deleteAvatar_Success() throws Exception {
//        // Given
//        CommonResponse response = new CommonResponse(200, "success");
//
//        when(userProfileService.deleteAvatar(TEST_ID)).thenReturn(ResponseEntity.ok(response));
//
//        // When & Then
//        mockMvc.perform(
//                        delete("/api/profile/avatar")
//                                .with(authenticatedUser())
//                                .accept(MediaType.APPLICATION_JSON))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.code").value(200))
//                .andExpect(jsonPath("$.message").value("success"));
//
//        verify(userProfileService).deleteAvatar(TEST_ID);
//    }
//
//    @Test
//    void deleteAvatar_UserNotFound() throws Exception {
//        // Given
//        CommonResponse serviceResponse = new CommonResponse(401, "User profile not found");
//
//        when(userProfileService.deleteAvatar(TEST_ID))
//                .thenReturn(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(serviceResponse));
//
//        // When & Then - Controller returns 200 regardless of service response
//        mockMvc.perform(
//                        delete("/api/profile/avatar")
//                                .with(authenticatedUser())
//                                .accept(MediaType.APPLICATION_JSON))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.code").value(200))
//                .andExpect(jsonPath("$.message").value("success"));
//
//        verify(userProfileService).deleteAvatar(TEST_ID);
//    }
//
//    @Test
//    void deleteAvatar_Unauthorized() throws Exception {
//        // When & Then - No authentication
//        mockMvc.perform(delete("/api/profile/avatar").accept(MediaType.APPLICATION_JSON))
//                .andExpect(status().isUnauthorized());
//    }
//
//    @Test
//    void updateNickName_WithSpecialCharacters() throws Exception {
//        // Given
//        String specialNickName = "User@123!";
//        UpdateNickNameRequest request = new UpdateNickNameRequest(specialNickName);
//        CommonResponse response = new CommonResponse(200, "success");
//
//        when(userProfileService.updateNickName(TEST_ID, specialNickName))
//                .thenReturn(ResponseEntity.ok(response));
//
//        // When & Then
//        mockMvc.perform(
//                        put("/api/profile/nickname")
//                                .with(authenticatedUser())
//                                .contentType(MediaType.APPLICATION_JSON)
//                                .content(objectMapper.writeValueAsString(request))
//                                .accept(MediaType.APPLICATION_JSON))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.code").value(200))
//                .andExpect(jsonPath("$.message").value("success"));
//
//        verify(userProfileService).updateNickName(TEST_ID, specialNickName);
//    }
//
//    @Test
//    void updateNickName_WithUnicodeCharacters() throws Exception {
//        // Given
//        String unicodeNickName = "用户123";
//        UpdateNickNameRequest request = new UpdateNickNameRequest(unicodeNickName);
//        CommonResponse response = new CommonResponse(200, "success");
//
//        when(userProfileService.updateNickName(TEST_ID, unicodeNickName))
//                .thenReturn(ResponseEntity.ok(response));
//
//        // When & Then
//        mockMvc.perform(
//                        put("/api/profile/nickname")
//                                .with(authenticatedUser())
//                                .contentType(MediaType.APPLICATION_JSON)
//                                .content(objectMapper.writeValueAsString(request))
//                                .accept(MediaType.APPLICATION_JSON))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.code").value(200))
//                .andExpect(jsonPath("$.message").value("success"));
//
//        verify(userProfileService).updateNickName(TEST_ID, unicodeNickName);
//    }
//
//    @Test
//    void updateNickName_ExactlyMaxLength() throws Exception {
//        // Given - Create a nickname exactly 50 characters long
//        String maxLengthNickName = "A".repeat(50);
//        UpdateNickNameRequest request = new UpdateNickNameRequest(maxLengthNickName);
//        CommonResponse response = new CommonResponse(200, "success");
//
//        when(userProfileService.updateNickName(TEST_ID, maxLengthNickName))
//                .thenReturn(ResponseEntity.ok(response));
//
//        // When & Then
//        mockMvc.perform(
//                        put("/api/profile/nickname")
//                                .with(authenticatedUser())
//                                .contentType(MediaType.APPLICATION_JSON)
//                                .content(objectMapper.writeValueAsString(request))
//                                .accept(MediaType.APPLICATION_JSON))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.code").value(200))
//                .andExpect(jsonPath("$.message").value("success"));
//
//        verify(userProfileService).updateNickName(TEST_ID, maxLengthNickName);
//    }
//
//    @Test
//    void updateNickName_MinLength() throws Exception {
//        // Given
//        String minLengthNickName = "A";
//        UpdateNickNameRequest request = new UpdateNickNameRequest(minLengthNickName);
//        CommonResponse response = new CommonResponse(200, "success");
//
//        when(userProfileService.updateNickName(TEST_ID, minLengthNickName))
//                .thenReturn(ResponseEntity.ok(response));
//
//        // When & Then
//        mockMvc.perform(
//                        put("/api/profile/nickname")
//                                .with(authenticatedUser())
//                                .contentType(MediaType.APPLICATION_JSON)
//                                .content(objectMapper.writeValueAsString(request))
//                                .accept(MediaType.APPLICATION_JSON))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.code").value(200))
//                .andExpect(jsonPath("$.message").value("success"));
//
//        verify(userProfileService).updateNickName(TEST_ID, minLengthNickName);
//    }
// }
