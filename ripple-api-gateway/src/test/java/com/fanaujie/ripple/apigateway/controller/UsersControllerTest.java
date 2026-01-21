package com.fanaujie.ripple.apigateway.controller;

import com.fanaujie.ripple.apigateway.config.SecurityConfig;
import com.fanaujie.ripple.apigateway.dto.*;
import com.fanaujie.ripple.apigateway.service.*;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.Collections;

@WebMvcTest(UsersController.class)
@Import(SecurityConfig.class)
@TestPropertySource(
        properties = {"oauth2.jwk.secret=dGVzdC1zZWNyZXQta2V5LWZvci1qd3QtdG9rZW4tMTIzNA=="})
class UsersControllerTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private MessageService messageService;

    @MockitoBean private ConversationService conversationService;

    @MockitoBean private UserProfileService userProfileService;

    @MockitoBean private RelationService relationService;

    @MockitoBean private GroupService groupService;

    private static final String USER_ID = "1";
    private static final String TARGET_USER_ID = "2";

    @BeforeEach
    void setUp() {
        reset(
                messageService,
                conversationService,
                userProfileService,
                relationService,
                groupService);
    }

    // ==================== Profile Tests ====================

    @Test
    void getMyProfile_Success() throws Exception {
        UserProfileData profileData = new UserProfileData(USER_ID, "TestUser", "avatar.jpg");
        when(userProfileService.getUserProfile(1L))
                .thenReturn(
                        ResponseEntity.ok(new UserProfileResponse(200, "success", profileData)));

        mockMvc.perform(
                        get("/api/users/me/profile")
                                .with(
                                        jwt().jwt(builder -> builder.subject(USER_ID))
                                                .authorities(
                                                        Collections.singletonList(
                                                                new SimpleGrantedAuthority(
                                                                        "ROLE_user")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userId").value(USER_ID))
                .andExpect(jsonPath("$.data.nickName").value("TestUser"));

        verify(userProfileService).getUserProfile(1L);
    }

    @Test
    void getMyProfile_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/users/me/profile")).andExpect(status().isUnauthorized());

        verifyNoInteractions(userProfileService);
    }

    @Test
    void getUserProfile_Success() throws Exception {
        UserProfileData profileData =
                new UserProfileData(TARGET_USER_ID, "TargetUser", "avatar.jpg");
        when(userProfileService.getUserProfile(2L))
                .thenReturn(
                        ResponseEntity.ok(new UserProfileResponse(200, "success", profileData)));

        mockMvc.perform(
                        get("/api/users/{userId}/profile", TARGET_USER_ID)
                                .with(
                                        jwt().jwt(builder -> builder.subject(USER_ID))
                                                .authorities(
                                                        Collections.singletonList(
                                                                new SimpleGrantedAuthority(
                                                                        "ROLE_user")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userId").value(TARGET_USER_ID));

        verify(userProfileService).getUserProfile(2L);
    }

    @Test
    void getUserProfile_NotFound() throws Exception {
        when(userProfileService.getUserProfile(2L))
                .thenReturn(
                        ResponseEntity.status(404)
                                .body(
                                        new UserProfileResponse(
                                                404, "User profile not found", null)));

        mockMvc.perform(
                        get("/api/users/{userId}/profile", TARGET_USER_ID)
                                .with(
                                        jwt().jwt(builder -> builder.subject(USER_ID))
                                                .authorities(
                                                        Collections.singletonList(
                                                                new SimpleGrantedAuthority(
                                                                        "ROLE_user")))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));

        verify(userProfileService).getUserProfile(2L);
    }

    @Test
    void getUserProfile_InvalidId() throws Exception {
        mockMvc.perform(
                        get("/api/users/{userId}/profile", "invalid")
                                .with(
                                        jwt().jwt(builder -> builder.subject(USER_ID))
                                                .authorities(
                                                        Collections.singletonList(
                                                                new SimpleGrantedAuthority(
                                                                        "ROLE_user")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(userProfileService);
    }

    @Test
    void updateProfile_Success() throws Exception {
        when(userProfileService.updateNickName(1L, "NewNickname"))
                .thenReturn(ResponseEntity.ok(new CommonResponse(200, "success")));

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setNickname("NewNickname");

        mockMvc.perform(
                        patch("/api/users/me/profile")
                                .with(
                                        jwt().jwt(builder -> builder.subject(USER_ID))
                                                .authorities(
                                                        Collections.singletonList(
                                                                new SimpleGrantedAuthority(
                                                                        "ROLE_user"))))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(userProfileService).updateNickName(1L, "NewNickname");
    }

    @Test
    void updateProfile_NoFieldsToUpdate() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest();

        mockMvc.perform(
                        patch("/api/users/me/profile")
                                .with(
                                        jwt().jwt(builder -> builder.subject(USER_ID))
                                                .authorities(
                                                        Collections.singletonList(
                                                                new SimpleGrantedAuthority(
                                                                        "ROLE_user"))))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("No fields to update"));

        verifyNoInteractions(userProfileService);
    }

    @Test
    void deleteAvatar_Success() throws Exception {
        when(userProfileService.deleteAvatar(1L))
                .thenReturn(ResponseEntity.ok(new CommonResponse(200, "success")));

        mockMvc.perform(
                        delete("/api/users/me/avatar")
                                .with(
                                        jwt().jwt(builder -> builder.subject(USER_ID))
                                                .authorities(
                                                        Collections.singletonList(
                                                                new SimpleGrantedAuthority(
                                                                        "ROLE_user")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(userProfileService).deleteAvatar(1L);
    }

    // ==================== Relations Tests ====================

    @Test
    void getRelations_Success() throws Exception {
        List<User> users = new ArrayList<>();
        users.add(new User(TARGET_USER_ID, "Friend", "avatar.jpg", "FriendRemark", 1));
        UserRelationsData data = new UserRelationsData(users, null, false, "v1");
        when(relationService.getRelations(1L, null, 50))
                .thenReturn(ResponseEntity.ok(new UserRelationsResponse(200, "success", data)));

        mockMvc.perform(
                        get("/api/users/me/relations")
                                .with(
                                        jwt().jwt(builder -> builder.subject(USER_ID))
                                                .authorities(
                                                        Collections.singletonList(
                                                                new SimpleGrantedAuthority(
                                                                        "ROLE_user")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.users").isArray())
                .andExpect(jsonPath("$.data.users[0].userId").value(TARGET_USER_ID));

        verify(relationService).getRelations(1L, null, 50);
    }

    @Test
    void getRelations_WithPagination() throws Exception {
        List<User> users = new ArrayList<>();
        UserRelationsData data = new UserRelationsData(users, "nextToken", true, null);
        when(relationService.getRelations(1L, "token", 20))
                .thenReturn(ResponseEntity.ok(new UserRelationsResponse(200, "success", data)));

        mockMvc.perform(
                        get("/api/users/me/relations")
                                .param("nextPageToken", "token")
                                .param("pageSize", "20")
                                .with(
                                        jwt().jwt(builder -> builder.subject(USER_ID))
                                                .authorities(
                                                        Collections.singletonList(
                                                                new SimpleGrantedAuthority(
                                                                        "ROLE_user")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasMore").value(true))
                .andExpect(jsonPath("$.data.nextPageToken").value("nextToken"));

        verify(relationService).getRelations(1L, "token", 20);
    }

    @Test
    void syncRelations_Success() throws Exception {
        List<RelationChange> changes = new ArrayList<>();
        RelationSyncData data = new RelationSyncData(false, "v2", changes);
        when(relationService.syncRelations(1L, "v1"))
                .thenReturn(ResponseEntity.ok(new RelationSyncResponse(200, "success", data)));

        mockMvc.perform(
                        get("/api/users/me/relations/sync")
                                .param("version", "v1")
                                .with(
                                        jwt().jwt(builder -> builder.subject(USER_ID))
                                                .authorities(
                                                        Collections.singletonList(
                                                                new SimpleGrantedAuthority(
                                                                        "ROLE_user")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.fullSync").value(false));

        verify(relationService).syncRelations(1L, "v1");
    }

    // ==================== Friends Tests ====================

    @Test
    void addFriend_Success() throws Exception {
        when(relationService.addFriend(1L, 2L))
                .thenReturn(ResponseEntity.ok(new CommonResponse(200, "success")));

        AddFriendRequest request = new AddFriendRequest();
        request.setTargetUserId(TARGET_USER_ID);

        mockMvc.perform(
                        post("/api/users/me/friends")
                                .with(
                                        jwt().jwt(builder -> builder.subject(USER_ID))
                                                .authorities(
                                                        Collections.singletonList(
                                                                new SimpleGrantedAuthority(
                                                                        "ROLE_user"))))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(relationService).addFriend(1L, 2L);
    }

    @Test
    void addFriend_InvalidTargetId() throws Exception {
        AddFriendRequest request = new AddFriendRequest();
        request.setTargetUserId("invalid");

        mockMvc.perform(
                        post("/api/users/me/friends")
                                .with(
                                        jwt().jwt(builder -> builder.subject(USER_ID))
                                                .authorities(
                                                        Collections.singletonList(
                                                                new SimpleGrantedAuthority(
                                                                        "ROLE_user"))))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(relationService);
    }

    @Test
    void addFriend_AlreadyFriend() throws Exception {
        when(relationService.addFriend(1L, 2L))
                .thenReturn(
                        ResponseEntity.badRequest()
                                .body(
                                        new CommonResponse(
                                                400, "Target user is already your friend")));

        AddFriendRequest request = new AddFriendRequest();
        request.setTargetUserId(TARGET_USER_ID);

        mockMvc.perform(
                        post("/api/users/me/friends")
                                .with(
                                        jwt().jwt(builder -> builder.subject(USER_ID))
                                                .authorities(
                                                        Collections.singletonList(
                                                                new SimpleGrantedAuthority(
                                                                        "ROLE_user"))))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("Target user is already your friend"));
    }

    @Test
    void removeFriend_Success() throws Exception {
        when(relationService.removeFriend(1L, 2L))
                .thenReturn(ResponseEntity.ok(new CommonResponse(200, "success")));

        mockMvc.perform(
                        delete("/api/users/me/friends/{friendId}", TARGET_USER_ID)
                                .with(
                                        jwt().jwt(builder -> builder.subject(USER_ID))
                                                .authorities(
                                                        Collections.singletonList(
                                                                new SimpleGrantedAuthority(
                                                                        "ROLE_user")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(relationService).removeFriend(1L, 2L);
    }

    @Test
    void removeFriend_NotFriend() throws Exception {
        when(relationService.removeFriend(1L, 2L))
                .thenReturn(
                        ResponseEntity.badRequest()
                                .body(new CommonResponse(400, "Target user is not your friend")));

        mockMvc.perform(
                        delete("/api/users/me/friends/{friendId}", TARGET_USER_ID)
                                .with(
                                        jwt().jwt(builder -> builder.subject(USER_ID))
                                                .authorities(
                                                        Collections.singletonList(
                                                                new SimpleGrantedAuthority(
                                                                        "ROLE_user")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void updateFriend_Success() throws Exception {
        when(relationService.updateFriendRemarkName(1L, 2L, "NewRemark"))
                .thenReturn(ResponseEntity.ok(new CommonResponse(200, "success")));

        UpdateFriendRequest request = new UpdateFriendRequest();
        request.setRemarkName("NewRemark");

        mockMvc.perform(
                        patch("/api/users/me/friends/{friendId}", TARGET_USER_ID)
                                .with(
                                        jwt().jwt(builder -> builder.subject(USER_ID))
                                                .authorities(
                                                        Collections.singletonList(
                                                                new SimpleGrantedAuthority(
                                                                        "ROLE_user"))))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(relationService).updateFriendRemarkName(1L, 2L, "NewRemark");
    }

    // ==================== Blocked Users Tests ====================

    @Test
    void blockUser_Success() throws Exception {
        when(relationService.addBlockedUser(1L, 2L))
                .thenReturn(ResponseEntity.ok(new CommonResponse(200, "success")));

        BlockUserRequest request = new BlockUserRequest();
        request.setTargetUserId(TARGET_USER_ID);

        mockMvc.perform(
                        post("/api/users/me/blocked-users")
                                .with(
                                        jwt().jwt(builder -> builder.subject(USER_ID))
                                                .authorities(
                                                        Collections.singletonList(
                                                                new SimpleGrantedAuthority(
                                                                        "ROLE_user"))))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(relationService).addBlockedUser(1L, 2L);
    }

    @Test
    void unblockUser_Success() throws Exception {
        when(relationService.removeBlockedUser(1L, 2L))
                .thenReturn(ResponseEntity.ok(new CommonResponse(200, "success")));

        mockMvc.perform(
                        delete("/api/users/me/blocked-users/{userId}", TARGET_USER_ID)
                                .with(
                                        jwt().jwt(builder -> builder.subject(USER_ID))
                                                .authorities(
                                                        Collections.singletonList(
                                                                new SimpleGrantedAuthority(
                                                                        "ROLE_user")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(relationService).removeBlockedUser(1L, 2L);
    }

    @Test
    void hideBlockedUser_Success() throws Exception {
        when(relationService.hideBlockedUser(1L, 2L))
                .thenReturn(ResponseEntity.ok(new CommonResponse(200, "success")));

        UpdateBlockedUserRequest request = new UpdateBlockedUserRequest();
        request.setHidden(true);

        mockMvc.perform(
                        patch("/api/users/me/blocked-users/{userId}", TARGET_USER_ID)
                                .with(
                                        jwt().jwt(builder -> builder.subject(USER_ID))
                                                .authorities(
                                                        Collections.singletonList(
                                                                new SimpleGrantedAuthority(
                                                                        "ROLE_user"))))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(relationService).hideBlockedUser(1L, 2L);
    }

    // ==================== Conversation Tests ====================

    @Test
    void getConversations_Success() throws Exception {
        List<ConversationItem> conversations = new ArrayList<>();
        ConversationsData data = new ConversationsData(conversations, null, false, null);
        when(conversationService.getConversations(1L, null, 50))
                .thenReturn(ResponseEntity.ok(new ConversationsResponse(200, "success", data)));

        mockMvc.perform(
                        get("/api/users/me/conversations")
                                .with(
                                        jwt().jwt(builder -> builder.subject(USER_ID))
                                                .authorities(
                                                        Collections.singletonList(
                                                                new SimpleGrantedAuthority(
                                                                        "ROLE_user")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.conversations").isArray());

        verify(conversationService).getConversations(1L, null, 50);
    }

    @Test
    void syncConversations_Success() throws Exception {
        List<ConversationChange> changes = new ArrayList<>();
        ConversationSyncData data = new ConversationSyncData(false, "v2", changes);
        when(conversationService.syncConversations(1L, "v1"))
                .thenReturn(ResponseEntity.ok(new ConversationSyncResponse(200, "success", data)));

        mockMvc.perform(
                        get("/api/users/me/conversations/sync")
                                .param("version", "v1")
                                .with(
                                        jwt().jwt(builder -> builder.subject(USER_ID))
                                                .authorities(
                                                        Collections.singletonList(
                                                                new SimpleGrantedAuthority(
                                                                        "ROLE_user")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.fullSync").value(false));

        verify(conversationService).syncConversations(1L, "v1");
    }

    @Test
    void getConversationSummaries_Success() throws Exception {
        List<String> conversationIds = List.of("conv1", "conv2");
        List<ConversationSummary> summaries = new ArrayList<>();
        ConversationSummaryData summaryData = new ConversationSummaryData(summaries);
        when(conversationService.getConversationSummaries(1L, conversationIds))
                .thenReturn(
                        ResponseEntity.ok(
                                new ConversationSummaryResponse(200, "success", summaryData)));

        ConversationSummaryRequest request = new ConversationSummaryRequest();
        request.setConversationIds(conversationIds);

        mockMvc.perform(
                        post("/api/users/me/conversations/summary")
                                .with(
                                        jwt().jwt(builder -> builder.subject(USER_ID))
                                                .authorities(
                                                        Collections.singletonList(
                                                                new SimpleGrantedAuthority(
                                                                        "ROLE_user"))))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(conversationService).getConversationSummaries(1L, conversationIds);
    }

    // ==================== Message Tests ====================

    @Test
    void sendMessage_Success() throws Exception {
        MessageResponseData messageData = new MessageResponseData("msg1");
        when(messageService.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(ResponseEntity.ok(new MessageResponse(200, "success", messageData)));

        SendMessageRequest request = new SendMessageRequest();
        request.setSenderId(USER_ID);
        request.setConversationId("conv1");
        request.setTextContent("Hello");

        mockMvc.perform(
                        post("/api/users/me/conversations/messages")
                                .with(
                                        jwt().jwt(builder -> builder.subject(USER_ID))
                                                .authorities(
                                                        Collections.singletonList(
                                                                new SimpleGrantedAuthority(
                                                                        "ROLE_user"))))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(messageService).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void sendMessage_SenderIdMismatch() throws Exception {
        SendMessageRequest request = new SendMessageRequest();
        request.setSenderId("999"); // Different from JWT subject
        request.setConversationId("conv1");
        request.setTextContent("Hello");

        mockMvc.perform(
                        post("/api/users/me/conversations/messages")
                                .with(
                                        jwt().jwt(builder -> builder.subject(USER_ID))
                                                .authorities(
                                                        Collections.singletonList(
                                                                new SimpleGrantedAuthority(
                                                                        "ROLE_user"))))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("Forbidden: Sender ID mismatch"));

        verifyNoInteractions(messageService);
    }

    @Test
    void readMessages_WithBeforeMessageId_Success() throws Exception {
        List<MessageItem> messages = new ArrayList<>();
        ReadMessagesData data = new ReadMessagesData(messages);
        when(messageService.readMessages("conv1", 100L, 50))
                .thenReturn(ResponseEntity.ok(new ReadMessagesResponse(200, "success", data)));

        mockMvc.perform(
                        get("/api/users/me/conversations/{conversationId}/messages", "conv1")
                                .param("beforeMessageId", "100")
                                .with(
                                        jwt().jwt(builder -> builder.subject(USER_ID))
                                                .authorities(
                                                        Collections.singletonList(
                                                                new SimpleGrantedAuthority(
                                                                        "ROLE_user")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(messageService).readMessages("conv1", 100L, 50);
    }

    @Test
    void readMessages_WithAfterMessageId_Success() throws Exception {
        List<MessageItem> messages = new ArrayList<>();
        ReadMessagesData data = new ReadMessagesData(messages);
        when(messageService.readMessagesAfter("conv1", 100L, 50))
                .thenReturn(ResponseEntity.ok(new ReadMessagesResponse(200, "success", data)));

        mockMvc.perform(
                        get("/api/users/me/conversations/{conversationId}/messages", "conv1")
                                .param("afterMessageId", "100")
                                .with(
                                        jwt().jwt(builder -> builder.subject(USER_ID))
                                                .authorities(
                                                        Collections.singletonList(
                                                                new SimpleGrantedAuthority(
                                                                        "ROLE_user")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(messageService).readMessagesAfter("conv1", 100L, 50);
    }

    @Test
    void readMessages_MissingBothParams_ReturnsBadRequest() throws Exception {
        mockMvc.perform(
                        get("/api/users/me/conversations/{conversationId}/messages", "conv1")
                                .with(
                                        jwt().jwt(builder -> builder.subject(USER_ID))
                                                .authorities(
                                                        Collections.singletonList(
                                                                new SimpleGrantedAuthority(
                                                                        "ROLE_user")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Exactly one of beforeMessageId or afterMessageId must be provided"));

        verifyNoInteractions(messageService);
    }

    @Test
    void readMessages_BothParamsProvided_ReturnsBadRequest() throws Exception {
        mockMvc.perform(
                        get("/api/users/me/conversations/{conversationId}/messages", "conv1")
                                .param("beforeMessageId", "100")
                                .param("afterMessageId", "50")
                                .with(
                                        jwt().jwt(builder -> builder.subject(USER_ID))
                                                .authorities(
                                                        Collections.singletonList(
                                                                new SimpleGrantedAuthority(
                                                                        "ROLE_user")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Exactly one of beforeMessageId or afterMessageId must be provided"));

        verifyNoInteractions(messageService);
    }

    @Test
    void readMessages_InvalidMessageId() throws Exception {
        mockMvc.perform(
                        get("/api/users/me/conversations/{conversationId}/messages", "conv1")
                                .param("beforeMessageId", "invalid")
                                .with(
                                        jwt().jwt(builder -> builder.subject(USER_ID))
                                                .authorities(
                                                        Collections.singletonList(
                                                                new SimpleGrantedAuthority(
                                                                        "ROLE_user")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(messageService);
    }

    @Test
    void updateReadPosition_Success() throws Exception {
        when(messageService.markLastReadMessageId("conv1", 100L, 1L))
                .thenReturn(ResponseEntity.ok(new CommonResponse(200, "success")));

        UpdateReadPositionRequest request = new UpdateReadPositionRequest();
        request.setMessageId("100");

        mockMvc.perform(
                        patch("/api/users/me/conversations/{conversationId}/read-position", "conv1")
                                .with(
                                        jwt().jwt(builder -> builder.subject(USER_ID))
                                                .authorities(
                                                        Collections.singletonList(
                                                                new SimpleGrantedAuthority(
                                                                        "ROLE_user"))))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(messageService).markLastReadMessageId("conv1", 100L, 1L);
    }

    // ==================== User Groups Tests ====================

    @Test
    void getUserGroups_Success() throws Exception {
        List<UserGroupData> groups = new ArrayList<>();
        UserGroupsData data = new UserGroupsData(groups, null, false, null);
        when(groupService.getUserGroups(1L, null, 50))
                .thenReturn(ResponseEntity.ok(new GetUserGroupsResponse(200, "success", data)));

        mockMvc.perform(
                        get("/api/users/me/groups")
                                .with(
                                        jwt().jwt(builder -> builder.subject(USER_ID))
                                                .authorities(
                                                        Collections.singletonList(
                                                                new SimpleGrantedAuthority(
                                                                        "ROLE_user")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.groups").isArray());

        verify(groupService).getUserGroups(1L, null, 50);
    }

    @Test
    void syncUserGroups_Success() throws Exception {
        List<UserGroupChange> changes = new ArrayList<>();
        UserGroupSyncData data = new UserGroupSyncData(false, "v2", changes);
        when(groupService.syncUserGroups(1L, "v1"))
                .thenReturn(ResponseEntity.ok(new UserGroupSyncResponse(200, "success", data)));

        mockMvc.perform(
                        get("/api/users/me/groups/sync")
                                .param("version", "v1")
                                .with(
                                        jwt().jwt(builder -> builder.subject(USER_ID))
                                                .authorities(
                                                        Collections.singletonList(
                                                                new SimpleGrantedAuthority(
                                                                        "ROLE_user")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.fullSync").value(false));

        verify(groupService).syncUserGroups(1L, "v1");
    }

    // ==================== Authorization Tests ====================

    @Test
    void allEndpoints_Unauthorized() throws Exception {
        // Test various endpoints without authentication
        mockMvc.perform(get("/api/users/me/profile")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/me/relations")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/me/conversations")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/me/groups")).andExpect(status().isUnauthorized());
        mockMvc.perform(
                        post("/api/users/me/friends")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
