package com.fanaujie.ripple.apigateway.controller;

import com.fanaujie.ripple.apigateway.dto.CommonResponse;
import com.fanaujie.ripple.apigateway.dto.PagedResponse;
import com.fanaujie.ripple.communication.grpc.client.GrpcClient;
import com.fanaujie.ripple.protobuf.msgapiserver.*;
import com.fanaujie.ripple.storage.model.BotInfo;
import com.fanaujie.ripple.storage.model.BotUserToken;
import com.fanaujie.ripple.storage.model.UserInstalledBot;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Bots", description = "Bot Marketplace and Management APIs")
@Slf4j
public class BotController {

    private final RippleStorageFacade storageFacade;
    private final GrpcClient<BotManagementServiceGrpc.BotManagementServiceBlockingStub> botManagementClient;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    // --- Marketplace ---

    @GetMapping("/bots")
    @Operation(summary = "List bots", description = "Retrieve a list of available bots with optional category filter and pagination")
    public ResponseEntity<PagedResponse<BotInfo>> getAllBots(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {

        size = Math.min(size, MAX_PAGE_SIZE);
        List<BotInfo> allBots;

        if (category != null && !category.isEmpty()) {
            allBots = storageFacade.getBotsByCategory(category);
        } else {
            allBots = storageFacade.getAllBots();
        }

        // Filter only enabled bots
        allBots = allBots.stream().filter(BotInfo::isEnabled).collect(Collectors.toList());

        // Apply pagination
        int total = allBots.size();
        int start = page * size;
        int end = Math.min(start + size, total);

        List<BotInfo> pagedBots = start < total ? allBots.subList(start, end) : List.of();

        return ResponseEntity.ok(new PagedResponse<>(pagedBots, page, size, total));
    }

    @GetMapping("/bots/{id}")
    @Operation(summary = "Get bot details", description = "Retrieve details for a specific bot")
    public ResponseEntity<BotInfo> getBot(@PathVariable("id") Long id) {
        BotInfo bot = storageFacade.getBot(id);
        if (bot == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(bot);
    }

    // --- User Library ---

    @GetMapping("/users/me/bots")
    @Operation(summary = "List installed bots", description = "Retrieve bots installed by the current user")
    public ResponseEntity<List<UserInstalledBot>> getMyBots(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        long userId = Long.parseLong(jwt.getSubject());
        return ResponseEntity.ok(storageFacade.getUserInstalledBots(userId));
    }

    @PostMapping("/users/me/bots/{id}")
    @Operation(summary = "Install bot", description = "Install a bot for the current user")
    public ResponseEntity<CommonResponse> installBot(
            @PathVariable("id") Long botId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        long userId = Long.parseLong(jwt.getSubject());

        InstallBotReq req = InstallBotReq.newBuilder().setUserId(userId).setBotId(botId).build();
        InstallBotResp resp = botManagementClient.getStub().installBot(req);

        if (resp.getSuccess()) {
            return ResponseEntity.ok(new CommonResponse(200, "Bot installed successfully"));
        } else {
            return ResponseEntity.badRequest().body(new CommonResponse(400, resp.getErrorMessage()));
        }
    }

    @DeleteMapping("/users/me/bots/{id}")
    @Operation(summary = "Uninstall bot", description = "Uninstall a bot for the current user")
    public ResponseEntity<CommonResponse> uninstallBot(
            @PathVariable("id") Long botId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        long userId = Long.parseLong(jwt.getSubject());

        UninstallBotReq req = UninstallBotReq.newBuilder().setUserId(userId).setBotId(botId).build();
        UninstallBotResp resp = botManagementClient.getStub().uninstallBot(req);

        if (resp.getSuccess()) {
            return ResponseEntity.ok(new CommonResponse(200, "Bot uninstalled successfully"));
        } else {
            return ResponseEntity.badRequest().body(new CommonResponse(400, resp.getErrorMessage()));
        }
    }

    // --- OAuth Flow ---

    @GetMapping("/bots/{botId}/oauth/authorize")
    @Operation(summary = "Initiate OAuth flow", description = "Redirect user to bot's OAuth authorization page")
    public ResponseEntity<Void> initiateOAuth(
            @PathVariable("botId") Long botId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {

        long userId = Long.parseLong(jwt.getSubject());
        BotInfo bot = storageFacade.getBot(botId);

        if (bot == null || !bot.isRequireAuth()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            JsonNode authConfig = objectMapper.readTree(bot.getAuthConfig());
            String authUrl = authConfig.get("auth_url").asText();
            String clientId = authConfig.get("client_id").asText();
            String redirectUri = buildRedirectUri(botId);
            String scope = authConfig.has("scope") ? authConfig.get("scope").asText() : "";

            String fullAuthUrl = String.format("%s?client_id=%s&redirect_uri=%s&response_type=code&state=%d&scope=%s",
                    authUrl, clientId, redirectUri, userId, scope);

            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(fullAuthUrl))
                    .build();
        } catch (Exception e) {
            log.error("Failed to initiate OAuth for bot {}", botId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/bots/{botId}/oauth/callback")
    @Operation(summary = "OAuth callback", description = "Handle OAuth callback and exchange code for tokens")
    public ResponseEntity<CommonResponse> handleOAuthCallback(
            @PathVariable("botId") Long botId,
            @RequestParam("code") String code,
            @RequestParam("state") String state) {

        long userId;
        try {
            userId = Long.parseLong(state);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(new CommonResponse(400, "Invalid state parameter"));
        }

        BotInfo bot = storageFacade.getBot(botId);
        if (bot == null || !bot.isRequireAuth()) {
            return ResponseEntity.badRequest().body(new CommonResponse(400, "Bot not found or auth not required"));
        }

        try {
            JsonNode authConfig = objectMapper.readTree(bot.getAuthConfig());
            String tokenUrl = authConfig.get("token_url").asText();
            String clientId = authConfig.get("client_id").asText();
            String clientSecret = authConfig.get("client_secret").asText();
            String redirectUri = buildRedirectUri(botId);

            // Exchange code for tokens
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "authorization_code");
            body.add("code", code);
            body.add("redirect_uri", redirectUri);
            body.add("client_id", clientId);
            body.add("client_secret", clientSecret);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(tokenUrl, request, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode tokenResponse = objectMapper.readTree(response.getBody());
                String accessToken = tokenResponse.get("access_token").asText();
                String refreshToken = tokenResponse.has("refresh_token") ? tokenResponse.get("refresh_token").asText() : null;
                int expiresIn = tokenResponse.has("expires_in") ? tokenResponse.get("expires_in").asInt() : 3600;

                BotUserToken token = BotUserToken.builder()
                        .botId(botId)
                        .userId(userId)
                        .accessToken(accessToken)
                        .refreshToken(refreshToken)
                        .expiresAt(Date.from(Instant.now().plus(expiresIn, ChronoUnit.SECONDS)))
                        .build();

                storageFacade.saveBotUserToken(token);
                log.info("Saved OAuth token for user {} bot {}", userId, botId);

                // Return success page or redirect
                return ResponseEntity.ok(new CommonResponse(200, "Authorization successful. You can now use the bot."));
            } else {
                log.error("Token exchange failed for bot {}: {}", botId, response.getBody());
                return ResponseEntity.badRequest().body(new CommonResponse(400, "Token exchange failed"));
            }
        } catch (Exception e) {
            log.error("Failed to handle OAuth callback for bot {}", botId, e);
            return ResponseEntity.internalServerError().body(new CommonResponse(500, "OAuth callback failed"));
        }
    }

    private String buildRedirectUri(Long botId) {
        // This should be configurable via environment
        return String.format("https://api.ripple.im/api/bots/%d/oauth/callback", botId);
    }
}
