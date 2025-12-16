package com.fanaujie.ripple.apigateway.controller;

import com.fanaujie.ripple.apigateway.dto.*;
import com.fanaujie.ripple.apigateway.service.ConversationService;
import com.fanaujie.ripple.apigateway.service.GroupService;
import com.fanaujie.ripple.apigateway.service.MessageService;
import com.fanaujie.ripple.apigateway.service.RelationService;
import com.fanaujie.ripple.apigateway.service.UserProfileService;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
@Tag(
        name = "Users",
        description = "User-related APIs including profile, conversations, relations, and groups")
@SecurityRequirement(name = "Bearer Authentication")
public class UsersController {

    private final MessageService messageService;
    private final ConversationService conversationService;
    private final UserProfileService userProfileService;
    private final RelationService relationService;
    private final GroupService groupService;

    // ==================== Conversations ====================

    @GetMapping(value = "/me/conversations", produces = "application/json")
    @Operation(
            summary = "Get conversations",
            description = "Retrieve a paginated list of conversations for the current user")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully retrieved conversations",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                ConversationsResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized access",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = CommonResponse.class)))
            })
    public ResponseEntity<ConversationsResponse> getConversations(
            @Parameter(description = "Next page token for pagination (optional)")
                    @RequestParam(required = false, name = "nextPageToken")
                    String nextPageToken,
            @Parameter(description = "Number of conversations per page (default: 50, max: 200)")
                    @RequestParam(defaultValue = "50", name = "pageSize")
                    int pageSize,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        long currentUserId = Long.parseLong(jwt.getSubject());
        return conversationService.getConversations(currentUserId, nextPageToken, pageSize);
    }

    @GetMapping(value = "/me/conversations/sync", produces = "application/json")
    @Operation(
            summary = "Sync conversations",
            description =
                    "Synchronize conversation list changes. Returns full sync flag and incremental changes if available.")
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
                                                                ConversationSyncResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized access",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = CommonResponse.class)))
            })
    public ResponseEntity<ConversationSyncResponse> syncConversations(
            @Parameter(description = "Client's last known version (optional)")
                    @RequestParam(required = false, name = "version")
                    String version,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        long currentUserId = Long.parseLong(jwt.getSubject());
        return conversationService.syncConversations(currentUserId, version);
    }

    @PostMapping(
            value = "/me/conversations/summary",
            consumes = "application/json",
            produces = "application/json")
    @Operation(
            summary = "Get conversation summaries",
            description =
                    "Retrieve summary information (last message and unread count) for specified conversations. "
                            + "Use this endpoint after incremental sync to get dynamic state.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully retrieved conversation summaries",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                ConversationSummaryResponse
                                                                        .class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Invalid request parameters",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                ConversationSummaryResponse
                                                                        .class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized access",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = CommonResponse.class)))
            })
    public ResponseEntity<ConversationSummaryResponse> getConversationSummaries(
            @Parameter(description = "Conversation IDs to retrieve summaries for")
                    @Valid
                    @RequestBody
                    ConversationSummaryRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        long currentUserId = Long.parseLong(jwt.getSubject());
        return conversationService.getConversationSummaries(
                currentUserId, request.getConversationIds());
    }

    @PostMapping("/me/conversations/messages")
    @Operation(
            summary = "Send message",
            description = "Send a text or file message to a conversation")
    public ResponseEntity<MessageResponse> sendMessage(
            @Parameter(description = "Conversation ID", required = true) @Valid @RequestBody
                    SendMessageRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        try {
            if (!request.getSenderId().equals(jwt.getSubject())) {
                return ResponseEntity.status(403)
                        .body(new MessageResponse(403, "Forbidden: Sender ID mismatch", null));
            }
            return messageService.sendMessage(request);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest()
                    .body(new MessageResponse(400, "Invalid user ID format", null));
        }
    }

    @GetMapping(
            value = "/me/conversations/{conversationId}/messages",
            produces = "application/json")
    @Operation(
            summary = "Read messages",
            description =
                    "Retrieve messages from a conversation before the specified message ID. Returns messages in descending order by message ID (newest to oldest relative to the anchor message).")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully retrieved messages",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                ReadMessagesResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized access",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = CommonResponse.class)))
            })
    public ResponseEntity<ReadMessagesResponse> readMessages(
            @Parameter(description = "Conversation ID", required = true)
                    @PathVariable("conversationId")
                    String conversationId,
            @Parameter(
                            description = "Message ID to read from (fetch messages before this ID)",
                            required = true)
                    @RequestParam(name = "messageId")
                    String messageId,
            @Parameter(description = "Number of messages to retrieve (default: 50, max: 200)")
                    @RequestParam(defaultValue = "50", name = "readSize")
                    int readSize,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        try {
            long currentUserId = Long.parseLong(jwt.getSubject());
            long messageIdLong = Long.parseLong(messageId);
            return messageService.readMessages(
                    conversationId, messageIdLong, readSize, currentUserId);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest()
                    .body(ReadMessagesResponse.error(400, "Invalid message ID or user ID format"));
        }
    }

    @PatchMapping("/me/conversations/{conversationId}/read-position")
    @Operation(
            summary = "Update read position",
            description = "Update the last read message position in a conversation")
    public ResponseEntity<CommonResponse> updateReadPosition(
            @Parameter(description = "Conversation ID", required = true)
                    @PathVariable("conversationId")
                    String conversationId,
            @Valid @RequestBody UpdateReadPositionRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        try {
            long currentUserId = Long.parseLong(jwt.getSubject());
            long messageIdLong = Long.parseLong(request.getMessageId());
            return messageService.markLastReadMessageId(
                    conversationId, messageIdLong, currentUserId);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Invalid message ID or user ID format"));
        }
    }

    // ==================== Profile ====================

    @GetMapping(value = "/me/profile", produces = "application/json")
    @Operation(
            summary = "Get current user's profile",
            description = "Retrieve profile information for the currently authenticated user")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully retrieved user profile",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                UserProfileResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized access",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = CommonResponse.class)))
            })
    public ResponseEntity<UserProfileResponse> getMyProfile(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        try {
            long currentUserId = Long.parseLong(jwt.getSubject());
            return userProfileService.getUserProfile(currentUserId);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest()
                    .body(new UserProfileResponse(400, "Invalid user ID format", null));
        }
    }

    @GetMapping(value = "/{userId}/profile", produces = "application/json")
    @Operation(
            summary = "Get user's profile",
            description = "Retrieve profile information for the specified user")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully retrieved user profile",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                UserProfileResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized access",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = CommonResponse.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "User profile not found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                UserProfileResponse.class)))
            })
    public ResponseEntity<UserProfileResponse> getUserProfile(
            @Parameter(description = "User ID to retrieve profile for", required = true)
                    @PathVariable("userId")
                    String userId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        try {
            long targetUserId = Long.parseLong(userId);
            return userProfileService.getUserProfile(targetUserId);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest()
                    .body(new UserProfileResponse(400, "Invalid user ID format", null));
        }
    }

    @PatchMapping(
            value = "/me/profile",
            consumes = "application/json",
            produces = "application/json")
    @Operation(
            summary = "Update user profile",
            description = "Update profile information for the currently authenticated user")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully updated profile",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = CommonResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Invalid request parameters",
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
    public ResponseEntity<CommonResponse> updateProfile(
            @Parameter(description = "Profile update request") @Valid @RequestBody
                    UpdateProfileRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        if (request.getNickname() != null) {
            return userProfileService.updateNickName(
                    Long.parseLong(jwt.getSubject()), request.getNickname());
        }
        return ResponseEntity.ok(new CommonResponse(200, "No fields to update"));
    }

    @DeleteMapping(value = "/me/avatar", produces = "application/json")
    @Operation(
            summary = "Delete user avatar",
            description = "Delete the avatar of the currently authenticated user")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully deleted avatar",
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
    public ResponseEntity<CommonResponse> deleteAvatar(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        userProfileService.deleteAvatar(Long.parseLong(jwt.getSubject()));
        return ResponseEntity.status(200).body(new CommonResponse(200, "success"));
    }

    // ==================== Relations ====================

    @GetMapping(value = "/me/relations", produces = "application/json")
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

    @GetMapping(value = "/me/relations/sync", produces = "application/json")
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

    // ==================== Friends ====================

    @PostMapping(
            value = "/me/friends",
            consumes = "application/json",
            produces = "application/json")
    @Operation(summary = "Add friend", description = "Add a user to the friends list")
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

    @DeleteMapping(value = "/me/friends/{friendId}", produces = "application/json")
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

    @PatchMapping(
            value = "/me/friends/{friendId}",
            consumes = "application/json",
            produces = "application/json")
    @Operation(
            summary = "Update friend",
            description = "Update the attributes of the specified friend (e.g., remark name)")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully updated friend",
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
    public ResponseEntity<CommonResponse> updateFriend(
            @Parameter(description = "Friend ID") @PathVariable("friendId") String friendId,
            @Parameter(description = "Friend update request") @Valid @RequestBody
                    UpdateFriendRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        try {
            long currentUserId = Long.parseLong(jwt.getSubject());
            if (request.getRemarkName() != null) {
                return relationService.updateFriendRemarkName(
                        currentUserId, Long.parseLong(friendId), request.getRemarkName());
            }
            return ResponseEntity.ok(new CommonResponse(200, "No fields to update"));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Invalid user ID format"));
        }
    }

    // ==================== Blocked Users ====================

    @PostMapping(
            value = "/me/blocked-users",
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

    @DeleteMapping(value = "/me/blocked-users/{userId}", produces = "application/json")
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
            @Parameter(description = "Target user ID") @PathVariable("userId") String userId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        try {
            long currentUserId = Long.parseLong(jwt.getSubject());
            return relationService.removeBlockedUser(currentUserId, Long.parseLong(userId));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Invalid user ID format"));
        }
    }

    @PatchMapping(
            value = "/me/blocked-users/{userId}",
            consumes = "application/json",
            produces = "application/json")
    @Operation(
            summary = "Update blocked user",
            description =
                    "Update the attributes of the specified blocked user (e.g., hidden status)")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully updated blocked user",
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
    public ResponseEntity<CommonResponse> updateBlockedUser(
            @Parameter(description = "Target user ID") @PathVariable("userId") String userId,
            @Parameter(description = "Blocked user update request") @Valid @RequestBody
                    UpdateBlockedUserRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        try {
            long currentUserId = Long.parseLong(jwt.getSubject());
            if (request.getHidden() != null && request.getHidden()) {
                return relationService.hideBlockedUser(currentUserId, Long.parseLong(userId));
            }
            return ResponseEntity.ok(new CommonResponse(200, "No fields to update"));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Invalid user ID format"));
        }
    }

    // ==================== User Groups ====================

    @GetMapping(value = "/me/groups", produces = "application/json")
    @Operation(
            summary = "Get user's groups with pagination",
            description =
                    "Retrieve all groups that the current user belongs to with pagination support")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully retrieved user's groups",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                GetUserGroupsResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Invalid page size",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                GetUserGroupsResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized access",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = CommonResponse.class)))
            })
    public ResponseEntity<GetUserGroupsResponse> getUserGroups(
            @Parameter(description = "Next page token for pagination (optional)")
                    @RequestParam(required = false, name = "nextPageToken")
                    String nextPageToken,
            @Parameter(description = "Number of groups per page (default: 50, max: 100)")
                    @RequestParam(defaultValue = "50", name = "pageSize")
                    int pageSize,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        try {
            long userIdLong = Long.parseLong(jwt.getSubject());
            return groupService.getUserGroups(userIdLong, nextPageToken, pageSize);
        } catch (NumberFormatException e) {
            log.error("getUserGroups: Invalid user ID format", e);
            return ResponseEntity.badRequest()
                    .body(GetUserGroupsResponse.error(400, "Invalid user ID format"));
        }
    }

    @GetMapping(value = "/me/groups/sync", produces = "application/json")
    @Operation(
            summary = "Sync user's groups",
            description =
                    "Synchronize user's group list changes. Returns full sync flag and incremental changes if available.")
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
                                                                UserGroupSyncResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized access",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = CommonResponse.class)))
            })
    public ResponseEntity<UserGroupSyncResponse> syncUserGroups(
            @Parameter(description = "Client's last known version (optional)")
                    @RequestParam(required = false, name = "version")
                    String version,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        try {
            long userIdLong = Long.parseLong(jwt.getSubject());
            return groupService.syncUserGroups(userIdLong, version);
        } catch (NumberFormatException e) {
            log.error("syncUserGroups: Invalid user ID format", e);
            return ResponseEntity.badRequest()
                    .body(UserGroupSyncResponse.error(400, "Invalid user ID format"));
        }
    }
}
