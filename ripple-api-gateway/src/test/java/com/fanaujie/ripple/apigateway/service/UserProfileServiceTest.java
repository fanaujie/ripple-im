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

    private static final long TEST_ID = 1L;
    private static final String TEST_NICKNAME = "Test User";
    private static final String TEST_PORTRAIT = "avatar.jpg";

    @BeforeEach
    void setUp() {}

    @Test
    void getUserProfile_Success() {
        // Given
        UserProfile mockProfile = new UserProfile();
        mockProfile.setUserId(TEST_ID);
        mockProfile.setNickName(TEST_NICKNAME);
        mockProfile.setAvatar(TEST_PORTRAIT);

        when(userProfileMapper.findById(TEST_ID)).thenReturn(mockProfile);

        // When
        ResponseEntity<UserProfileResponse> response = userProfileService.getUserProfile(TEST_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertEquals("success", response.getBody().getMessage());

        UserProfileData data = response.getBody().getData();
        assertNotNull(data);
        assertEquals(TEST_ID, data.getUserId());
        assertEquals(TEST_NICKNAME, data.getNickName());
        assertEquals(TEST_PORTRAIT, data.getAvatar());

        verify(userProfileMapper).findById(TEST_ID);
    }

    @Test
    void getUserProfile_UserNotFound() {
        // Given
        when(userProfileMapper.findById(TEST_ID)).thenReturn(null);

        // When
        ResponseEntity<UserProfileResponse> response = userProfileService.getUserProfile(TEST_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(401, response.getBody().getCode());
        assertEquals("User profile not found", response.getBody().getMessage());
        assertNull(response.getBody().getData());

        verify(userProfileMapper).findById(TEST_ID);
    }

    @Test
    void updateNickName_Success() {
        // Given
        String newNickName = "New Nickname";
        when(userProfileMapper.countById(TEST_ID)).thenReturn(1);

        // When
        ResponseEntity<CommonResponse> response =
                userProfileService.updateNickName(TEST_ID, newNickName);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertEquals("success", response.getBody().getMessage());

        verify(userProfileMapper).countById(TEST_ID);
        verify(userProfileMapper).updateNickName(TEST_ID, newNickName);
    }

    @Test
    void updateNickName_UserNotFound() {
        // Given
        String newNickName = "New Nickname";
        when(userProfileMapper.countById(TEST_ID)).thenReturn(0);

        // When
        ResponseEntity<CommonResponse> response =
                userProfileService.updateNickName(TEST_ID, newNickName);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(401, response.getBody().getCode());
        assertEquals("User profile not found", response.getBody().getMessage());

        verify(userProfileMapper).countById(TEST_ID);
        verify(userProfileMapper, never()).updateNickName(anyLong(), anyString());
    }

    @Test
    void updateNickName_WithNullNickName() {
        // Given
        when(userProfileMapper.countById(TEST_ID)).thenReturn(1);

        // When
        ResponseEntity<CommonResponse> response = userProfileService.updateNickName(TEST_ID, null);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertEquals("success", response.getBody().getMessage());

        verify(userProfileMapper).countById(TEST_ID);
        verify(userProfileMapper).updateNickName(TEST_ID, null);
    }

    @Test
    void updateNickName_WithEmptyNickName() {
        // Given
        String emptyNickName = "";
        when(userProfileMapper.countById(TEST_ID)).thenReturn(1);

        // When
        ResponseEntity<CommonResponse> response =
                userProfileService.updateNickName(TEST_ID, emptyNickName);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertEquals("success", response.getBody().getMessage());

        verify(userProfileMapper).countById(TEST_ID);
        verify(userProfileMapper).updateNickName(TEST_ID, emptyNickName);
    }

    @Test
    void deleteUserPortrait_Success() {
        // Given
        when(userProfileMapper.countById(TEST_ID)).thenReturn(1);

        // When
        ResponseEntity<CommonResponse> response = userProfileService.deleteAvatar(TEST_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertEquals("success", response.getBody().getMessage());

        verify(userProfileMapper).countById(TEST_ID);
        verify(userProfileMapper).updateAvatar(TEST_ID, null);
    }

    @Test
    void deleteUserPortrait_UserNotFound() {
        // Given
        when(userProfileMapper.countById(TEST_ID)).thenReturn(0);

        // When
        ResponseEntity<CommonResponse> response = userProfileService.deleteAvatar(TEST_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(401, response.getBody().getCode());
        assertEquals("User profile not found", response.getBody().getMessage());

        verify(userProfileMapper).countById(TEST_ID);
        verify(userProfileMapper, never()).updateAvatar(anyLong(), any());
    }

    @Test
    void userProfileExists_ReturnsTrue_WhenUserExists() {
        // Given
        when(userProfileMapper.countById(TEST_ID)).thenReturn(1);

        // When
        ResponseEntity<CommonResponse> response =
                userProfileService.updateNickName(TEST_ID, "test");

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userProfileMapper).countById(TEST_ID);
    }

    @Test
    void userProfileExists_ReturnsFalse_WhenUserDoesNotExist() {
        // Given
        when(userProfileMapper.countById(TEST_ID)).thenReturn(0);

        // When
        ResponseEntity<CommonResponse> response =
                userProfileService.updateNickName(TEST_ID, "test");

        // Then
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(userProfileMapper).countById(TEST_ID);
    }

    @Test
    void updateNickName_WithLongNickName() {
        // Given
        String longNickName = "This is a very long nickname that might exceed normal limits";
        when(userProfileMapper.countById(TEST_ID)).thenReturn(1);

        // When
        ResponseEntity<CommonResponse> response =
                userProfileService.updateNickName(TEST_ID, longNickName);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertEquals("success", response.getBody().getMessage());

        verify(userProfileMapper).countById(TEST_ID);
        verify(userProfileMapper).updateNickName(TEST_ID, longNickName);
    }

    @Test
    void getUserProfile_WithNullPortrait() {
        // Given
        UserProfile mockProfile = new UserProfile();
        mockProfile.setUserId(TEST_ID);
        mockProfile.setNickName(TEST_NICKNAME);
        mockProfile.setAvatar(null);

        when(userProfileMapper.findById(TEST_ID)).thenReturn(mockProfile);

        // When
        ResponseEntity<UserProfileResponse> response = userProfileService.getUserProfile(TEST_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertEquals("success", response.getBody().getMessage());

        UserProfileData data = response.getBody().getData();
        assertNotNull(data);
        assertEquals(TEST_ID, data.getUserId());
        assertEquals(TEST_NICKNAME, data.getNickName());
        assertNull(data.getAvatar());

        verify(userProfileMapper).findById(TEST_ID);
    }
}
