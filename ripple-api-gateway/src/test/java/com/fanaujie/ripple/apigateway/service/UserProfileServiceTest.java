package com.fanaujie.ripple.apigateway.service;
//
// import com.fanaujie.ripple.apigateway.dto.CommonResponse;
// import com.fanaujie.ripple.apigateway.dto.UserProfileData;
// import com.fanaujie.ripple.apigateway.dto.UserProfileResponse;
// import java.util.concurrent.ExecutorService;
//
// import com.fanaujie.ripple.protobuf.msgpublisher.MessageDispatcherGrpc;
// import com.fanaujie.ripple.storage.exception.NotFoundUserProfileException;
// import com.fanaujie.ripple.storage.model.UserProfile;
// import com.fanaujie.ripple.storage.repository.UserRepository;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.boot.test.context.SpringBootTest;
// import org.springframework.http.HttpStatus;
// import org.springframework.http.ResponseEntity;
// import org.springframework.test.context.bean.override.mockito.MockitoBean;
//
// import static org.junit.jupiter.api.Assertions.*;
// import static org.mockito.Mockito.*;
//
// @SpringBootTest(classes = {UserProfileService.class})
// class UserProfileServiceTest {
//
//    @MockitoBean private ExecutorService executorService;
//
//    @MockitoBean
//    private MessageDispatcherGrpc.MessageDispatcherBlockingStub messageDispatcherClient;
//
//    @MockitoBean private UserRepository userStorage;
//
//    @Autowired private UserProfileService userProfileService;
//
//    private static final long USER_ID = 1L;
//    private static final String NICK_NAME = "testUser";
//    private static final String NEW_NICK_NAME = "newTestUser";
//    private static final String AVATAR = "avatar.jpg";
//
//    @BeforeEach
//    void setUp() {}
//
//    @Test
//    void getUserProfile_Success() throws NotFoundUserProfileException {
//        // Given
//        UserProfile mockProfile = new UserProfile();
//        mockProfile.setUserId(USER_ID);
//        mockProfile.setNickName(NICK_NAME);
//        mockProfile.setAvatar(AVATAR);
//
//        when(userStorage.getUserProfile(USER_ID)).thenReturn(mockProfile);
//
//        // When
//        ResponseEntity<UserProfileResponse> response = userProfileService.getUserProfile(USER_ID);
//
//        // Then
//        assertNotNull(response);
//        assertEquals(HttpStatus.OK, response.getStatusCode());
//        assertNotNull(response.getBody());
//        assertEquals(200, response.getBody().getCode());
//        assertEquals("success", response.getBody().getMessage());
//
//        UserProfileData data = response.getBody().getData();
//        assertNotNull(data);
//        assertEquals(String.valueOf(USER_ID), data.getUserId());
//        assertEquals(NICK_NAME, data.getNickName());
//        assertEquals(AVATAR, data.getAvatar());
//
//        verify(userStorage).getUserProfile(USER_ID);
//    }
//
//    @Test
//    void getUserProfile_UserNotFound() throws NotFoundUserProfileException {
//        // Given
//        when(userStorage.getUserProfile(USER_ID))
//                .thenThrow(new NotFoundUserProfileException("User profile not found"));
//
//        // When
//        ResponseEntity<UserProfileResponse> response = userProfileService.getUserProfile(USER_ID);
//
//        // Then
//        assertNotNull(response);
//        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
//        assertNotNull(response.getBody());
//        assertEquals(404, response.getBody().getCode());
//        assertEquals("User profile not found", response.getBody().getMessage());
//        assertNull(response.getBody().getData());
//
//        verify(userStorage).getUserProfile(USER_ID);
//    }
//
//    @Test
//    void updateNickName_Success() throws NotFoundUserProfileException {
//        // Given
//        doNothing().when(userStorage).updateNickNameByUserId(USER_ID, NEW_NICK_NAME);
//
//        // When
//        ResponseEntity<CommonResponse> response =
//                userProfileService.updateNickName(USER_ID, NEW_NICK_NAME);
//
//        // Then
//        assertNotNull(response);
//        assertEquals(HttpStatus.OK, response.getStatusCode());
//        assertNotNull(response.getBody());
//        assertEquals(200, response.getBody().getCode());
//        assertEquals("success", response.getBody().getMessage());
//
//        verify(userStorage).updateNickNameByUserId(USER_ID, NEW_NICK_NAME);
//    }
//
//    @Test
//    void updateNickName_UserNotFound() throws NotFoundUserProfileException {
//        // Given
//        doThrow(new NotFoundUserProfileException("User profile not found"))
//                .when(userStorage)
//                .updateNickNameByUserId(USER_ID, NEW_NICK_NAME);
//
//        // When
//        ResponseEntity<CommonResponse> response =
//                userProfileService.updateNickName(USER_ID, NEW_NICK_NAME);
//
//        // Then
//        assertNotNull(response);
//        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
//        assertNotNull(response.getBody());
//        assertEquals(404, response.getBody().getCode());
//        assertEquals("User profile not found", response.getBody().getMessage());
//
//        verify(userStorage).updateNickNameByUserId(USER_ID, NEW_NICK_NAME);
//    }
//
//    @Test
//    void deleteAvatar_Success() throws NotFoundUserProfileException {
//        // Given
//        doNothing().when(userStorage).updateAvatarByUserId(USER_ID, null);
//
//        // When
//        ResponseEntity<CommonResponse> response = userProfileService.deleteAvatar(USER_ID);
//
//        // Then
//        assertNotNull(response);
//        assertEquals(HttpStatus.OK, response.getStatusCode());
//        assertNotNull(response.getBody());
//        assertEquals(200, response.getBody().getCode());
//        assertEquals("success", response.getBody().getMessage());
//
//        verify(userStorage).updateAvatarByUserId(USER_ID, null);
//    }
//
//    @Test
//    void deleteAvatar_UserNotFound() throws NotFoundUserProfileException {
//        // Given
//        doThrow(new NotFoundUserProfileException("User profile not found"))
//                .when(userStorage)
//                .updateAvatarByUserId(USER_ID, null);
//
//        // When
//        ResponseEntity<CommonResponse> response = userProfileService.deleteAvatar(USER_ID);
//
//        // Then
//        assertNotNull(response);
//        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
//        assertNotNull(response.getBody());
//        assertEquals(404, response.getBody().getCode());
//        assertEquals("User profile not found", response.getBody().getMessage());
//
//        verify(userStorage).updateAvatarByUserId(USER_ID, null);
//    }
// }
