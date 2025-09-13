package com.fanaujie.ripple.apigateway.controller;

import com.fanaujie.ripple.apigateway.dto.CommonResponse;
import com.fanaujie.ripple.apigateway.dto.UpdateNickNameRequest;

import com.fanaujie.ripple.apigateway.dto.UserProfileResponse;
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
@RequestMapping("/api/profile")
@RequiredArgsConstructor
@Slf4j
@Tag(
        name = "User Profile Management",
        description = "APIs for querying and updating user profile information")
@SecurityRequirement(name = "bearerAuth")
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping(produces = "application/json")
    @Operation(
            summary = "Get user profile",
            description =
                    "Retrieve detailed profile information for the specified user or the currently authenticated user if no userId is provided")
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
            @Parameter(
                            description =
                                    "User ID to retrieve profile for. If not provided, returns profile of authenticated user")
                    @RequestParam(name = "userId", required = false)
                    Long userId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        long targetUserId = userId != null ? userId : Long.parseLong(jwt.getSubject());
        return userProfileService.getUserProfile(targetUserId);
    }

    @PutMapping(value = "/nickname", consumes = "application/json", produces = "application/json")
    @Operation(
            summary = "Update user nickname",
            description = "Update the nickname of the currently authenticated user")
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successfully updated nickname",
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
    public ResponseEntity<CommonResponse> updateNickName(
            @Parameter(description = "Nickname update request") @Valid @RequestBody
                    UpdateNickNameRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return userProfileService.updateNickName(
                Long.parseLong(jwt.getSubject()), request.getNickName());
    }

    @DeleteMapping(value = "/avatar", produces = "application/json")
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
}
