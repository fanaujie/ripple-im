package com.fanaujie.ripple.apigateway.controller;

import com.fanaujie.ripple.apigateway.dto.*;
import com.fanaujie.ripple.apigateway.service.GroupService;
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
@RequestMapping("/api/groups")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Groups", description = "Group management APIs")
@SecurityRequirement(name = "Bearer Authentication")
public class GroupsController {

    private final GroupService groupService;

    @PostMapping
    @Operation(
            summary = "Create group",
            description = "Create a new group chat with specified members")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully created group",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                CreateGroupResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Invalid request data",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                CreateGroupResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized access",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                CreateGroupResponse.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "Forbidden: Sender ID mismatch",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                CreateGroupResponse.class)))
            })
    public ResponseEntity<CreateGroupResponse> createGroup(
            @Valid @RequestBody CreateGroupRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        try {
            if (!request.getSenderId().equals(jwt.getSubject())) {
                log.warn(
                        "createGroup: Sender ID mismatch. Request: {}, JWT: {}",
                        request.getSenderId(),
                        jwt.getSubject());
                return ResponseEntity.status(403)
                        .body(CreateGroupResponse.error(403, "Forbidden: Sender ID mismatch"));
            }

            return groupService.createGroup(request);

        } catch (NumberFormatException e) {
            log.error("createGroup: Invalid user ID format", e);
            return ResponseEntity.badRequest()
                    .body(CreateGroupResponse.error(400, "Invalid user ID format"));
        }
    }

    @GetMapping("/{groupId}/members")
    @Operation(
            summary = "Get group members with pagination",
            description = "Retrieve the list of members in a group with pagination support")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully retrieved members",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                GetGroupMembersResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Invalid group ID format or page size",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                GetGroupMembersResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized access",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                GetGroupMembersResponse.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "Forbidden: Not a group member",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                GetGroupMembersResponse.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Group not found",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                GetGroupMembersResponse.class)))
            })
    public ResponseEntity<GetGroupMembersResponse> getGroupMembers(
            @PathVariable("groupId") @Parameter(description = "Group ID", required = true)
                    String groupId,
            @Parameter(description = "Next page token for pagination (optional)")
                    @RequestParam(required = false, name = "nextPageToken")
                    String nextPageToken,
            @Parameter(description = "Number of members per page (default: 50, max: 100)")
                    @RequestParam(defaultValue = "50", name = "pageSize")
                    int pageSize,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        try {
            long groupIdLong = Long.parseLong(groupId);
            long requesterId = Long.parseLong(jwt.getSubject());

            return groupService.getGroupMembers(groupIdLong, requesterId, nextPageToken, pageSize);

        } catch (NumberFormatException e) {
            log.error("getGroupMembers: Invalid group ID format", e);
            return ResponseEntity.badRequest()
                    .body(GetGroupMembersResponse.error(400, "Invalid group ID format"));
        }
    }

    @PostMapping("/{groupId}/members")
    @Operation(
            summary = "Add members to group",
            description = "Invite new members to an existing group")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully added members",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = CommonResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Invalid request data",
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
                                        schema = @Schema(implementation = CommonResponse.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "Forbidden: Sender ID mismatch",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = CommonResponse.class)))
            })
    public ResponseEntity<CommonResponse> addGroupMembers(
            @PathVariable("groupId") @Parameter(description = "Group ID", required = true)
                    String groupId,
            @Valid @RequestBody InviteGroupMemberRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        try {
            if (!request.getSenderId().equals(jwt.getSubject())) {
                log.warn(
                        "addGroupMembers: Sender ID mismatch. Request: {}, JWT: {}",
                        request.getSenderId(),
                        jwt.getSubject());
                return ResponseEntity.status(403)
                        .body(CommonResponse.error(403, "Forbidden: Sender ID mismatch"));
            }

            long groupIdLong = Long.parseLong(groupId);
            return groupService.inviteGroupMembers(groupIdLong, request);

        } catch (NumberFormatException e) {
            log.error("addGroupMembers: Invalid group ID format", e);
            return ResponseEntity.badRequest()
                    .body(CommonResponse.error(400, "Invalid group ID format"));
        }
    }

    @DeleteMapping("/{groupId}/members/me")
    @Operation(summary = "Leave group", description = "Remove current user from a group")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully left group",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = CommonResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Invalid ID format",
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
    public ResponseEntity<CommonResponse> leaveGroup(
            @PathVariable("groupId") @Parameter(description = "Group ID", required = true)
                    String groupId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        try {
            long groupIdLong = Long.parseLong(groupId);
            long userIdLong = Long.parseLong(jwt.getSubject());

            return groupService.removeGroupMember(groupIdLong, userIdLong);

        } catch (NumberFormatException e) {
            log.error("leaveGroup: Invalid ID format", e);
            return ResponseEntity.badRequest()
                    .body(CommonResponse.error(400, "Invalid group ID format"));
        }
    }

    @PatchMapping("/{groupId}")
    @Operation(summary = "Update group", description = "Update the name or avatar of a group")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully updated group",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = CommonResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Invalid request data",
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
                                        schema = @Schema(implementation = CommonResponse.class))),
                @ApiResponse(
                        responseCode = "403",
                        description = "Forbidden: Sender ID mismatch",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = CommonResponse.class)))
            })
    public ResponseEntity<CommonResponse> updateGroup(
            @PathVariable("groupId") @Parameter(description = "Group ID", required = true)
                    String groupId,
            @Valid @RequestBody UpdateGroupRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        try {
            if (!request.getSenderId().equals(jwt.getSubject())) {
                log.warn(
                        "updateGroup: Sender ID mismatch. Request: {}, JWT: {}",
                        request.getSenderId(),
                        jwt.getSubject());
                return ResponseEntity.status(403)
                        .body(CommonResponse.error(403, "Forbidden: Sender ID mismatch"));
            }

            long groupIdLong = Long.parseLong(groupId);

            // Update name if provided
            if (request.getName() != null) {
                UpdateGroupInfoRequest nameRequest = new UpdateGroupInfoRequest();
                nameRequest.setSenderId(request.getSenderId());
                nameRequest.setValue(request.getName());
                ResponseEntity<CommonResponse> nameResult =
                        groupService.updateGroupName(groupIdLong, nameRequest);
                if (nameResult.getStatusCode().isError()) {
                    return nameResult;
                }
            }

            // Update avatar if provided
            if (request.getAvatar() != null) {
                UpdateGroupInfoRequest avatarRequest = new UpdateGroupInfoRequest();
                avatarRequest.setSenderId(request.getSenderId());
                avatarRequest.setValue(request.getAvatar());
                ResponseEntity<CommonResponse> avatarResult =
                        groupService.updateGroupAvatar(groupIdLong, avatarRequest);
                if (avatarResult.getStatusCode().isError()) {
                    return avatarResult;
                }
            }

            if (request.getName() == null && request.getAvatar() == null) {
                return ResponseEntity.ok(new CommonResponse(200, "No fields to update"));
            }

            return ResponseEntity.ok(new CommonResponse(200, "Group updated successfully"));

        } catch (NumberFormatException e) {
            log.error("updateGroup: Invalid group ID format", e);
            return ResponseEntity.badRequest()
                    .body(CommonResponse.error(400, "Invalid group ID format"));
        }
    }

    @GetMapping(value = "/{groupId}/members/sync", produces = "application/json")
    @Operation(
            summary = "Sync group members",
            description =
                    "Synchronize group member changes. Returns full sync flag and incremental changes if available.")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully retrieved sync data",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema =
                                                @Schema(implementation = GroupSyncResponse.class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized access",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = CommonResponse.class)))
            })
    public ResponseEntity<GroupSyncResponse> syncGroupMembers(
            @PathVariable("groupId") @Parameter(description = "Group ID", required = true)
                    String groupId,
            @Parameter(description = "Client's last known version (optional)")
                    @RequestParam(required = false, name = "version")
                    String version,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        try {
            long groupIdLong = Long.parseLong(groupId);
            return groupService.syncGroupMembers(groupIdLong, version);
        } catch (NumberFormatException e) {
            log.error("syncGroupMembers: Invalid group ID format", e);
            return ResponseEntity.badRequest()
                    .body(new GroupSyncResponse(400, "Invalid group ID format", null));
        }
    }
}
