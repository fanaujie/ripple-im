package com.fanaujie.ripple.apigateway.controller;

import com.fanaujie.ripple.apigateway.dto.BotsListResponse;
import com.fanaujie.ripple.apigateway.service.BotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bots")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Bots", description = "Bot discovery APIs")
@SecurityRequirement(name = "Bearer Authentication")
public class BotsController {

    private final BotService botService;

    @GetMapping
    @Operation(
            summary = "List available bots",
            description = "Retrieves a list of all active bots available for chat")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Bots retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BotsListResponse.class)))
    })
    public ResponseEntity<BotsListResponse> listBots() {
        return botService.listBots();
    }
}
