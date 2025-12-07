package com.fanaujie.ripple.apigateway.controller;

import com.fanaujie.ripple.apigateway.dto.CommonResponse;
import com.fanaujie.ripple.apigateway.dto.CreateGroupRequest;
import com.fanaujie.ripple.apigateway.dto.CreateGroupResponse;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/group")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Groups", description = "Group management APIs")
@SecurityRequirement(name = "Bearer Authentication")
public class GroupController {

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
                                        schema = @Schema(implementation = CreateGroupResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Invalid request data",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = CreateGroupResponse.class))),
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
                                        schema = @Schema(implementation = CreateGroupResponse.class)))
            })
    public ResponseEntity<CreateGroupResponse> createGroup(
            @Valid @RequestBody CreateGroupRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        try {
            // Validate sender ID matches JWT subject
            if (!request.getSenderId().equals(jwt.getSubject())) {
                log.warn("createGroup: Sender ID mismatch. Request: {}, JWT: {}",
                        request.getSenderId(), jwt.getSubject());
                return ResponseEntity.status(403)
                        .body(CreateGroupResponse.error(403, "Forbidden: Sender ID mismatch"));
            }

            return groupService.createGroup(request);

        } catch (NumberFormatException e) {
            log.error("createGroup: Invalid user ID format", e);
            return ResponseEntity.badRequest()
                    .body(CreateGroupResponse.error(400, "Invalid user ID format"));
        } catch (Exception e) {
            log.error("createGroup: Unexpected error", e);
            return ResponseEntity.status(500)
                    .body(CreateGroupResponse.error(500, "Internal server error"));
        }
    }
}
