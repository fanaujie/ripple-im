package com.fanaujie.ripple.apigateway.controller;

import com.fanaujie.ripple.apigateway.dto.*;
import com.fanaujie.ripple.apigateway.service.RelationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/relations")
@RequiredArgsConstructor
@Slf4j
public class RelationsController {

    private final RelationService relationService;

    @PostMapping("/friends")
    public ResponseEntity<CommonResponse> addFriend(
            @Valid @RequestBody AddFriendRequest request, @AuthenticationPrincipal Jwt jwt) {
        long currentUserId = Long.parseLong(jwt.getSubject());
        return relationService.addFriend(currentUserId, request.getTargetUserId());
    }

    @GetMapping("/friends-with-blocked")
    public ResponseEntity<UsersResponse> getFriends(@AuthenticationPrincipal Jwt jwt) {
        long currentUserId = Long.parseLong(jwt.getSubject());
        return relationService.getFriendsWithBlockedLists(currentUserId);
    }

    @PutMapping("/friends/{friendId}/display-name")
    public ResponseEntity<CommonResponse> updateFriendDisplayName(
            @PathVariable("friendId") long friendId,
            @Valid @RequestBody UpdateFriendDisplayNameRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        long currentUserId = Long.parseLong(jwt.getSubject());
        return relationService.updateFriendDisplayName(
                currentUserId, friendId, request.getDisplayName());
    }

    @DeleteMapping("/friends/{friendId}")
    public ResponseEntity<CommonResponse> removeFriend(
            @PathVariable("friendId") long friendId, @AuthenticationPrincipal Jwt jwt) {
        long currentUserId = Long.parseLong(jwt.getSubject());
        return relationService.removeFriend(currentUserId, friendId);
    }

    @PostMapping("/blocked-users")
    public ResponseEntity<CommonResponse> blockUser(
            @Valid @RequestBody BlockUserRequest request, @AuthenticationPrincipal Jwt jwt) {
        long currentUserId = Long.parseLong(jwt.getSubject());
        return relationService.updateBlockedStatus(currentUserId, request.getTargetUserId(), true);
    }

    @DeleteMapping("/blocked-users/{targetUserId}")
    public ResponseEntity<CommonResponse> unblockUser(
            @PathVariable("targetUserId") long targetUserId, @AuthenticationPrincipal Jwt jwt) {
        long currentUserId = Long.parseLong(jwt.getSubject());
        return relationService.updateBlockedStatus(currentUserId, targetUserId, false);
    }

    @PatchMapping("/blocked-users/{targetUserId}/hide")
    public ResponseEntity<CommonResponse> hideBlockedUser(
            @PathVariable("targetUserId") long targetUserId, @AuthenticationPrincipal Jwt jwt) {
        long currentUserId = Long.parseLong(jwt.getSubject());
        return relationService.hideBlockedUser(currentUserId, targetUserId);
    }
}
