package com.fanaujie.ripple.apigateway.service;

import com.fanaujie.ripple.apigateway.dto.CommonResponse;
import com.fanaujie.ripple.apigateway.dto.RelationResponse;
import com.fanaujie.ripple.apigateway.dto.UserRelationsData;
import com.fanaujie.ripple.apigateway.dto.UserRelationsResponse;
import com.fanaujie.ripple.database.exception.NotFoundRelationException;
import com.fanaujie.ripple.database.exception.NotFoundUserProfileException;
import com.fanaujie.ripple.database.model.RelationWithProfile;
import com.fanaujie.ripple.database.model.UserProfile;
import com.fanaujie.ripple.database.model.UserRelation;
import com.fanaujie.ripple.database.service.IRelationStorage;
import com.fanaujie.ripple.database.service.IUserProfileStorage;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(classes = {RelationService.class})
class RelationServiceTest {

    @MockitoBean private IRelationStorage relationStorage;
    @MockitoBean private IUserProfileStorage userProfileStorage;

    @Autowired private RelationService relationService;

    private static final long CURRENT_USER_ID = 1L;
    private static final long TARGET_USER_ID = 2L;
    private static final String DISPLAY_NAME = "Friend Display Name";
    private static final String TARGET_USER_NICKNAME = "TargetUserNick";

    @BeforeEach
    void setUp() {}

    @Test
    void addFriend_Success() throws NotFoundRelationException, NotFoundUserProfileException {
        // Given
        UserProfile mockUserProfile = new UserProfile();
        mockUserProfile.setNickName(TARGET_USER_NICKNAME);
        when(userProfileStorage.getUserProfile(TARGET_USER_ID)).thenReturn(mockUserProfile);
        doThrow(new NotFoundRelationException("No existing relation"))
                .when(relationStorage)
                .getRelationStatus(CURRENT_USER_ID, TARGET_USER_ID);

        // When
        ResponseEntity<RelationResponse> response =
                relationService.addFriend(CURRENT_USER_ID, TARGET_USER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertEquals("success", response.getBody().getMessage());
        assertNotNull(response.getBody().getData());
        assertEquals(
                (int) UserRelation.FRIEND_FLAG, response.getBody().getData().getRelationFlags());
        assertEquals(String.valueOf(CURRENT_USER_ID), response.getBody().getData().getSourceUserId());
        assertEquals(String.valueOf(TARGET_USER_ID), response.getBody().getData().getTargetUserId());

        verify(userProfileStorage).getUserProfile(TARGET_USER_ID);
        verify(relationStorage).getRelationStatus(CURRENT_USER_ID, TARGET_USER_ID);
        verify(relationStorage)
                .insertRelationStatus(
                        CURRENT_USER_ID,
                        TARGET_USER_ID,
                        TARGET_USER_NICKNAME,
                        UserRelation.FRIEND_FLAG);
    }

    @Test
    void addFriend_SameUser_ReturnsBadRequest() {
        // When
        ResponseEntity<RelationResponse> response =
                relationService.addFriend(CURRENT_USER_ID, CURRENT_USER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertEquals("Cannot add yourself as friend", response.getBody().getMessage());
        assertNull(response.getBody().getData());

        verifyNoInteractions(relationStorage, userProfileStorage);
    }

    @Test
    void addFriend_TargetUserNotExists_ReturnsBadRequest()
            throws NotFoundRelationException, NotFoundUserProfileException {
        // Given
        doThrow(new NotFoundUserProfileException("User not found"))
                .when(userProfileStorage)
                .getUserProfile(TARGET_USER_ID);

        // When
        ResponseEntity<RelationResponse> response =
                relationService.addFriend(CURRENT_USER_ID, TARGET_USER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertEquals("Target user does not exist", response.getBody().getMessage());
        assertNull(response.getBody().getData());

        verify(userProfileStorage).getUserProfile(TARGET_USER_ID);
        verify(relationStorage, never()).getRelationStatus(anyLong(), anyLong());
        verify(relationStorage, never())
                .insertRelationStatus(anyLong(), anyLong(), anyString(), anyByte());
        verify(relationStorage, never()).updateRelationStatus(anyLong(), anyLong(), anyByte());
    }

    @Test
    void addFriend_AlreadyFriend_ReturnsBadRequest()
            throws NotFoundRelationException, NotFoundUserProfileException {
        // Given
        UserProfile mockUserProfile = new UserProfile();
        mockUserProfile.setNickName(TARGET_USER_NICKNAME);
        when(userProfileStorage.getUserProfile(TARGET_USER_ID)).thenReturn(mockUserProfile);
        when(relationStorage.getRelationStatus(CURRENT_USER_ID, TARGET_USER_ID))
                .thenReturn(UserRelation.FRIEND_FLAG);

        // When
        ResponseEntity<RelationResponse> response =
                relationService.addFriend(CURRENT_USER_ID, TARGET_USER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertEquals("Target user is already your friend", response.getBody().getMessage());
        assertNull(response.getBody().getData());

        verify(userProfileStorage).getUserProfile(TARGET_USER_ID);
        verify(relationStorage).getRelationStatus(CURRENT_USER_ID, TARGET_USER_ID);
        verify(relationStorage, never())
                .insertRelationStatus(anyLong(), anyLong(), anyString(), anyByte());
        verify(relationStorage, never()).updateRelationStatus(anyLong(), anyLong(), anyByte());
    }

    @Test
    void removeFriend_Success() throws NotFoundRelationException {
        // Given
        when(userProfileStorage.userProfileExists(TARGET_USER_ID)).thenReturn(true);
        when(relationStorage.getRelationStatus(CURRENT_USER_ID, TARGET_USER_ID))
                .thenReturn(UserRelation.FRIEND_FLAG);

        // When
        ResponseEntity<RelationResponse> response =
                relationService.removeFriend(CURRENT_USER_ID, TARGET_USER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertEquals("success", response.getBody().getMessage());
        assertNotNull(response.getBody().getData());
        assertEquals(0, response.getBody().getData().getRelationFlags());
        assertEquals(String.valueOf(CURRENT_USER_ID), response.getBody().getData().getSourceUserId());
        assertEquals(String.valueOf(TARGET_USER_ID), response.getBody().getData().getTargetUserId());

        verify(userProfileStorage).userProfileExists(TARGET_USER_ID);
        verify(relationStorage).getRelationStatus(CURRENT_USER_ID, TARGET_USER_ID);
        verify(relationStorage).updateRelationStatus(CURRENT_USER_ID, TARGET_USER_ID, (byte) 0);
    }

    @Test
    void removeFriend_SameUser_ReturnsBadRequest() {
        // When
        ResponseEntity<RelationResponse> response =
                relationService.removeFriend(CURRENT_USER_ID, CURRENT_USER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertEquals("Cannot remove yourself as friend", response.getBody().getMessage());
        assertNull(response.getBody().getData());

        verifyNoInteractions(relationStorage, userProfileStorage);
    }

    @Test
    void removeFriend_TargetUserNotExists_ReturnsBadRequest() throws NotFoundRelationException {
        // Given
        when(userProfileStorage.userProfileExists(TARGET_USER_ID)).thenReturn(false);

        // When
        ResponseEntity<RelationResponse> response =
                relationService.removeFriend(CURRENT_USER_ID, TARGET_USER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertEquals("Target user does not exist", response.getBody().getMessage());
        assertNull(response.getBody().getData());

        verify(userProfileStorage).userProfileExists(TARGET_USER_ID);
        verify(relationStorage, never()).getRelationStatus(anyLong(), anyLong());
        verify(relationStorage, never())
                .insertRelationStatus(anyLong(), anyLong(), anyString(), anyByte());
        verify(relationStorage, never()).updateRelationStatus(anyLong(), anyLong(), anyByte());
    }

    @Test
    void removeFriend_NotFriend_ReturnsBadRequest() throws NotFoundRelationException {
        // Given
        when(userProfileStorage.userProfileExists(TARGET_USER_ID)).thenReturn(true);
        when(relationStorage.getRelationStatus(CURRENT_USER_ID, TARGET_USER_ID)).thenReturn(0);

        // When
        ResponseEntity<RelationResponse> response =
                relationService.removeFriend(CURRENT_USER_ID, TARGET_USER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertEquals("Target user is not your friend", response.getBody().getMessage());
        assertNull(response.getBody().getData());

        verify(userProfileStorage).userProfileExists(TARGET_USER_ID);
        verify(relationStorage).getRelationStatus(CURRENT_USER_ID, TARGET_USER_ID);
        verify(relationStorage, never())
                .insertRelationStatus(anyLong(), anyLong(), anyString(), anyByte());
        verify(relationStorage, never()).updateRelationStatus(anyLong(), anyLong(), anyByte());
    }

    @Test
    void removeFriend_NoRelationExists_ReturnsBadRequest() throws NotFoundRelationException {
        // Given
        when(userProfileStorage.userProfileExists(TARGET_USER_ID)).thenReturn(true);
        doThrow(new NotFoundRelationException("No existing relation"))
                .when(relationStorage)
                .getRelationStatus(CURRENT_USER_ID, TARGET_USER_ID);

        // When
        ResponseEntity<RelationResponse> response =
                relationService.removeFriend(CURRENT_USER_ID, TARGET_USER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertEquals("No existing relation with target user", response.getBody().getMessage());
        assertNull(response.getBody().getData());

        verify(userProfileStorage).userProfileExists(TARGET_USER_ID);
        verify(relationStorage).getRelationStatus(CURRENT_USER_ID, TARGET_USER_ID);
        verify(relationStorage, never())
                .insertRelationStatus(anyLong(), anyLong(), anyString(), anyByte());
        verify(relationStorage, never()).updateRelationStatus(anyLong(), anyLong(), anyByte());
    }

    @Test
    void getFriendsWithBlockedLists_Success() {
        // Given
        List<RelationWithProfile> mockRelations = new ArrayList<>();

        RelationWithProfile friend = mock(RelationWithProfile.class);
        when(friend.getRelationFlags()).thenReturn(UserRelation.FRIEND_FLAG);
        when(friend.getTargetUserId()).thenReturn(1L);
        when(friend.getTargetNickName()).thenReturn("Friend");
        when(friend.getTargetAvatar()).thenReturn("avatar1.jpg");
        when(friend.getTargetUserDisplayName()).thenReturn(DISPLAY_NAME);

        RelationWithProfile blockedUser = mock(RelationWithProfile.class);
        when(blockedUser.getRelationFlags()).thenReturn(UserRelation.BLOCKED_FLAG);
        when(blockedUser.getTargetUserId()).thenReturn(2L);
        when(blockedUser.getTargetNickName()).thenReturn("Blocked User");
        when(blockedUser.getTargetAvatar()).thenReturn("avatar2.jpg");
        when(blockedUser.getTargetUserDisplayName()).thenReturn(DISPLAY_NAME);

        RelationWithProfile hiddenUser = mock(RelationWithProfile.class);
        when(hiddenUser.getRelationFlags())
                .thenReturn(UserRelation.BLOCKED_FLAG | UserRelation.HIDDEN_FLAG);
        when(hiddenUser.getTargetUserId()).thenReturn(3L);
        when(hiddenUser.getTargetNickName()).thenReturn("Hidden User");
        when(hiddenUser.getTargetAvatar()).thenReturn("avatar3.jpg");
        when(hiddenUser.getTargetUserDisplayName()).thenReturn(DISPLAY_NAME);

        mockRelations.add(friend);
        mockRelations.add(blockedUser);
        mockRelations.add(hiddenUser);

        when(relationStorage.getFriendsWithBlockedUsers(CURRENT_USER_ID)).thenReturn(mockRelations);

        // When
        ResponseEntity<UserRelationsResponse> response =
                relationService.getFriendsWithBlockedLists(CURRENT_USER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertEquals("success", response.getBody().getMessage());

        UserRelationsData data = response.getBody().getData();
        assertNotNull(data);
        assertEquals(1, data.getFriends().size());
        assertEquals(1, data.getBlockedUsers().size());

        verify(relationStorage).getFriendsWithBlockedUsers(CURRENT_USER_ID);
    }

    @Test
    void updateFriendDisplayName_Success() throws NotFoundRelationException {
        // Given
        when(userProfileStorage.userProfileExists(TARGET_USER_ID)).thenReturn(true);
        when(relationStorage.getRelationStatus(CURRENT_USER_ID, TARGET_USER_ID))
                .thenReturn(UserRelation.FRIEND_FLAG);

        // When
        ResponseEntity<CommonResponse> response =
                relationService.updateFriendDisplayName(
                        CURRENT_USER_ID, TARGET_USER_ID, DISPLAY_NAME);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertEquals("success", response.getBody().getMessage());

        verify(userProfileStorage).userProfileExists(TARGET_USER_ID);
        verify(relationStorage).getRelationStatus(CURRENT_USER_ID, TARGET_USER_ID);
        verify(relationStorage)
                .updateFriendDisplayName(CURRENT_USER_ID, TARGET_USER_ID, DISPLAY_NAME);
    }

    @Test
    void updateFriendDisplayName_SameUser_ReturnsBadRequest() {
        // When
        ResponseEntity<CommonResponse> response =
                relationService.updateFriendDisplayName(
                        CURRENT_USER_ID, CURRENT_USER_ID, DISPLAY_NAME);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertEquals("Cannot update display name for yourself", response.getBody().getMessage());

        verifyNoInteractions(relationStorage, userProfileStorage);
    }

    @Test
    void updateFriendDisplayName_TargetUserNotExists_ReturnsBadRequest()
            throws NotFoundRelationException {
        // Given
        when(userProfileStorage.userProfileExists(TARGET_USER_ID)).thenReturn(false);

        // When
        ResponseEntity<CommonResponse> response =
                relationService.updateFriendDisplayName(
                        CURRENT_USER_ID, TARGET_USER_ID, DISPLAY_NAME);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertEquals("Target user does not exist", response.getBody().getMessage());

        verify(userProfileStorage).userProfileExists(TARGET_USER_ID);
        verify(relationStorage, never()).getRelationStatus(anyLong(), anyLong());
        verify(relationStorage, never()).updateFriendDisplayName(anyLong(), anyLong(), anyString());
    }

    @Test
    void updateFriendDisplayName_NotFriend_ReturnsBadRequest() throws NotFoundRelationException {
        // Given
        when(userProfileStorage.userProfileExists(TARGET_USER_ID)).thenReturn(true);
        when(relationStorage.getRelationStatus(CURRENT_USER_ID, TARGET_USER_ID)).thenReturn(0);

        // When
        ResponseEntity<CommonResponse> response =
                relationService.updateFriendDisplayName(
                        CURRENT_USER_ID, TARGET_USER_ID, DISPLAY_NAME);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertEquals("You are not friends with the target user", response.getBody().getMessage());

        verify(userProfileStorage).userProfileExists(TARGET_USER_ID);
        verify(relationStorage).getRelationStatus(CURRENT_USER_ID, TARGET_USER_ID);
        verify(relationStorage, never()).updateFriendDisplayName(anyLong(), anyLong(), anyString());
    }

    @Test
    void updateFriendDisplayName_NoRelationExists_ReturnsBadRequest()
            throws NotFoundRelationException {
        // Given
        when(userProfileStorage.userProfileExists(TARGET_USER_ID)).thenReturn(true);
        doThrow(new NotFoundRelationException("No existing relation"))
                .when(relationStorage)
                .getRelationStatus(CURRENT_USER_ID, TARGET_USER_ID);

        // When
        ResponseEntity<CommonResponse> response =
                relationService.updateFriendDisplayName(
                        CURRENT_USER_ID, TARGET_USER_ID, DISPLAY_NAME);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertEquals("No existing relation with target user", response.getBody().getMessage());

        verify(userProfileStorage).userProfileExists(TARGET_USER_ID);
        verify(relationStorage).getRelationStatus(CURRENT_USER_ID, TARGET_USER_ID);
        verify(relationStorage, never()).updateFriendDisplayName(anyLong(), anyLong(), anyString());
    }

    @Test
    void updateBlockedStatus_BlockUser_Success() throws NotFoundRelationException {
        // Given
        when(userProfileStorage.userProfileExists(TARGET_USER_ID)).thenReturn(true);
        when(relationStorage.getRelationStatus(CURRENT_USER_ID, TARGET_USER_ID)).thenReturn(0);

        // When
        ResponseEntity<RelationResponse> response =
                relationService.updateBlockedStatus(CURRENT_USER_ID, TARGET_USER_ID, true);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertEquals("success", response.getBody().getMessage());
        assertNotNull(response.getBody().getData());
        assertEquals(
                (int) UserRelation.BLOCKED_FLAG, response.getBody().getData().getRelationFlags());
        assertEquals(String.valueOf(CURRENT_USER_ID), response.getBody().getData().getSourceUserId());
        assertEquals(String.valueOf(TARGET_USER_ID), response.getBody().getData().getTargetUserId());

        verify(userProfileStorage).userProfileExists(TARGET_USER_ID);
        verify(relationStorage).getRelationStatus(CURRENT_USER_ID, TARGET_USER_ID);
        verify(relationStorage)
                .updateRelationStatus(CURRENT_USER_ID, TARGET_USER_ID, UserRelation.BLOCKED_FLAG);
    }

    @Test
    void updateBlockedStatus_UnblockUser_Success() throws NotFoundRelationException {
        // Given
        when(userProfileStorage.userProfileExists(TARGET_USER_ID)).thenReturn(true);
        when(relationStorage.getRelationStatus(CURRENT_USER_ID, TARGET_USER_ID))
                .thenReturn(UserRelation.BLOCKED_FLAG);

        // When
        ResponseEntity<RelationResponse> response =
                relationService.updateBlockedStatus(CURRENT_USER_ID, TARGET_USER_ID, false);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertEquals("success", response.getBody().getMessage());
        assertNotNull(response.getBody().getData());
        assertEquals(0, response.getBody().getData().getRelationFlags());
        assertEquals(String.valueOf(CURRENT_USER_ID), response.getBody().getData().getSourceUserId());
        assertEquals(String.valueOf(TARGET_USER_ID), response.getBody().getData().getTargetUserId());

        verify(userProfileStorage).userProfileExists(TARGET_USER_ID);
        verify(relationStorage).getRelationStatus(CURRENT_USER_ID, TARGET_USER_ID);
        verify(relationStorage).updateRelationStatus(CURRENT_USER_ID, TARGET_USER_ID, (byte) 0);
    }

    @Test
    void updateBlockedStatus_SameUser_ReturnsBadRequest() {
        // When
        ResponseEntity<RelationResponse> response =
                relationService.updateBlockedStatus(CURRENT_USER_ID, CURRENT_USER_ID, true);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertEquals("Cannot block/unblock yourself", response.getBody().getMessage());
        assertNull(response.getBody().getData());

        verifyNoInteractions(relationStorage, userProfileStorage);
    }

    @Test
    void updateBlockedStatus_TargetUserNotExists_ReturnsBadRequest()
            throws NotFoundRelationException {
        // Given
        when(userProfileStorage.userProfileExists(TARGET_USER_ID)).thenReturn(false);

        // When
        ResponseEntity<RelationResponse> response =
                relationService.updateBlockedStatus(CURRENT_USER_ID, TARGET_USER_ID, true);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertEquals("Target user does not exist", response.getBody().getMessage());
        assertNull(response.getBody().getData());

        verify(userProfileStorage).userProfileExists(TARGET_USER_ID);
        verify(relationStorage, never()).getRelationStatus(anyLong(), anyLong());
        verify(relationStorage, never())
                .insertRelationStatus(anyLong(), anyLong(), anyString(), anyByte());
        verify(relationStorage, never()).updateRelationStatus(anyLong(), anyLong(), anyByte());
    }

    @Test
    void updateBlockedStatus_NoRelationExists_ReturnsBadRequest() throws NotFoundRelationException {
        // Given
        when(userProfileStorage.userProfileExists(TARGET_USER_ID)).thenReturn(true);
        doThrow(new NotFoundRelationException("No existing relation"))
                .when(relationStorage)
                .getRelationStatus(CURRENT_USER_ID, TARGET_USER_ID);

        // When
        ResponseEntity<RelationResponse> response =
                relationService.updateBlockedStatus(CURRENT_USER_ID, TARGET_USER_ID, true);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertEquals("No existing relation with target user", response.getBody().getMessage());
        assertNull(response.getBody().getData());

        verify(userProfileStorage).userProfileExists(TARGET_USER_ID);
        verify(relationStorage).getRelationStatus(CURRENT_USER_ID, TARGET_USER_ID);
        verify(relationStorage, never())
                .insertRelationStatus(anyLong(), anyLong(), anyString(), anyByte());
        verify(relationStorage, never()).updateRelationStatus(anyLong(), anyLong(), anyByte());
    }

    @Test
    void hideBlockedUser_Success() throws NotFoundRelationException {
        // Given
        when(userProfileStorage.userProfileExists(TARGET_USER_ID)).thenReturn(true);
        when(relationStorage.getRelationStatus(CURRENT_USER_ID, TARGET_USER_ID))
                .thenReturn(UserRelation.BLOCKED_FLAG);

        // When
        ResponseEntity<RelationResponse> response =
                relationService.hideBlockedUser(CURRENT_USER_ID, TARGET_USER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertEquals("success", response.getBody().getMessage());
        assertNotNull(response.getBody().getData());
        assertEquals(
                (int) (UserRelation.BLOCKED_FLAG | UserRelation.HIDDEN_FLAG),
                response.getBody().getData().getRelationFlags());
        assertEquals(String.valueOf(CURRENT_USER_ID), response.getBody().getData().getSourceUserId());
        assertEquals(String.valueOf(TARGET_USER_ID), response.getBody().getData().getTargetUserId());

        verify(userProfileStorage).userProfileExists(TARGET_USER_ID);
        verify(relationStorage).getRelationStatus(CURRENT_USER_ID, TARGET_USER_ID);
        verify(relationStorage)
                .updateRelationStatus(
                        CURRENT_USER_ID,
                        TARGET_USER_ID,
                        (byte) (UserRelation.BLOCKED_FLAG | UserRelation.HIDDEN_FLAG));
    }

    @Test
    void hideBlockedUser_SameUser_ReturnsBadRequest() {
        // When
        ResponseEntity<RelationResponse> response =
                relationService.hideBlockedUser(CURRENT_USER_ID, CURRENT_USER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertEquals("Cannot hide yourself", response.getBody().getMessage());
        assertNull(response.getBody().getData());

        verifyNoInteractions(relationStorage, userProfileStorage);
    }

    @Test
    void hideBlockedUser_TargetUserNotExists_ReturnsBadRequest() throws NotFoundRelationException {
        // Given
        when(userProfileStorage.userProfileExists(TARGET_USER_ID)).thenReturn(false);

        // When
        ResponseEntity<RelationResponse> response =
                relationService.hideBlockedUser(CURRENT_USER_ID, TARGET_USER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertEquals("Target user does not exist", response.getBody().getMessage());
        assertNull(response.getBody().getData());

        verify(userProfileStorage).userProfileExists(TARGET_USER_ID);
        verify(relationStorage, never()).getRelationStatus(anyLong(), anyLong());
        verify(relationStorage, never())
                .insertRelationStatus(anyLong(), anyLong(), anyString(), anyByte());
        verify(relationStorage, never()).updateRelationStatus(anyLong(), anyLong(), anyByte());
    }

    @Test
    void hideBlockedUser_UserNotBlocked_ReturnsBadRequest() throws NotFoundRelationException {
        // Given
        when(userProfileStorage.userProfileExists(TARGET_USER_ID)).thenReturn(true);
        when(relationStorage.getRelationStatus(CURRENT_USER_ID, TARGET_USER_ID))
                .thenReturn(UserRelation.FRIEND_FLAG);

        // When
        ResponseEntity<RelationResponse> response =
                relationService.hideBlockedUser(CURRENT_USER_ID, TARGET_USER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertEquals("Target user is not blocked", response.getBody().getMessage());
        assertNull(response.getBody().getData());

        verify(userProfileStorage).userProfileExists(TARGET_USER_ID);
        verify(relationStorage).getRelationStatus(CURRENT_USER_ID, TARGET_USER_ID);
        verify(relationStorage, never())
                .insertRelationStatus(anyLong(), anyLong(), anyString(), anyByte());
        verify(relationStorage, never()).updateRelationStatus(anyLong(), anyLong(), anyByte());
    }

    @Test
    void hideBlockedUser_NoRelationExists_ReturnsBadRequest() throws NotFoundRelationException {
        // Given
        when(userProfileStorage.userProfileExists(TARGET_USER_ID)).thenReturn(true);
        doThrow(new NotFoundRelationException("No existing relation"))
                .when(relationStorage)
                .getRelationStatus(CURRENT_USER_ID, TARGET_USER_ID);

        // When
        ResponseEntity<RelationResponse> response =
                relationService.hideBlockedUser(CURRENT_USER_ID, TARGET_USER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertEquals("No existing relation with target user", response.getBody().getMessage());
        assertNull(response.getBody().getData());

        verify(userProfileStorage).userProfileExists(TARGET_USER_ID);
        verify(relationStorage).getRelationStatus(CURRENT_USER_ID, TARGET_USER_ID);
        verify(relationStorage, never())
                .insertRelationStatus(anyLong(), anyLong(), anyString(), anyByte());
        verify(relationStorage, never()).updateRelationStatus(anyLong(), anyLong(), anyByte());
    }
}
