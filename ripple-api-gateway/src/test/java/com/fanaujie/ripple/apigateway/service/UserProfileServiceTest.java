package com.fanaujie.ripple.apigateway.service;

import com.fanaujie.ripple.apigateway.dto.CommonResponse;
import com.fanaujie.ripple.apigateway.dto.UserProfileData;
import com.fanaujie.ripple.apigateway.dto.UserProfileResponse;
import com.fanaujie.ripple.database.mapper.UserProfileMapper;
import com.fanaujie.ripple.database.model.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock private UserProfileMapper userProfileMapper;

    @InjectMocks private UserProfileService userProfileService;

    private static final String TEST_ACCOUNT = "testuser";
    private static final String TEST_NICKNAME = "Test User";
    private static final String TEST_PORTRAIT = "avatar.jpg";

    @BeforeEach
    void setUp() {}

    @Test
    void getUserProfile_Success() {
        // Given
        UserProfile mockProfile = new UserProfile();
        mockProfile.setAccount(TEST_ACCOUNT);
        mockProfile.setNickName(TEST_NICKNAME);
        mockProfile.setUserPortrait(TEST_PORTRAIT);

        when(userProfileMapper.findByAccount(TEST_ACCOUNT)).thenReturn(mockProfile);

        // When
        ResponseEntity<UserProfileResponse> response =
                userProfileService.getUserProfile(TEST_ACCOUNT);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertEquals("success", response.getBody().getMessage());

        UserProfileData data = response.getBody().getData();
        assertNotNull(data);
        assertEquals(TEST_ACCOUNT, data.getAccount());
        assertEquals(TEST_NICKNAME, data.getNickName());
        assertEquals(TEST_PORTRAIT, data.getUserPortrait());

        verify(userProfileMapper).findByAccount(TEST_ACCOUNT);
    }

    @Test
    void getUserProfile_UserNotFound() {
        // Given
        when(userProfileMapper.findByAccount(TEST_ACCOUNT)).thenReturn(null);

        // When
        ResponseEntity<UserProfileResponse> response =
                userProfileService.getUserProfile(TEST_ACCOUNT);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(401, response.getBody().getCode());
        assertEquals("User profile not found", response.getBody().getMessage());
        assertNull(response.getBody().getData());

        verify(userProfileMapper).findByAccount(TEST_ACCOUNT);
    }

    @Test
    void updateNickName_Success() {
        // Given
        String newNickName = "New Nickname";
        when(userProfileMapper.countByAccount(TEST_ACCOUNT)).thenReturn(1);

        // When
        ResponseEntity<CommonResponse> response =
                userProfileService.updateNickName(TEST_ACCOUNT, newNickName);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertEquals("success", response.getBody().getMessage());

        verify(userProfileMapper).countByAccount(TEST_ACCOUNT);
        verify(userProfileMapper).updateNickName(TEST_ACCOUNT, newNickName);
    }

    @Test
    void updateNickName_UserNotFound() {
        // Given
        String newNickName = "New Nickname";
        when(userProfileMapper.countByAccount(TEST_ACCOUNT)).thenReturn(0);

        // When
        ResponseEntity<CommonResponse> response =
                userProfileService.updateNickName(TEST_ACCOUNT, newNickName);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(401, response.getBody().getCode());
        assertEquals("User profile not found", response.getBody().getMessage());

        verify(userProfileMapper).countByAccount(TEST_ACCOUNT);
        verify(userProfileMapper, never()).updateNickName(anyString(), anyString());
    }

    @Test
    void updateNickName_WithNullNickName() {
        // Given
        when(userProfileMapper.countByAccount(TEST_ACCOUNT)).thenReturn(1);

        // When
        ResponseEntity<CommonResponse> response =
                userProfileService.updateNickName(TEST_ACCOUNT, null);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertEquals("success", response.getBody().getMessage());

        verify(userProfileMapper).countByAccount(TEST_ACCOUNT);
        verify(userProfileMapper).updateNickName(TEST_ACCOUNT, null);
    }

    @Test
    void updateNickName_WithEmptyNickName() {
        // Given
        String emptyNickName = "";
        when(userProfileMapper.countByAccount(TEST_ACCOUNT)).thenReturn(1);

        // When
        ResponseEntity<CommonResponse> response =
                userProfileService.updateNickName(TEST_ACCOUNT, emptyNickName);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertEquals("success", response.getBody().getMessage());

        verify(userProfileMapper).countByAccount(TEST_ACCOUNT);
        verify(userProfileMapper).updateNickName(TEST_ACCOUNT, emptyNickName);
    }

    @Test
    void deleteUserPortrait_Success() {
        // Given
        when(userProfileMapper.countByAccount(TEST_ACCOUNT)).thenReturn(1);

        // When
        ResponseEntity<CommonResponse> response =
                userProfileService.deleteUserPortrait(TEST_ACCOUNT);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertEquals("success", response.getBody().getMessage());

        verify(userProfileMapper).countByAccount(TEST_ACCOUNT);
        verify(userProfileMapper).updateUserPortrait(TEST_ACCOUNT, null);
    }

    @Test
    void deleteUserPortrait_UserNotFound() {
        // Given
        when(userProfileMapper.countByAccount(TEST_ACCOUNT)).thenReturn(0);

        // When
        ResponseEntity<CommonResponse> response =
                userProfileService.deleteUserPortrait(TEST_ACCOUNT);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(401, response.getBody().getCode());
        assertEquals("User profile not found", response.getBody().getMessage());

        verify(userProfileMapper).countByAccount(TEST_ACCOUNT);
        verify(userProfileMapper, never()).updateUserPortrait(anyString(), any());
    }

    @Test
    void userProfileExists_ReturnsTrue_WhenUserExists() {
        // Given
        when(userProfileMapper.countByAccount(TEST_ACCOUNT)).thenReturn(1);

        // When
        ResponseEntity<CommonResponse> response =
                userProfileService.updateNickName(TEST_ACCOUNT, "test");

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userProfileMapper).countByAccount(TEST_ACCOUNT);
    }

    @Test
    void userProfileExists_ReturnsFalse_WhenUserDoesNotExist() {
        // Given
        when(userProfileMapper.countByAccount(TEST_ACCOUNT)).thenReturn(0);

        // When
        ResponseEntity<CommonResponse> response =
                userProfileService.updateNickName(TEST_ACCOUNT, "test");

        // Then
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(userProfileMapper).countByAccount(TEST_ACCOUNT);
    }

    @Test
    void updateNickName_WithLongNickName() {
        // Given
        String longNickName = "This is a very long nickname that might exceed normal limits";
        when(userProfileMapper.countByAccount(TEST_ACCOUNT)).thenReturn(1);

        // When
        ResponseEntity<CommonResponse> response =
                userProfileService.updateNickName(TEST_ACCOUNT, longNickName);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertEquals("success", response.getBody().getMessage());

        verify(userProfileMapper).countByAccount(TEST_ACCOUNT);
        verify(userProfileMapper).updateNickName(TEST_ACCOUNT, longNickName);
    }

    @Test
    void getUserProfile_WithNullPortrait() {
        // Given
        UserProfile mockProfile = new UserProfile();
        mockProfile.setAccount(TEST_ACCOUNT);
        mockProfile.setNickName(TEST_NICKNAME);
        mockProfile.setUserPortrait(null);

        when(userProfileMapper.findByAccount(TEST_ACCOUNT)).thenReturn(mockProfile);

        // When
        ResponseEntity<UserProfileResponse> response =
                userProfileService.getUserProfile(TEST_ACCOUNT);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertEquals("success", response.getBody().getMessage());

        UserProfileData data = response.getBody().getData();
        assertNotNull(data);
        assertEquals(TEST_ACCOUNT, data.getAccount());
        assertEquals(TEST_NICKNAME, data.getNickName());
        assertNull(data.getUserPortrait());

        verify(userProfileMapper).findByAccount(TEST_ACCOUNT);
    }
}
