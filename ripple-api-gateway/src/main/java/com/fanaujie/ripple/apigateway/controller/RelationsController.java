package com.fanaujie.ripple.apigateway.controller;

import com.fanaujie.ripple.apigateway.dto.*;
import com.fanaujie.ripple.apigateway.service.RelationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(
        name = "Friend Relationship Management",
        description =
                "APIs for managing friend relationships including adding, removing, and blocking users")
@SecurityRequirement(name = "bearerAuth")
public class RelationsController {

    private final RelationService relationService;

    @PostMapping(value = "/friends", consumes = "application/json", produces = "application/json")
    @Operation(summary = "Add friend", description = "Send a friend request to the specified user")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully added friend",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = CommonResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description =
                                "Invalid request parameters or friend relationship already exists",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = CommonResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized access",
                        content = @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = CommonResponse.class)))
            })
    public ResponseEntity<CommonResponse> addFriend(
            @Parameter(description = "Add friend request") @Valid @RequestBody
                    AddFriendRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        long currentUserId = Long.parseLong(jwt.getSubject());
        return relationService.addFriend(currentUserId, request.getTargetUserId());
    }

    @GetMapping(value = "/friends-with-blocked", produces = "application/json")
    @Operation(
            summary = "Get friends and blocked users list",
            description = "Retrieve all friends and blocked users for the current user")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully retrieved friends list",
                        content = @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = UsersResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized access",
                        content = @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = CommonResponse.class)))
            })
    public ResponseEntity<UsersResponse> getFriends(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        long currentUserId = Long.parseLong(jwt.getSubject());
        return relationService.getFriendsWithBlockedLists(currentUserId);
    }

    @PutMapping(value = "/friends/{friendId}/display-name", consumes = "application/json", produces = "application/json")
    @Operation(
            summary = "Update friend display name",
            description = "Update the display name of the specified friend in the friends list")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully updated friend display name",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = CommonResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description =
                                "Invalid request parameters or friend relationship does not exist",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = CommonResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized access",
                        content = @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = CommonResponse.class)))
            })
    public ResponseEntity<CommonResponse> updateFriendDisplayName(
            @Parameter(description = "Friend ID") @PathVariable("friendId") long friendId,
            @Parameter(description = "Display name update request") @Valid @RequestBody
                    UpdateFriendDisplayNameRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        long currentUserId = Long.parseLong(jwt.getSubject());
        return relationService.updateFriendDisplayName(
                currentUserId, friendId, request.getDisplayName());
    }

    @DeleteMapping(value = "/friends/{friendId}", produces = "application/json")
    @Operation(
            summary = "Remove friend",
            description = "Remove the specified friend from the friends list")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully removed friend",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = CommonResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Friend relationship does not exist",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = CommonResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized access",
                        content = @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = CommonResponse.class)))
            })
    public ResponseEntity<CommonResponse> removeFriend(
            @Parameter(description = "Friend ID") @PathVariable("friendId") long friendId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        long currentUserId = Long.parseLong(jwt.getSubject());
        return relationService.removeFriend(currentUserId, friendId);
    }

    @PostMapping(value = "/blocked-users", consumes = "application/json", produces = "application/json")
    @Operation(
            summary = "Block user",
            description = "Add the specified user to the blocked users list")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully blocked user",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = CommonResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Invalid request parameters or user already blocked",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = CommonResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized access",
                        content = @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = CommonResponse.class)))
            })
    public ResponseEntity<CommonResponse> blockUser(
            @Parameter(description = "Block user request") @Valid @RequestBody
                    BlockUserRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        long currentUserId = Long.parseLong(jwt.getSubject());
        return relationService.updateBlockedStatus(currentUserId, request.getTargetUserId(), true);
    }

    @DeleteMapping(value = "/blocked-users/{targetUserId}", produces = "application/json")
    @Operation(
            summary = "Unblock user",
            description = "Remove the specified user from the blocked users list")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully unblocked user",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = CommonResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "User is not blocked",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = CommonResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized access",
                        content = @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = CommonResponse.class)))
            })
    public ResponseEntity<CommonResponse> unblockUser(
            @Parameter(description = "Target user ID") @PathVariable("targetUserId")
                    long targetUserId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        long currentUserId = Long.parseLong(jwt.getSubject());
        return relationService.updateBlockedStatus(currentUserId, targetUserId, false);
    }

    @PatchMapping(value = "/blocked-users/{targetUserId}/hide", produces = "application/json")
    @Operation(
            summary = "Hide blocked user",
            description = "Hide the specified blocked user from the blocked users list")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully hide blocked user",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = CommonResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "User is not blocked or already hidden",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = CommonResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized access",
                        content = @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = CommonResponse.class)))
            })
    public ResponseEntity<CommonResponse> hideBlockedUser(
            @Parameter(description = "Target user ID") @PathVariable("targetUserId")
                    long targetUserId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        long currentUserId = Long.parseLong(jwt.getSubject());
        return relationService.hideBlockedUser(currentUserId, targetUserId);
    }
}
