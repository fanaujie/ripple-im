package com.fanaujie.ripple.apigateway.controller;

import com.fanaujie.ripple.apigateway.dto.*;
import com.fanaujie.ripple.apigateway.service.BotService;
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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/bots")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Bots", description = "Admin bot management APIs")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminBotsController {

    private final BotService botService;

    @PostMapping
    @Operation(
            summary = "Register a new bot",
            description = "Creates a new bot user with webhook configuration")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Bot registered successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BotRegistrationResponse.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request or account already exists",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BotRegistrationResponse.class)))
    })
    public ResponseEntity<BotRegistrationResponse> registerBot(
            @Valid @RequestBody BotRegistrationRequest request) {
        return botService.registerBot(request);
    }

    @PutMapping("/{botId}")
    @Operation(
            summary = "Update bot configuration",
            description = "Updates the webhook configuration for an existing bot")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Bot updated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Bot not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse> updateBot(
            @Parameter(description = "Bot user ID") @PathVariable String botId,
            @Valid @RequestBody BotUpdateRequest request) {
        long botUserId = Long.parseLong(botId);
        return botService.updateBot(botUserId, request);
    }

    @DeleteMapping("/{botId}")
    @Operation(
            summary = "Delete a bot",
            description = "Deletes a bot and its configuration")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Bot deleted successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CommonResponse.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "Bot not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CommonResponse.class)))
    })
    public ResponseEntity<CommonResponse> deleteBot(
            @Parameter(description = "Bot user ID") @PathVariable String botId) {
        long botUserId = Long.parseLong(botId);
        return botService.deleteBot(botUserId);
    }
}
