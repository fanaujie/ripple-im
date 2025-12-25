package com.fanaujie.ripple.apigateway.controller;

import com.fanaujie.ripple.apigateway.config.SecurityConfig;
import com.fanaujie.ripple.apigateway.dto.*;
import com.fanaujie.ripple.apigateway.service.GroupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.Collections;

@WebMvcTest(GroupsController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {"oauth2.jwk.secret=dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdG9rZW4tMTIzNA=="})
class GroupsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GroupService groupService;

    private static final String USER_ID = "1";
    private static final String GROUP_ID = "100";

    @BeforeEach
    void setUp() {
        reset(groupService);
    }

    // ==================== createGroup Tests ====================

    @Test
    void createGroup_Success() throws Exception {
        GroupData data = new GroupData(GROUP_ID);
        when(groupService.createGroup(any(CreateGroupRequest.class)))
                .thenReturn(ResponseEntity.ok(new CreateGroupResponse(200, "success", data)));

        CreateGroupRequest request = new CreateGroupRequest();
        request.setSenderId(USER_ID);
        request.setGroupName("Test Group");
        request.setMemberIds(Arrays.asList("2", "3"));

        mockMvc.perform(post("/api/groups")
                        .with(jwt().jwt(builder -> builder.subject(USER_ID)).authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.groupId").value(GROUP_ID));

        verify(groupService).createGroup(any(CreateGroupRequest.class));
    }

    @Test
    void createGroup_SenderIdMismatch_ReturnsForbidden() throws Exception {
        CreateGroupRequest request = new CreateGroupRequest();
        request.setSenderId("999"); // Different from JWT subject
        request.setGroupName("Test Group");
        request.setMemberIds(Arrays.asList("2", "3"));

        mockMvc.perform(post("/api/groups")
                        .with(jwt().jwt(builder -> builder.subject(USER_ID)).authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("Forbidden: Sender ID mismatch"));

        verifyNoInteractions(groupService);
    }

    @Test
    void createGroup_Unauthorized() throws Exception {
        CreateGroupRequest request = new CreateGroupRequest();
        request.setSenderId(USER_ID);
        request.setGroupName("Test Group");
        request.setMemberIds(Arrays.asList("2", "3"));

        mockMvc.perform(post("/api/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(groupService);
    }

    // ==================== getGroupMembers Tests ====================

    @Test
    void getGroupMembers_Success() throws Exception {
        List<GroupMemberData> members = new ArrayList<>();
        GroupMemberData member = new GroupMemberData();
        member.setUserId(USER_ID);
        member.setName("User1");
        members.add(member);

        GroupMembersData data = new GroupMembersData(members, null, false, "v1");
        when(groupService.getGroupMembers(100L, 1L, null, 50))
                .thenReturn(ResponseEntity.ok(GetGroupMembersResponse.success(data)));

        mockMvc.perform(get("/api/groups/{groupId}/members", GROUP_ID)
                        .with(jwt().jwt(builder -> builder.subject(USER_ID)).authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.members").isArray());

        verify(groupService).getGroupMembers(100L, 1L, null, 50);
    }

    @Test
    void getGroupMembers_WithPagination() throws Exception {
        List<GroupMemberData> members = new ArrayList<>();
        GroupMembersData data = new GroupMembersData(members, "nextToken", true, null);
        when(groupService.getGroupMembers(100L, 1L, "token", 20))
                .thenReturn(ResponseEntity.ok(GetGroupMembersResponse.success(data)));

        mockMvc.perform(get("/api/groups/{groupId}/members", GROUP_ID)
                        .param("nextPageToken", "token")
                        .param("pageSize", "20")
                        .with(jwt().jwt(builder -> builder.subject(USER_ID)).authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasMore").value(true))
                .andExpect(jsonPath("$.data.nextPageToken").value("nextToken"));

        verify(groupService).getGroupMembers(100L, 1L, "token", 20);
    }

    @Test
    void getGroupMembers_NotMember_ReturnsForbidden() throws Exception {
        when(groupService.getGroupMembers(100L, 1L, null, 50))
                .thenReturn(ResponseEntity.status(403)
                        .body(GetGroupMembersResponse.error(403, "Forbidden: Not a group member")));

        mockMvc.perform(get("/api/groups/{groupId}/members", GROUP_ID)
                        .with(jwt().jwt(builder -> builder.subject(USER_ID)).authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user")))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void getGroupMembers_GroupNotFound_Returns404() throws Exception {
        when(groupService.getGroupMembers(100L, 1L, null, 50))
                .thenReturn(ResponseEntity.status(404)
                        .body(GetGroupMembersResponse.error(404, "Group not found")));

        mockMvc.perform(get("/api/groups/{groupId}/members", GROUP_ID)
                        .with(jwt().jwt(builder -> builder.subject(USER_ID)).authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user")))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void getGroupMembers_InvalidGroupId() throws Exception {
        mockMvc.perform(get("/api/groups/{groupId}/members", "invalid")
                        .with(jwt().jwt(builder -> builder.subject(USER_ID)).authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Invalid group ID format"));

        verifyNoInteractions(groupService);
    }

    // ==================== addGroupMembers Tests ====================

    @Test
    void addGroupMembers_Success() throws Exception {
        when(groupService.inviteGroupMembers(eq(100L), any(InviteGroupMemberRequest.class)))
                .thenReturn(ResponseEntity.ok(CommonResponse.success()));

        InviteGroupMemberRequest request = new InviteGroupMemberRequest();
        request.setSenderId(USER_ID);
        request.setNewMemberIds(Arrays.asList("4", "5"));
        request.setGroupName("Test Group");

        mockMvc.perform(post("/api/groups/{groupId}/members", GROUP_ID)
                        .with(jwt().jwt(builder -> builder.subject(USER_ID)).authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(groupService).inviteGroupMembers(eq(100L), any(InviteGroupMemberRequest.class));
    }

    @Test
    void addGroupMembers_SenderIdMismatch_ReturnsForbidden() throws Exception {
        InviteGroupMemberRequest request = new InviteGroupMemberRequest();
        request.setSenderId("999"); // Different from JWT subject
        request.setNewMemberIds(Arrays.asList("4", "5"));
        request.setGroupName("Test Group");

        mockMvc.perform(post("/api/groups/{groupId}/members", GROUP_ID)
                        .with(jwt().jwt(builder -> builder.subject(USER_ID)).authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verifyNoInteractions(groupService);
    }

    // ==================== leaveGroup Tests ====================

    @Test
    void leaveGroup_Success() throws Exception {
        when(groupService.removeGroupMember(100L, 1L))
                .thenReturn(ResponseEntity.ok(CommonResponse.success()));

        mockMvc.perform(delete("/api/groups/{groupId}/members/me", GROUP_ID)
                        .with(jwt().jwt(builder -> builder.subject(USER_ID)).authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(groupService).removeGroupMember(100L, 1L);
    }

    @Test
    void leaveGroup_InvalidGroupId() throws Exception {
        mockMvc.perform(delete("/api/groups/{groupId}/members/me", "invalid")
                        .with(jwt().jwt(builder -> builder.subject(USER_ID)).authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(groupService);
    }

    // ==================== updateGroup Tests ====================

    @Test
    void updateGroup_Success() throws Exception {
        when(groupService.updateGroupName(eq(100L), any(UpdateGroupInfoRequest.class)))
                .thenReturn(ResponseEntity.ok(CommonResponse.success()));

        UpdateGroupRequest request = new UpdateGroupRequest();
        request.setSenderId(USER_ID);
        request.setName("New Group Name");

        mockMvc.perform(patch("/api/groups/{groupId}", GROUP_ID)
                        .with(jwt().jwt(builder -> builder.subject(USER_ID)).authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(groupService).updateGroupName(eq(100L), any(UpdateGroupInfoRequest.class));
    }

    @Test
    void updateGroup_NoFieldsToUpdate() throws Exception {
        UpdateGroupRequest request = new UpdateGroupRequest();
        request.setSenderId(USER_ID);
        // No name field

        mockMvc.perform(patch("/api/groups/{groupId}", GROUP_ID)
                        .with(jwt().jwt(builder -> builder.subject(USER_ID)).authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("No fields to update"));

        verifyNoInteractions(groupService);
    }

    @Test
    void updateGroup_SenderIdMismatch_ReturnsForbidden() throws Exception {
        UpdateGroupRequest request = new UpdateGroupRequest();
        request.setSenderId("999"); // Different from JWT subject
        request.setName("New Group Name");

        mockMvc.perform(patch("/api/groups/{groupId}", GROUP_ID)
                        .with(jwt().jwt(builder -> builder.subject(USER_ID)).authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verifyNoInteractions(groupService);
    }

    // ==================== syncGroupMembers Tests ====================

    @Test
    void syncGroupMembers_Success() throws Exception {
        List<GroupChange> changes = new ArrayList<>();
        GroupSyncData data = new GroupSyncData(false, "v2", changes);
        when(groupService.syncGroupMembers(100L, "v1"))
                .thenReturn(ResponseEntity.ok(new GroupSyncResponse(200, "success", data)));

        mockMvc.perform(get("/api/groups/{groupId}/members/sync", GROUP_ID)
                        .param("version", "v1")
                        .with(jwt().jwt(builder -> builder.subject(USER_ID)).authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.fullSync").value(false));

        verify(groupService).syncGroupMembers(100L, "v1");
    }

    @Test
    void syncGroupMembers_FullSync() throws Exception {
        GroupSyncData data = new GroupSyncData(true, null, new ArrayList<>());
        when(groupService.syncGroupMembers(100L, null))
                .thenReturn(ResponseEntity.ok(new GroupSyncResponse(200, "success", data)));

        mockMvc.perform(get("/api/groups/{groupId}/members/sync", GROUP_ID)
                        .with(jwt().jwt(builder -> builder.subject(USER_ID)).authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullSync").value(true));

        verify(groupService).syncGroupMembers(100L, null);
    }

    @Test
    void syncGroupMembers_InvalidGroupId() throws Exception {
        mockMvc.perform(get("/api/groups/{groupId}/members/sync", "invalid")
                        .with(jwt().jwt(builder -> builder.subject(USER_ID)).authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_user")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(groupService);
    }

    // ==================== Authorization Tests ====================

    @Test
    void allEndpoints_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/groups/100/members")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/groups/100/members/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/groups/100")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isUnauthorized());
    }
}
