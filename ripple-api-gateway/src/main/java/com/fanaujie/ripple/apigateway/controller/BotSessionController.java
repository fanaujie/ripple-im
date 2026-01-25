package com.fanaujie.ripple.apigateway.controller;

import com.fanaujie.ripple.apigateway.dto.BotSessionResponse;
import com.fanaujie.ripple.apigateway.service.BotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me/bot-sessions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Bot Sessions", description = "Bot session management APIs")
@SecurityRequirement(name = "Bearer Authentication")
public class BotSessionController {

    private final BotService botService;

    @PostMapping("/{botId}/new")
    @Operation(
            summary = "Create new bot session",
            description = "Creates a new session with a bot, clearing any previous conversation context")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Session created successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BotSessionResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Bot not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BotSessionResponse.class)))
    })
    public ResponseEntity<BotSessionResponse> createNewSession(
            @Parameter(description = "Bot user ID") @PathVariable String botId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        long currentUserId = Long.parseLong(jwt.getSubject());
        long botUserId = Long.parseLong(botId);
        return botService.createNewSession(currentUserId, botUserId);
    }

    @GetMapping("/{botId}")
    @Operation(
            summary = "Get bot session",
            description = "Retrieves the current session information with a bot")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Session retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BotSessionResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Session not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BotSessionResponse.class)))
    })
    public ResponseEntity<BotSessionResponse> getSession(
            @Parameter(description = "Bot user ID") @PathVariable String botId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        long currentUserId = Long.parseLong(jwt.getSubject());
        long botUserId = Long.parseLong(botId);
        return botService.getSession(currentUserId, botUserId);
    }
}
