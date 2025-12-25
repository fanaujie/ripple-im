package com.fanaujie.ripple.apigateway.service;

import com.fanaujie.ripple.apigateway.dto.*;
import com.fanaujie.ripple.communication.msgapi.MessageAPISender;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq;
import com.fanaujie.ripple.storage.exception.InvalidVersionException;
import com.fanaujie.ripple.storage.exception.NotFoundUserProfileException;
import com.fanaujie.ripple.storage.model.*;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = {RelationService.class})
class RelationServiceTest {

    @MockitoBean
    private RippleStorageFacade storageFacade;

    @MockitoBean
    private MessageAPISender messageAPISender;

    @Autowired
    private RelationService relationService;

    private static final long CURRENT_USER_ID = 1L;
    private static final long TARGET_USER_ID = 2L;
    private static final String REMARK_NAME = "Friend Display Name";
    private static final String TARGET_USER_NICKNAME = "TargetUserNick";
    private static final String TARGET_USER_AVATAR = "avatar.jpg";

    @BeforeEach
    void setUp() {
        reset(storageFacade, messageAPISender);
    }

    // ==================== addFriend Tests ====================

    @Test
    void addFriend_Success() throws Exception {
        // Given
        when(storageFacade.getRelationBetweenUser(CURRENT_USER_ID, TARGET_USER_ID)).thenReturn(null);
        UserProfile mockUserProfile = new UserProfile();
        mockUserProfile.setNickName(TARGET_USER_NICKNAME);
        mockUserProfile.setAvatar(TARGET_USER_AVATAR);
        when(storageFacade.getUserProfile(TARGET_USER_ID)).thenReturn(mockUserProfile);

        // When
        ResponseEntity<CommonResponse> response = relationService.addFriend(CURRENT_USER_ID, TARGET_USER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertEquals("success", response.getBody().getMessage());

        verify(storageFacade).getRelationBetweenUser(CURRENT_USER_ID, TARGET_USER_ID);
        verify(storageFacade).getUserProfile(TARGET_USER_ID);
        verify(messageAPISender).sendEvent(any(SendEventReq.class));
    }

    @Test
    void addFriend_SameUser_ReturnsBadRequest() {
        // When
        ResponseEntity<CommonResponse> response = relationService.addFriend(CURRENT_USER_ID, CURRENT_USER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertEquals("Cannot add yourself as friend", response.getBody().getMessage());

        verifyNoInteractions(storageFacade, messageAPISender);
    }

    @Test
    void addFriend_TargetUserNotExists_ReturnsBadRequest() throws Exception {
        // Given
        when(storageFacade.getRelationBetweenUser(CURRENT_USER_ID, TARGET_USER_ID)).thenReturn(null);
        when(storageFacade.getUserProfile(TARGET_USER_ID))
                .thenThrow(new NotFoundUserProfileException("User not found"));

        // When
        ResponseEntity<CommonResponse> response = relationService.addFriend(CURRENT_USER_ID, TARGET_USER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());

        verify(storageFacade).getRelationBetweenUser(CURRENT_USER_ID, TARGET_USER_ID);
        verify(storageFacade).getUserProfile(TARGET_USER_ID);
        verify(messageAPISender, never()).sendEvent(any());
    }

    @Test
    void addFriend_AlreadyFriend_ReturnsBadRequest() throws Exception {
        // Given
        Relation existingRelation = new Relation();
        existingRelation.setRelationFlags(RelationFlags.FRIEND.getValue());
        when(storageFacade.getRelationBetweenUser(CURRENT_USER_ID, TARGET_USER_ID)).thenReturn(existingRelation);

        // When
        ResponseEntity<CommonResponse> response = relationService.addFriend(CURRENT_USER_ID, TARGET_USER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertEquals("Target user is already your friend", response.getBody().getMessage());

        verify(storageFacade).getRelationBetweenUser(CURRENT_USER_ID, TARGET_USER_ID);
        verify(messageAPISender, never()).sendEvent(any());
    }

    // ==================== removeFriend Tests ====================

    @Test
    void removeFriend_Success() throws Exception {
        // Given
        Relation existingRelation = new Relation();
        existingRelation.setRelationFlags(RelationFlags.FRIEND.getValue());
        when(storageFacade.getRelationBetweenUser(CURRENT_USER_ID, TARGET_USER_ID)).thenReturn(existingRelation);

        // When
        ResponseEntity<CommonResponse> response = relationService.removeFriend(CURRENT_USER_ID, TARGET_USER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertEquals("success", response.getBody().getMessage());

        verify(storageFacade).getRelationBetweenUser(CURRENT_USER_ID, TARGET_USER_ID);
        verify(messageAPISender).sendEvent(any(SendEventReq.class));
    }

    @Test
    void removeFriend_SameUser_ReturnsBadRequest() {
        // When
        ResponseEntity<CommonResponse> response = relationService.removeFriend(CURRENT_USER_ID, CURRENT_USER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertEquals("Cannot remove yourself as friend", response.getBody().getMessage());

        verifyNoInteractions(storageFacade, messageAPISender);
    }

    @Test
    void removeFriend_NotRelated_ReturnsBadRequest() throws Exception {
        // Given
        when(storageFacade.getRelationBetweenUser(CURRENT_USER_ID, TARGET_USER_ID)).thenReturn(null);

        // When
        ResponseEntity<CommonResponse> response = relationService.removeFriend(CURRENT_USER_ID, TARGET_USER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertEquals("Target user is not your friend", response.getBody().getMessage());

        verify(storageFacade).getRelationBetweenUser(CURRENT_USER_ID, TARGET_USER_ID);
        verify(messageAPISender, never()).sendEvent(any());
    }

    // ==================== updateFriendRemarkName Tests ====================

    @Test
    void updateFriendRemarkName_Success() throws Exception {
        // Given
        Relation existingRelation = new Relation();
        existingRelation.setRelationFlags(RelationFlags.FRIEND.getValue());
        when(storageFacade.getRelationBetweenUser(CURRENT_USER_ID, TARGET_USER_ID)).thenReturn(existingRelation);

        // When
        ResponseEntity<CommonResponse> response =
                relationService.updateFriendRemarkName(CURRENT_USER_ID, TARGET_USER_ID, REMARK_NAME);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertEquals("success", response.getBody().getMessage());

        verify(storageFacade).getRelationBetweenUser(CURRENT_USER_ID, TARGET_USER_ID);
        verify(messageAPISender).sendEvent(any(SendEventReq.class));
    }

    @Test
    void updateFriendRemarkName_SameUser_ReturnsBadRequest() {
        // When
        ResponseEntity<CommonResponse> response =
                relationService.updateFriendRemarkName(CURRENT_USER_ID, CURRENT_USER_ID, REMARK_NAME);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertEquals("Cannot update display name for yourself", response.getBody().getMessage());

        verifyNoInteractions(storageFacade, messageAPISender);
    }

    @Test
    void updateFriendRemarkName_NotRelated_ReturnsBadRequest() throws Exception {
        // Given
        when(storageFacade.getRelationBetweenUser(CURRENT_USER_ID, TARGET_USER_ID)).thenReturn(null);

        // When
        ResponseEntity<CommonResponse> response =
                relationService.updateFriendRemarkName(CURRENT_USER_ID, TARGET_USER_ID, REMARK_NAME);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertEquals("Target user is not your friend", response.getBody().getMessage());

        verify(storageFacade).getRelationBetweenUser(CURRENT_USER_ID, TARGET_USER_ID);
        verify(messageAPISender, never()).sendEvent(any());
    }

    // ==================== addBlockedUser Tests ====================

    @Test
    void addBlockedUser_BlockFriend_Success() throws Exception {
        // Given - blocking an existing friend
        Relation existingRelation = new Relation();
        existingRelation.setRelationFlags(RelationFlags.FRIEND.getValue());
        existingRelation.setRelationNickName(TARGET_USER_NICKNAME);
        existingRelation.setRelationAvatar(TARGET_USER_AVATAR);
        when(storageFacade.getRelationBetweenUser(CURRENT_USER_ID, TARGET_USER_ID)).thenReturn(existingRelation);

        // When
        ResponseEntity<CommonResponse> response = relationService.addBlockedUser(CURRENT_USER_ID, TARGET_USER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertEquals("success", response.getBody().getMessage());

        verify(storageFacade).getRelationBetweenUser(CURRENT_USER_ID, TARGET_USER_ID);
        verify(messageAPISender).sendEvent(any(SendEventReq.class));
    }

    @Test
    void addBlockedUser_BlockStranger_Success() throws Exception {
        // Given - blocking a stranger
        when(storageFacade.getRelationBetweenUser(CURRENT_USER_ID, TARGET_USER_ID)).thenReturn(null);
        UserProfile strangerProfile = new UserProfile();
        strangerProfile.setNickName(TARGET_USER_NICKNAME);
        strangerProfile.setAvatar(TARGET_USER_AVATAR);
        when(storageFacade.getUserProfile(TARGET_USER_ID)).thenReturn(strangerProfile);

        // When
        ResponseEntity<CommonResponse> response = relationService.addBlockedUser(CURRENT_USER_ID, TARGET_USER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());

        verify(storageFacade).getRelationBetweenUser(CURRENT_USER_ID, TARGET_USER_ID);
        verify(storageFacade).getUserProfile(TARGET_USER_ID);
        verify(messageAPISender).sendEvent(any(SendEventReq.class));
    }

    @Test
    void addBlockedUser_SameUser_ReturnsBadRequest() {
        // When
        ResponseEntity<CommonResponse> response = relationService.addBlockedUser(CURRENT_USER_ID, CURRENT_USER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertEquals("Cannot block yourself", response.getBody().getMessage());

        verifyNoInteractions(storageFacade, messageAPISender);
    }

    @Test
    void addBlockedUser_AlreadyBlocked_ReturnsBadRequest() throws Exception {
        // Given
        Relation existingRelation = new Relation();
        existingRelation.setRelationFlags(RelationFlags.BLOCKED.getValue());
        when(storageFacade.getRelationBetweenUser(CURRENT_USER_ID, TARGET_USER_ID)).thenReturn(existingRelation);

        // When
        ResponseEntity<CommonResponse> response = relationService.addBlockedUser(CURRENT_USER_ID, TARGET_USER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertEquals("Target user is already blocked", response.getBody().getMessage());

        verify(storageFacade).getRelationBetweenUser(CURRENT_USER_ID, TARGET_USER_ID);
        verify(messageAPISender, never()).sendEvent(any());
    }

    @Test
    void addBlockedUser_StrangerNotFound_ReturnsBadRequest() throws Exception {
        // Given
        when(storageFacade.getRelationBetweenUser(CURRENT_USER_ID, TARGET_USER_ID)).thenReturn(null);
        when(storageFacade.getUserProfile(TARGET_USER_ID))
                .thenThrow(new NotFoundUserProfileException("User not found"));

        // When
        ResponseEntity<CommonResponse> response = relationService.addBlockedUser(CURRENT_USER_ID, TARGET_USER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertEquals("Target user profile not found", response.getBody().getMessage());

        verify(storageFacade).getRelationBetweenUser(CURRENT_USER_ID, TARGET_USER_ID);
        verify(storageFacade).getUserProfile(TARGET_USER_ID);
        verify(messageAPISender, never()).sendEvent(any());
    }

    // ==================== removeBlockedUser Tests ====================

    @Test
    void removeBlockedUser_Success() throws Exception {
        // Given
        Relation existingRelation = new Relation();
        existingRelation.setRelationFlags(RelationFlags.BLOCKED.getValue());
        when(storageFacade.getRelationBetweenUser(CURRENT_USER_ID, TARGET_USER_ID)).thenReturn(existingRelation);

        // When
        ResponseEntity<CommonResponse> response = relationService.removeBlockedUser(CURRENT_USER_ID, TARGET_USER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertEquals("success", response.getBody().getMessage());

        verify(storageFacade).getRelationBetweenUser(CURRENT_USER_ID, TARGET_USER_ID);
        verify(messageAPISender).sendEvent(any(SendEventReq.class));
    }

    @Test
    void removeBlockedUser_SameUser_ReturnsBadRequest() {
        // When
        ResponseEntity<CommonResponse> response = relationService.removeBlockedUser(CURRENT_USER_ID, CURRENT_USER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertEquals("Cannot unblock yourself", response.getBody().getMessage());

        verifyNoInteractions(storageFacade, messageAPISender);
    }

    @Test
    void removeBlockedUser_NotRelated_ReturnsBadRequest() throws Exception {
        // Given
        when(storageFacade.getRelationBetweenUser(CURRENT_USER_ID, TARGET_USER_ID)).thenReturn(null);

        // When
        ResponseEntity<CommonResponse> response = relationService.removeBlockedUser(CURRENT_USER_ID, TARGET_USER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertEquals("Target user is not related to you", response.getBody().getMessage());

        verify(storageFacade).getRelationBetweenUser(CURRENT_USER_ID, TARGET_USER_ID);
        verify(messageAPISender, never()).sendEvent(any());
    }

    @Test
    void removeBlockedUser_NotBlocked_ReturnsBadRequest() throws Exception {
        // Given
        Relation existingRelation = new Relation();
        existingRelation.setRelationFlags(RelationFlags.FRIEND.getValue()); // Not blocked
        when(storageFacade.getRelationBetweenUser(CURRENT_USER_ID, TARGET_USER_ID)).thenReturn(existingRelation);

        // When
        ResponseEntity<CommonResponse> response = relationService.removeBlockedUser(CURRENT_USER_ID, TARGET_USER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertEquals("Target user is not blocked", response.getBody().getMessage());

        verify(storageFacade).getRelationBetweenUser(CURRENT_USER_ID, TARGET_USER_ID);
        verify(messageAPISender, never()).sendEvent(any());
    }

    // ==================== hideBlockedUser Tests ====================

    @Test
    void hideBlockedUser_Success() throws Exception {
        // Given
        Relation existingRelation = new Relation();
        existingRelation.setRelationFlags(RelationFlags.BLOCKED.getValue());
        when(storageFacade.getRelationBetweenUser(CURRENT_USER_ID, TARGET_USER_ID)).thenReturn(existingRelation);

        // When
        ResponseEntity<CommonResponse> response = relationService.hideBlockedUser(CURRENT_USER_ID, TARGET_USER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertEquals("success", response.getBody().getMessage());

        verify(storageFacade).getRelationBetweenUser(CURRENT_USER_ID, TARGET_USER_ID);
        verify(messageAPISender).sendEvent(any(SendEventReq.class));
    }

    @Test
    void hideBlockedUser_SameUser_ReturnsBadRequest() {
        // When
        ResponseEntity<CommonResponse> response = relationService.hideBlockedUser(CURRENT_USER_ID, CURRENT_USER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertEquals("Cannot hide yourself", response.getBody().getMessage());

        verifyNoInteractions(storageFacade, messageAPISender);
    }

    @Test
    void hideBlockedUser_NotRelated_ReturnsBadRequest() throws Exception {
        // Given
        when(storageFacade.getRelationBetweenUser(CURRENT_USER_ID, TARGET_USER_ID)).thenReturn(null);

        // When
        ResponseEntity<CommonResponse> response = relationService.hideBlockedUser(CURRENT_USER_ID, TARGET_USER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertEquals("Target user is not related to you", response.getBody().getMessage());

        verify(storageFacade).getRelationBetweenUser(CURRENT_USER_ID, TARGET_USER_ID);
        verify(messageAPISender, never()).sendEvent(any());
    }

    @Test
    void hideBlockedUser_NotBlocked_ReturnsBadRequest() throws Exception {
        // Given
        Relation existingRelation = new Relation();
        existingRelation.setRelationFlags(RelationFlags.FRIEND.getValue()); // Not blocked
        when(storageFacade.getRelationBetweenUser(CURRENT_USER_ID, TARGET_USER_ID)).thenReturn(existingRelation);

        // When
        ResponseEntity<CommonResponse> response = relationService.hideBlockedUser(CURRENT_USER_ID, TARGET_USER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertEquals("Target user is not blocked", response.getBody().getMessage());

        verify(storageFacade).getRelationBetweenUser(CURRENT_USER_ID, TARGET_USER_ID);
        verify(messageAPISender, never()).sendEvent(any());
    }

    // ==================== getRelations Tests ====================

    @Test
    void getRelations_Success() {
        // Given
        List<Relation> relations = new ArrayList<>();
        Relation relation1 = new Relation();
        relation1.setRelationUserId(TARGET_USER_ID);
        relation1.setRelationNickName(TARGET_USER_NICKNAME);
        relation1.setRelationAvatar(TARGET_USER_AVATAR);
        relation1.setRelationRemarkName(REMARK_NAME);
        relation1.setRelationFlags(RelationFlags.FRIEND.getValue());
        relations.add(relation1);

        PagedRelationResult pagedResult = new PagedRelationResult(relations, null, false);
        when(storageFacade.getRelations(CURRENT_USER_ID, null, 50)).thenReturn(pagedResult);
        when(storageFacade.getLatestRelationVersion(CURRENT_USER_ID)).thenReturn("v1");

        // When
        ResponseEntity<UserRelationsResponse> response =
                relationService.getRelations(CURRENT_USER_ID, null, 50);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertEquals("success", response.getBody().getMessage());
        assertNotNull(response.getBody().getData());
        assertEquals(1, response.getBody().getData().getUsers().size());

        verify(storageFacade).getRelations(CURRENT_USER_ID, null, 50);
        verify(storageFacade).getLatestRelationVersion(CURRENT_USER_ID);
    }

    @Test
    void getRelations_InvalidPageSize_ReturnsBadRequest() {
        // When - page size too large
        ResponseEntity<UserRelationsResponse> response =
                relationService.getRelations(CURRENT_USER_ID, null, 500);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertEquals("Invalid page size", response.getBody().getMessage());

        verifyNoInteractions(storageFacade);
    }

    @Test
    void getRelations_InvalidPageSize_Zero_ReturnsBadRequest() {
        // When - page size zero
        ResponseEntity<UserRelationsResponse> response =
                relationService.getRelations(CURRENT_USER_ID, null, 0);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().getCode());

        verifyNoInteractions(storageFacade);
    }

    // ==================== syncRelations Tests ====================

    @Test
    void syncRelations_NullVersion_RequiresFullSync() {
        // When
        ResponseEntity<RelationSyncResponse> response =
                relationService.syncRelations(CURRENT_USER_ID, null);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertNotNull(response.getBody().getData());
        assertTrue(response.getBody().getData().isFullSync());
        assertTrue(response.getBody().getData().getChanges().isEmpty());

        verifyNoInteractions(storageFacade);
    }

    @Test
    void syncRelations_EmptyVersion_RequiresFullSync() {
        // When
        ResponseEntity<RelationSyncResponse> response =
                relationService.syncRelations(CURRENT_USER_ID, "");

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().getData().isFullSync());

        verifyNoInteractions(storageFacade);
    }

    @Test
    void syncRelations_WithChanges_Success() throws InvalidVersionException {
        // Given
        String version = "v1";
        List<RelationVersionChange> changes = new ArrayList<>();
        RelationVersionChange change = new RelationVersionChange();
        change.setVersion("v2");
        change.setOperation((byte) 1);
        change.setRelationUserId(TARGET_USER_ID);
        change.setNickName(TARGET_USER_NICKNAME);
        change.setRelationFlags(RelationFlags.FRIEND.getValue());
        changes.add(change);

        when(storageFacade.getRelationChanges(CURRENT_USER_ID, version, 200)).thenReturn(changes);

        // When
        ResponseEntity<RelationSyncResponse> response =
                relationService.syncRelations(CURRENT_USER_ID, version);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertNotNull(response.getBody().getData());
        assertFalse(response.getBody().getData().isFullSync());
        assertEquals(1, response.getBody().getData().getChanges().size());
        assertEquals("v2", response.getBody().getData().getLatestVersion());

        verify(storageFacade).getRelationChanges(CURRENT_USER_ID, version, 200);
    }

    @Test
    void syncRelations_InvalidVersion_ReturnsBadRequest() throws InvalidVersionException {
        // Given
        String invalidVersion = "invalid";
        when(storageFacade.getRelationChanges(CURRENT_USER_ID, invalidVersion, 200))
                .thenThrow(new InvalidVersionException("Invalid version format"));

        // When
        ResponseEntity<RelationSyncResponse> response =
                relationService.syncRelations(CURRENT_USER_ID, invalidVersion);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());

        verify(storageFacade).getRelationChanges(CURRENT_USER_ID, invalidVersion, 200);
    }
}
