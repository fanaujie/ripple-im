package com.fanaujie.ripple.apigateway.service;

import com.fanaujie.ripple.apigateway.dto.*;
import com.fanaujie.ripple.communication.msgapi.MessageAPISender;
import com.fanaujie.ripple.protobuf.msgapiserver.SendGroupCommandReq;
import com.fanaujie.ripple.protobuf.snowflakeid.GenerateIdResponse;
import com.fanaujie.ripple.snowflakeid.client.SnowflakeIdClient;
import com.fanaujie.ripple.storage.exception.InvalidVersionException;
import com.fanaujie.ripple.storage.exception.NotFoundGroupException;
import com.fanaujie.ripple.storage.model.*;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = {GroupService.class})
class GroupServiceTest {

    @MockitoBean
    private MessageAPISender messageAPISender;

    @MockitoBean
    private SnowflakeIdClient snowflakeIdClient;

    @MockitoBean
    private RippleStorageFacade storageFacade;

    @Autowired
    private GroupService groupService;

    private static final long USER_ID = 1L;
    private static final long GROUP_ID = 100L;
    private static final long MESSAGE_ID = 12345L;
    private static final String VERSION = "v1";

    @BeforeEach
    void setUp() {
        reset(messageAPISender, snowflakeIdClient, storageFacade);
    }

    // ==================== createGroup Tests ====================

    @Test
    void createGroup_Success() throws Exception {
        // Given
        GenerateIdResponse groupIdResponse = GenerateIdResponse.newBuilder().setId(GROUP_ID).build();
        GenerateIdResponse messageIdResponse = GenerateIdResponse.newBuilder().setId(MESSAGE_ID).build();
        when(snowflakeIdClient.requestSnowflakeId())
                .thenReturn(CompletableFuture.completedFuture(groupIdResponse))
                .thenReturn(CompletableFuture.completedFuture(messageIdResponse));
        doNothing().when(messageAPISender).sendGroupCommand(any(SendGroupCommandReq.class));

        CreateGroupRequest request = new CreateGroupRequest();
        request.setSenderId(String.valueOf(USER_ID));
        request.setGroupName("Test Group");
        request.setMemberIds(Arrays.asList("2", "3"));

        // When
        ResponseEntity<CreateGroupResponse> response = groupService.createGroup(request);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertEquals(String.valueOf(GROUP_ID), response.getBody().getData().getGroupId());

        verify(snowflakeIdClient, times(2)).requestSnowflakeId();
        verify(messageAPISender).sendGroupCommand(any(SendGroupCommandReq.class));
    }

    @Test
    void createGroup_InvalidSenderId_ReturnsBadRequest() throws Exception {
        // Given
        GenerateIdResponse groupIdResponse = GenerateIdResponse.newBuilder().setId(GROUP_ID).build();
        GenerateIdResponse messageIdResponse = GenerateIdResponse.newBuilder().setId(MESSAGE_ID).build();
        when(snowflakeIdClient.requestSnowflakeId())
                .thenReturn(CompletableFuture.completedFuture(groupIdResponse))
                .thenReturn(CompletableFuture.completedFuture(messageIdResponse));

        CreateGroupRequest request = new CreateGroupRequest();
        request.setSenderId("invalid");
        request.setGroupName("Test Group");
        request.setMemberIds(Arrays.asList("2", "3"));

        // When
        ResponseEntity<CreateGroupResponse> response = groupService.createGroup(request);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().getCode());
        assertTrue(response.getBody().getMessage().contains("Invalid sender ID"));

        verifyNoInteractions(messageAPISender);
    }

    @Test
    void createGroup_InvalidMemberId_ReturnsBadRequest() throws Exception {
        // Given
        GenerateIdResponse groupIdResponse = GenerateIdResponse.newBuilder().setId(GROUP_ID).build();
        GenerateIdResponse messageIdResponse = GenerateIdResponse.newBuilder().setId(MESSAGE_ID).build();
        when(snowflakeIdClient.requestSnowflakeId())
                .thenReturn(CompletableFuture.completedFuture(groupIdResponse))
                .thenReturn(CompletableFuture.completedFuture(messageIdResponse));

        CreateGroupRequest request = new CreateGroupRequest();
        request.setSenderId(String.valueOf(USER_ID));
        request.setGroupName("Test Group");
        request.setMemberIds(Arrays.asList("2", "invalid"));

        // When
        ResponseEntity<CreateGroupResponse> response = groupService.createGroup(request);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().getCode());
        assertTrue(response.getBody().getMessage().contains("Invalid new member ID"));
    }

    @Test
    void createGroup_GrpcError_ReturnsInternalError() throws Exception {
        // Given
        GenerateIdResponse groupIdResponse = GenerateIdResponse.newBuilder().setId(GROUP_ID).build();
        GenerateIdResponse messageIdResponse = GenerateIdResponse.newBuilder().setId(MESSAGE_ID).build();
        when(snowflakeIdClient.requestSnowflakeId())
                .thenReturn(CompletableFuture.completedFuture(groupIdResponse))
                .thenReturn(CompletableFuture.completedFuture(messageIdResponse));
        doThrow(new RuntimeException("gRPC error"))
                .when(messageAPISender).sendGroupCommand(any(SendGroupCommandReq.class));

        CreateGroupRequest request = new CreateGroupRequest();
        request.setSenderId(String.valueOf(USER_ID));
        request.setGroupName("Test Group");
        request.setMemberIds(Arrays.asList("2", "3"));

        // When
        ResponseEntity<CreateGroupResponse> response = groupService.createGroup(request);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(500, response.getBody().getCode());
    }

    // ==================== inviteGroupMembers Tests ====================

    @Test
    void inviteGroupMembers_Success() throws Exception {
        // Given
        GenerateIdResponse messageIdResponse = GenerateIdResponse.newBuilder().setId(MESSAGE_ID).build();
        when(snowflakeIdClient.requestSnowflakeId())
                .thenReturn(CompletableFuture.completedFuture(messageIdResponse));
        doNothing().when(messageAPISender).sendGroupCommand(any(SendGroupCommandReq.class));

        InviteGroupMemberRequest request = new InviteGroupMemberRequest();
        request.setSenderId(String.valueOf(USER_ID));
        request.setNewMemberIds(Arrays.asList("4", "5"));
        request.setGroupName("Test Group");

        // When
        ResponseEntity<CommonResponse> response = groupService.inviteGroupMembers(GROUP_ID, request);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(200, response.getBody().getCode());

        verify(messageAPISender).sendGroupCommand(any(SendGroupCommandReq.class));
    }

    @Test
    void inviteGroupMembers_InvalidSenderId_ReturnsBadRequest() throws Exception {
        // Given
        GenerateIdResponse messageIdResponse = GenerateIdResponse.newBuilder().setId(MESSAGE_ID).build();
        when(snowflakeIdClient.requestSnowflakeId())
                .thenReturn(CompletableFuture.completedFuture(messageIdResponse));

        InviteGroupMemberRequest request = new InviteGroupMemberRequest();
        request.setSenderId("invalid");
        request.setNewMemberIds(Arrays.asList("4", "5"));

        // When
        ResponseEntity<CommonResponse> response = groupService.inviteGroupMembers(GROUP_ID, request);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().getCode());
        assertTrue(response.getBody().getMessage().contains("Invalid sender ID"));
    }

    @Test
    void inviteGroupMembers_InvalidMemberId_ReturnsBadRequest() throws Exception {
        // Given
        GenerateIdResponse messageIdResponse = GenerateIdResponse.newBuilder().setId(MESSAGE_ID).build();
        when(snowflakeIdClient.requestSnowflakeId())
                .thenReturn(CompletableFuture.completedFuture(messageIdResponse));

        InviteGroupMemberRequest request = new InviteGroupMemberRequest();
        request.setSenderId(String.valueOf(USER_ID));
        request.setNewMemberIds(Arrays.asList("4", "invalid"));

        // When
        ResponseEntity<CommonResponse> response = groupService.inviteGroupMembers(GROUP_ID, request);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().getCode());
        assertTrue(response.getBody().getMessage().contains("Invalid new member ID"));
    }

    // ==================== getGroupMembers Tests ====================

    @Test
    void getGroupMembers_Success() throws NotFoundGroupException {
        // Given
        List<GroupMemberInfo> members = new ArrayList<>();
        GroupMemberInfo member = new GroupMemberInfo();
        member.setUserId(USER_ID);
        member.setName("User1");
        member.setAvatar("avatar1.jpg");
        members.add(member);

        when(storageFacade.getGroupMembersInfo(GROUP_ID)).thenReturn(members);

        PagedGroupMemberResult pagedResult = new PagedGroupMemberResult(members, null, false);
        when(storageFacade.getGroupMembersPaged(GROUP_ID, null, 50)).thenReturn(pagedResult);
        when(storageFacade.getLatestGroupVersion(GROUP_ID)).thenReturn("v1");

        // When
        ResponseEntity<GetGroupMembersResponse> response =
                groupService.getGroupMembers(GROUP_ID, USER_ID, null, 50);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(200, response.getBody().getCode());
        assertEquals(1, response.getBody().getData().getMembers().size());

        verify(storageFacade).getGroupMembersInfo(GROUP_ID);
        verify(storageFacade).getGroupMembersPaged(GROUP_ID, null, 50);
    }

    @Test
    void getGroupMembers_NotMember_ReturnsForbidden() throws NotFoundGroupException {
        // Given
        List<GroupMemberInfo> members = new ArrayList<>();
        GroupMemberInfo member = new GroupMemberInfo();
        member.setUserId(2L); // Different user
        member.setName("User2");
        members.add(member);

        when(storageFacade.getGroupMembersInfo(GROUP_ID)).thenReturn(members);

        // When
        ResponseEntity<GetGroupMembersResponse> response =
                groupService.getGroupMembers(GROUP_ID, USER_ID, null, 50);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(403, response.getBody().getCode());
        assertTrue(response.getBody().getMessage().contains("Not a group member"));
    }

    @Test
    void getGroupMembers_GroupNotFound_Returns404() throws NotFoundGroupException {
        // Given
        when(storageFacade.getGroupMembersInfo(GROUP_ID))
                .thenThrow(new NotFoundGroupException("Group not found"));

        // When
        ResponseEntity<GetGroupMembersResponse> response =
                groupService.getGroupMembers(GROUP_ID, USER_ID, null, 50);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(404, response.getBody().getCode());
    }

    @Test
    void getGroupMembers_InvalidPageSize_ReturnsBadRequest() {
        // When
        ResponseEntity<GetGroupMembersResponse> response =
                groupService.getGroupMembers(GROUP_ID, USER_ID, null, 0);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().getCode());
        assertTrue(response.getBody().getMessage().contains("Invalid page size"));

        verifyNoInteractions(storageFacade);
    }

    @Test
    void getGroupMembers_InvalidPageSize_TooLarge_ReturnsBadRequest() {
        // When
        ResponseEntity<GetGroupMembersResponse> response =
                groupService.getGroupMembers(GROUP_ID, USER_ID, null, 500);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().getCode());

        verifyNoInteractions(storageFacade);
    }

    // ==================== removeGroupMember Tests ====================

    @Test
    void removeGroupMember_Success() throws Exception {
        // Given
        GenerateIdResponse messageIdResponse = GenerateIdResponse.newBuilder().setId(MESSAGE_ID).build();
        when(snowflakeIdClient.requestSnowflakeId())
                .thenReturn(CompletableFuture.completedFuture(messageIdResponse));
        doNothing().when(messageAPISender).sendGroupCommand(any(SendGroupCommandReq.class));

        // When
        ResponseEntity<CommonResponse> response = groupService.removeGroupMember(GROUP_ID, USER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(200, response.getBody().getCode());

        verify(messageAPISender).sendGroupCommand(any(SendGroupCommandReq.class));
    }

    @Test
    void removeGroupMember_Error_ReturnsInternalError() throws Exception {
        // Given
        GenerateIdResponse messageIdResponse = GenerateIdResponse.newBuilder().setId(MESSAGE_ID).build();
        when(snowflakeIdClient.requestSnowflakeId())
                .thenReturn(CompletableFuture.completedFuture(messageIdResponse));
        doThrow(new RuntimeException("gRPC error"))
                .when(messageAPISender).sendGroupCommand(any(SendGroupCommandReq.class));

        // When
        ResponseEntity<CommonResponse> response = groupService.removeGroupMember(GROUP_ID, USER_ID);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(500, response.getBody().getCode());
    }

    // ==================== updateGroupName Tests ====================

    @Test
    void updateGroupName_Success() throws Exception {
        // Given
        GenerateIdResponse messageIdResponse = GenerateIdResponse.newBuilder().setId(MESSAGE_ID).build();
        when(snowflakeIdClient.requestSnowflakeId())
                .thenReturn(CompletableFuture.completedFuture(messageIdResponse));
        doNothing().when(messageAPISender).sendGroupCommand(any(SendGroupCommandReq.class));

        UpdateGroupInfoRequest request = new UpdateGroupInfoRequest();
        request.setSenderId(String.valueOf(USER_ID));
        request.setValue("New Group Name");

        // When
        ResponseEntity<CommonResponse> response = groupService.updateGroupName(GROUP_ID, request);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(200, response.getBody().getCode());

        verify(messageAPISender).sendGroupCommand(any(SendGroupCommandReq.class));
    }

    @Test
    void updateGroupName_InvalidSenderId_ReturnsBadRequest() throws Exception {
        // Given
        GenerateIdResponse messageIdResponse = GenerateIdResponse.newBuilder().setId(MESSAGE_ID).build();
        when(snowflakeIdClient.requestSnowflakeId())
                .thenReturn(CompletableFuture.completedFuture(messageIdResponse));

        UpdateGroupInfoRequest request = new UpdateGroupInfoRequest();
        request.setSenderId("invalid");
        request.setValue("New Group Name");

        // When
        ResponseEntity<CommonResponse> response = groupService.updateGroupName(GROUP_ID, request);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().getCode());
        assertTrue(response.getBody().getMessage().contains("Invalid sender ID"));
    }

    // ==================== syncGroupMembers Tests ====================

    @Test
    void syncGroupMembers_NullVersion_RequiresFullSync() {
        // When
        ResponseEntity<GroupSyncResponse> response = groupService.syncGroupMembers(GROUP_ID, null);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(200, response.getBody().getCode());
        assertTrue(response.getBody().getData().isFullSync());
        assertTrue(response.getBody().getData().getChanges().isEmpty());

        verifyNoInteractions(storageFacade);
    }

    @Test
    void syncGroupMembers_EmptyVersion_RequiresFullSync() {
        // When
        ResponseEntity<GroupSyncResponse> response = groupService.syncGroupMembers(GROUP_ID, "");

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().getData().isFullSync());

        verifyNoInteractions(storageFacade);
    }

    @Test
    void syncGroupMembers_WithChanges_Success() throws InvalidVersionException {
        // Given
        List<GroupVersionChange> changes = new ArrayList<>();
        GroupVersionChange change = new GroupVersionChange();
        change.setGroupId(GROUP_ID);
        change.setVersion("v2");
        change.setChanges(new ArrayList<>());
        changes.add(change);

        when(storageFacade.getGroupChanges(GROUP_ID, VERSION, 200)).thenReturn(changes);

        // When
        ResponseEntity<GroupSyncResponse> response = groupService.syncGroupMembers(GROUP_ID, VERSION);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(200, response.getBody().getCode());
        assertFalse(response.getBody().getData().isFullSync());
        assertEquals(1, response.getBody().getData().getChanges().size());
        assertEquals("v2", response.getBody().getData().getLatestVersion());

        verify(storageFacade).getGroupChanges(GROUP_ID, VERSION, 200);
    }

    @Test
    void syncGroupMembers_InvalidVersion_RequiresFullSync() throws InvalidVersionException {
        // Given - InvalidVersionException triggers full sync instead of error
        when(storageFacade.getGroupChanges(GROUP_ID, "invalid", 200))
                .thenThrow(new InvalidVersionException("Invalid version"));

        // When
        ResponseEntity<GroupSyncResponse> response = groupService.syncGroupMembers(GROUP_ID, "invalid");

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().getData().isFullSync());
    }

    // ==================== syncUserGroups Tests ====================

    @Test
    void syncUserGroups_NullVersion_RequiresFullSync() {
        // When
        ResponseEntity<UserGroupSyncResponse> response = groupService.syncUserGroups(USER_ID, null);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(200, response.getBody().getCode());
        assertTrue(response.getBody().getData().isFullSync());

        verifyNoInteractions(storageFacade);
    }

    @Test
    void syncUserGroups_WithChanges_Success() throws InvalidVersionException {
        // Given
        List<UserGroupVersionChange> changes = new ArrayList<>();
        UserGroupVersionChange change = new UserGroupVersionChange();
        change.setVersion("v2");
        change.setOperation((byte) 1);
        change.setGroupId(GROUP_ID);
        change.setGroupName("Test Group");
        changes.add(change);

        when(storageFacade.getUserGroupChanges(USER_ID, VERSION, 200)).thenReturn(changes);

        // When
        ResponseEntity<UserGroupSyncResponse> response = groupService.syncUserGroups(USER_ID, VERSION);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertFalse(response.getBody().getData().isFullSync());
        assertEquals(1, response.getBody().getData().getChanges().size());
        assertEquals("v2", response.getBody().getData().getLatestVersion());

        verify(storageFacade).getUserGroupChanges(USER_ID, VERSION, 200);
    }

    @Test
    void syncUserGroups_InvalidVersion_RequiresFullSync() throws InvalidVersionException {
        // Given
        when(storageFacade.getUserGroupChanges(USER_ID, "invalid", 200))
                .thenThrow(new InvalidVersionException("Invalid version"));

        // When
        ResponseEntity<UserGroupSyncResponse> response = groupService.syncUserGroups(USER_ID, "invalid");

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().getData().isFullSync());
    }

    // ==================== getUserGroups Tests ====================

    @Test
    void getUserGroups_Success() {
        // Given
        List<UserGroup> groups = new ArrayList<>();
        UserGroup group = new UserGroup(GROUP_ID, "Test Group", "avatar.jpg");
        groups.add(group);

        PagedUserGroupResult pagedResult = new PagedUserGroupResult(groups, null, false);
        when(storageFacade.getUserGroupsPaged(USER_ID, null, 50)).thenReturn(pagedResult);
        when(storageFacade.getLatestUserGroupVersion(USER_ID)).thenReturn("v1");

        // When
        ResponseEntity<GetUserGroupsResponse> response =
                groupService.getUserGroups(USER_ID, null, 50);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(200, response.getBody().getCode());
        assertEquals(1, response.getBody().getData().getGroups().size());
        assertEquals(String.valueOf(GROUP_ID), response.getBody().getData().getGroups().get(0).getGroupId());

        verify(storageFacade).getUserGroupsPaged(USER_ID, null, 50);
        verify(storageFacade).getLatestUserGroupVersion(USER_ID);
    }

    @Test
    void getUserGroups_WithPagination() {
        // Given
        List<UserGroup> groups = new ArrayList<>();
        PagedUserGroupResult pagedResult = new PagedUserGroupResult(groups, "nextToken", true);
        when(storageFacade.getUserGroupsPaged(USER_ID, "token", 20)).thenReturn(pagedResult);

        // When
        ResponseEntity<GetUserGroupsResponse> response =
                groupService.getUserGroups(USER_ID, "token", 20);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().getData().isHasMore());
        assertEquals("nextToken", response.getBody().getData().getNextPageToken());

        verify(storageFacade).getUserGroupsPaged(USER_ID, "token", 20);
        verify(storageFacade, never()).getLatestUserGroupVersion(USER_ID);
    }

    @Test
    void getUserGroups_InvalidPageSize_ReturnsBadRequest() {
        // When
        ResponseEntity<GetUserGroupsResponse> response =
                groupService.getUserGroups(USER_ID, null, 0);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().getCode());
        assertTrue(response.getBody().getMessage().contains("Invalid page size"));

        verifyNoInteractions(storageFacade);
    }

    @Test
    void getUserGroups_Error_ReturnsInternalError() {
        // Given
        when(storageFacade.getUserGroupsPaged(USER_ID, null, 50))
                .thenThrow(new RuntimeException("Storage error"));

        // When
        ResponseEntity<GetUserGroupsResponse> response =
                groupService.getUserGroups(USER_ID, null, 50);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(500, response.getBody().getCode());
    }
}
