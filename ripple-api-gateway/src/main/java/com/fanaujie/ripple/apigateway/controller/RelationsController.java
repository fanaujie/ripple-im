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
        name = "Relationship Management",
        description =
                "APIs for managing relationships including adding, removing friend, and blocking users")
@SecurityRequirement(name = "bearerAuth")
public class RelationsController {

    private final RelationService relationService;

    @PostMapping(value = "/friends", consumes = "application/json", produces = "application/json")
    @Operation(summary = "Add friend", description = "Add a user to the friends list")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully added relation",
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
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = CommonResponse.class)))
            })
    public ResponseEntity<CommonResponse> addFriend(
            @Parameter(description = "Add friend request") @Valid @RequestBody
                    AddFriendRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        try {
            long currentUserId = Long.parseLong(jwt.getSubject());
            return relationService.addFriend(
                    currentUserId, Long.parseLong(request.getTargetUserId()));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Invalid user ID format"));
        }
    }

    @GetMapping(produces = "application/json")
    @Operation(
            summary = "Get user relations with pagination",
            description =
                    "Retrieve all relations (friends and blocked users) for the current user with pagination support")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully retrieved user relations",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                UserRelationsResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized access",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = CommonResponse.class)))
            })
    public ResponseEntity<UserRelationsResponse> getRelations(
            @Parameter(description = "Next page token for pagination (optional)")
                    @RequestParam(required = false, name = "nextPageToken")
                    String nextPageToken,
            @Parameter(description = "Number of relations per page (default: 50, max: 100)")
                    @RequestParam(defaultValue = "50", name = "pageSize")
                    int pageSize,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        long currentUserId = Long.parseLong(jwt.getSubject());
        return relationService.getRelations(currentUserId, nextPageToken, pageSize);
    }

    @PutMapping(
            value = "/friends/{friendId}/remark-name",
            consumes = "application/json",
            produces = "application/json")
    @Operation(
            summary = "Update friend remark name",
            description = "Update the remark name of the specified friend in the friends list")
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
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = CommonResponse.class)))
            })
    public ResponseEntity<CommonResponse> updateFriendRemarkName(
            @Parameter(description = "Friend ID") @PathVariable("friendId") String friendId,
            @Parameter(description = "Remark name update request") @Valid @RequestBody
                    UpdateFriendRemarkNameRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        try {
            long currentUserId = Long.parseLong(jwt.getSubject());
            return relationService.updateFriendRemarkName(
                    currentUserId, Long.parseLong(friendId), request.getRemarkName());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Invalid user ID format"));
        }
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
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = CommonResponse.class)))
            })
    public ResponseEntity<CommonResponse> removeFriend(
            @Parameter(description = "Friend ID") @PathVariable("friendId") String friendId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        try {
            long currentUserId = Long.parseLong(jwt.getSubject());
            return relationService.removeFriend(currentUserId, Long.parseLong(friendId));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Invalid user ID format"));
        }
    }

    @PostMapping(
            value = "/blocked-users",
            consumes = "application/json",
            produces = "application/json")
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
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = CommonResponse.class)))
            })
    public ResponseEntity<CommonResponse> blockUser(
            @Parameter(description = "Block user request") @Valid @RequestBody
                    BlockUserRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        try {
            long currentUserId = Long.parseLong(jwt.getSubject());
            return relationService.addBlockedUser(
                    currentUserId, Long.parseLong(request.getTargetUserId()));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Invalid user ID format"));
        }
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
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = CommonResponse.class)))
            })
    public ResponseEntity<CommonResponse> unblockUser(
            @Parameter(description = "Target user ID") @PathVariable("targetUserId")
                    String targetUserId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        try {
            long currentUserId = Long.parseLong(jwt.getSubject());
            return relationService.removeBlockedUser(currentUserId, Long.parseLong(targetUserId));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Invalid user ID format"));
        }
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
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = CommonResponse.class)))
            })
    public ResponseEntity<CommonResponse> hideBlockedUser(
            @Parameter(description = "Target user ID") @PathVariable("targetUserId")
                    String targetUserId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        try {
            long currentUserId = Long.parseLong(jwt.getSubject());
            return relationService.hideBlockedUser(currentUserId, Long.parseLong(targetUserId));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Invalid user ID format"));
        }
    }

    @GetMapping(value = "/sync", produces = "application/json")
    @Operation(
            summary = "Sync relations",
            description =
                    "Synchronize relation list changes. Returns full sync flag and incremental changes if available.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully retrieved sync data",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                RelationSyncResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized access",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = CommonResponse.class)))
            })
    public ResponseEntity<RelationSyncResponse> syncRelations(
            @Parameter(description = "Client's last known version (optional)")
                    @RequestParam(required = false, name = "version")
                    String version,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        long currentUserId = Long.parseLong(jwt.getSubject());
        return relationService.syncRelations(currentUserId, version);
    }

    @GetMapping(value = "/version", produces = "application/json")
    @Operation(
            summary = "Get latest relation version",
            description =
                    "Retrieve the latest version timestamp for the current user's relation changes")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully retrieved latest version",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                RelationVersionResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized access",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = CommonResponse.class)))
            })
    public ResponseEntity<RelationVersionResponse> getLatestVersion(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        long currentUserId = Long.parseLong(jwt.getSubject());
        return relationService.getLatestVersion(currentUserId);
    }
}
