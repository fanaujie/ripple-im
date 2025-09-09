package com.fanaujie.ripple.apigateway.controller;

import com.fanaujie.ripple.apigateway.config.SecurityConfig;
import com.fanaujie.ripple.apigateway.dto.*;
import com.fanaujie.ripple.apigateway.service.RelationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ContextConfiguration(classes = {SecurityConfig.class, RelationsController.class})
@WebMvcTest
@TestPropertySource(
        properties = {
            "oauth2.jwk.secret=dGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQ="
        })
class RelationsControllerTest {

    private MockMvc mockMvc;

    @Autowired private WebApplicationContext context;

    @MockitoBean private RelationService relationService;

    @Autowired private ObjectMapper objectMapper;

    private static final long TEST_USER_ID = 1L;
    private static final long TARGET_USER_ID = 2L;
    private static final long FRIEND_ID = 3L;
    private static final String TEST_DISPLAY_NAME = "Test Friend";

    private RequestPostProcessor authenticatedUser() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_user"))
                .jwt(jwt -> jwt.header("alg", "HS256").claim("sub", String.valueOf(TEST_USER_ID)));
    }

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void addFriend_Success() throws Exception {
        AddFriendRequest request = new AddFriendRequest(TARGET_USER_ID);
        CommonResponse response = new CommonResponse(200, "Friend request sent successfully");

        when(relationService.addFriend(TEST_USER_ID, TARGET_USER_ID))
                .thenReturn(ResponseEntity.ok(response));

        mockMvc.perform(
                        post("/api/relations/friends")
                                .with(authenticatedUser())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Friend request sent successfully"));

        verify(relationService).addFriend(TEST_USER_ID, TARGET_USER_ID);
    }

    @Test
    void addFriend_UserNotFound() throws Exception {
        AddFriendRequest request = new AddFriendRequest(TARGET_USER_ID);
        CommonResponse response = new CommonResponse(404, "User not found");

        when(relationService.addFriend(TEST_USER_ID, TARGET_USER_ID))
                .thenReturn(ResponseEntity.status(HttpStatus.NOT_FOUND).body(response));

        mockMvc.perform(
                        post("/api/relations/friends")
                                .with(authenticatedUser())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("User not found"));

        verify(relationService).addFriend(TEST_USER_ID, TARGET_USER_ID);
    }

    @Test
    void addFriend_Unauthorized() throws Exception {
        AddFriendRequest request = new AddFriendRequest(TARGET_USER_ID);

        mockMvc.perform(
                        post("/api/relations/friends")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getFriends_Success() throws Exception {
        UserData userData = new UserData();
        UsersResponse response = new UsersResponse(200, "success", userData);

        when(relationService.getFriendsWithBlockedLists(TEST_USER_ID))
                .thenReturn(ResponseEntity.ok(response));

        mockMvc.perform(
                        get("/api/relations/friends-with-blocked")
                                .with(authenticatedUser())
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"));

        verify(relationService).getFriendsWithBlockedLists(TEST_USER_ID);
    }

    @Test
    void getFriends_Unauthorized() throws Exception {
        mockMvc.perform(
                        get("/api/relations/friends-with-blocked")
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateFriendDisplayName_Success() throws Exception {
        UpdateFriendDisplayNameRequest request =
                new UpdateFriendDisplayNameRequest(TEST_DISPLAY_NAME);
        CommonResponse response = new CommonResponse(200, "Display name updated successfully");

        when(relationService.updateFriendDisplayName(TEST_USER_ID, FRIEND_ID, TEST_DISPLAY_NAME))
                .thenReturn(ResponseEntity.ok(response));

        mockMvc.perform(
                        put("/api/relations/friends/{friendId}/display-name", FRIEND_ID)
                                .with(authenticatedUser())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Display name updated successfully"));

        verify(relationService).updateFriendDisplayName(TEST_USER_ID, FRIEND_ID, TEST_DISPLAY_NAME);
    }

    @Test
    void updateFriendDisplayName_ValidationError_BlankDisplayName() throws Exception {
        UpdateFriendDisplayNameRequest request = new UpdateFriendDisplayNameRequest("");

        mockMvc.perform(
                        put("/api/relations/friends/{friendId}/display-name", FRIEND_ID)
                                .with(authenticatedUser())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateFriendDisplayName_ValidationError_TooLongDisplayName() throws Exception {
        String longDisplayName = "A".repeat(51);
        UpdateFriendDisplayNameRequest request =
                new UpdateFriendDisplayNameRequest(longDisplayName);

        mockMvc.perform(
                        put("/api/relations/friends/{friendId}/display-name", FRIEND_ID)
                                .with(authenticatedUser())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateFriendDisplayName_Unauthorized() throws Exception {
        UpdateFriendDisplayNameRequest request =
                new UpdateFriendDisplayNameRequest(TEST_DISPLAY_NAME);

        mockMvc.perform(
                        put("/api/relations/friends/{friendId}/display-name", FRIEND_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void removeFriend_Success() throws Exception {
        CommonResponse response = new CommonResponse(200, "Friend removed successfully");

        when(relationService.removeFriend(TEST_USER_ID, FRIEND_ID))
                .thenReturn(ResponseEntity.ok(response));

        mockMvc.perform(
                        delete("/api/relations/friends/{friendId}", FRIEND_ID)
                                .with(authenticatedUser())
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Friend removed successfully"));

        verify(relationService).removeFriend(TEST_USER_ID, FRIEND_ID);
    }

    @Test
    void removeFriend_FriendNotFound() throws Exception {
        CommonResponse response = new CommonResponse(404, "Friend not found");

        when(relationService.removeFriend(TEST_USER_ID, FRIEND_ID))
                .thenReturn(ResponseEntity.status(HttpStatus.NOT_FOUND).body(response));

        mockMvc.perform(
                        delete("/api/relations/friends/{friendId}", FRIEND_ID)
                                .with(authenticatedUser())
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("Friend not found"));

        verify(relationService).removeFriend(TEST_USER_ID, FRIEND_ID);
    }

    @Test
    void removeFriend_Unauthorized() throws Exception {
        mockMvc.perform(
                        delete("/api/relations/friends/{friendId}", FRIEND_ID)
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blockUser_Success() throws Exception {
        BlockUserRequest request = new BlockUserRequest(TARGET_USER_ID);
        CommonResponse response = new CommonResponse(200, "User blocked successfully");

        when(relationService.updateBlockedStatus(TEST_USER_ID, TARGET_USER_ID, true))
                .thenReturn(ResponseEntity.ok(response));

        mockMvc.perform(
                        post("/api/relations/blocked-users")
                                .with(authenticatedUser())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("User blocked successfully"));

        verify(relationService).updateBlockedStatus(TEST_USER_ID, TARGET_USER_ID, true);
    }

    @Test
    void blockUser_UserNotFound() throws Exception {
        BlockUserRequest request = new BlockUserRequest(TARGET_USER_ID);
        CommonResponse response = new CommonResponse(404, "User not found");

        when(relationService.updateBlockedStatus(TEST_USER_ID, TARGET_USER_ID, true))
                .thenReturn(ResponseEntity.status(HttpStatus.NOT_FOUND).body(response));

        mockMvc.perform(
                        post("/api/relations/blocked-users")
                                .with(authenticatedUser())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("User not found"));

        verify(relationService).updateBlockedStatus(TEST_USER_ID, TARGET_USER_ID, true);
    }

    @Test
    void blockUser_Unauthorized() throws Exception {
        BlockUserRequest request = new BlockUserRequest(TARGET_USER_ID);

        mockMvc.perform(
                        post("/api/relations/blocked-users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unblockUser_Success() throws Exception {
        CommonResponse response = new CommonResponse(200, "User unblocked successfully");

        when(relationService.updateBlockedStatus(TEST_USER_ID, TARGET_USER_ID, false))
                .thenReturn(ResponseEntity.ok(response));

        mockMvc.perform(
                        delete("/api/relations/blocked-users/{targetUserId}", TARGET_USER_ID)
                                .with(authenticatedUser())
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("User unblocked successfully"));

        verify(relationService).updateBlockedStatus(TEST_USER_ID, TARGET_USER_ID, false);
    }

    @Test
    void unblockUser_UserNotFound() throws Exception {
        CommonResponse response = new CommonResponse(404, "User not found");

        when(relationService.updateBlockedStatus(TEST_USER_ID, TARGET_USER_ID, false))
                .thenReturn(ResponseEntity.status(HttpStatus.NOT_FOUND).body(response));

        mockMvc.perform(
                        delete("/api/relations/blocked-users/{targetUserId}", TARGET_USER_ID)
                                .with(authenticatedUser())
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("User not found"));

        verify(relationService).updateBlockedStatus(TEST_USER_ID, TARGET_USER_ID, false);
    }

    @Test
    void unblockUser_Unauthorized() throws Exception {
        mockMvc.perform(
                        delete("/api/relations/blocked-users/{targetUserId}", TARGET_USER_ID)
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void hideBlockedUser_Success() throws Exception {
        CommonResponse response = new CommonResponse(200, "Blocked user hidden successfully");

        when(relationService.hideBlockedUser(TEST_USER_ID, TARGET_USER_ID))
                .thenReturn(ResponseEntity.ok(response));

        mockMvc.perform(
                        patch("/api/relations/blocked-users/{targetUserId}/hide", TARGET_USER_ID)
                                .with(authenticatedUser())
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Blocked user hidden successfully"));

        verify(relationService).hideBlockedUser(TEST_USER_ID, TARGET_USER_ID);
    }

    @Test
    void hideBlockedUser_UserNotFound() throws Exception {
        CommonResponse response = new CommonResponse(404, "User not found");

        when(relationService.hideBlockedUser(TEST_USER_ID, TARGET_USER_ID))
                .thenReturn(ResponseEntity.status(HttpStatus.NOT_FOUND).body(response));

        mockMvc.perform(
                        patch("/api/relations/blocked-users/{targetUserId}/hide", TARGET_USER_ID)
                                .with(authenticatedUser())
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("User not found"));

        verify(relationService).hideBlockedUser(TEST_USER_ID, TARGET_USER_ID);
    }

    @Test
    void hideBlockedUser_Unauthorized() throws Exception {
        mockMvc.perform(
                        patch("/api/relations/blocked-users/{targetUserId}/hide", TARGET_USER_ID)
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
