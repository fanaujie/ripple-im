package com.fanaujie.ripple.apigateway.controller;

import com.fanaujie.ripple.apigateway.dto.*;
import com.fanaujie.ripple.apigateway.service.ConversationService;
import com.fanaujie.ripple.apigateway.service.MessageService;
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
import org.springframework.boot.autoconfigure.cassandra.CassandraConnectionDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/conversation")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Conversations", description = "Conversation APIs")
@SecurityRequirement(name = "Bearer Authentication")
public class ConversationController {

    private final MessageService messageService;
    private final ConversationService conversationService;
    private final CassandraConnectionDetails cassandraConnectionDetails;

    @GetMapping(produces = "application/json")
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

    @GetMapping(value = "/sync", produces = "application/json")
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

    @GetMapping(value = "/version", produces = "application/json")
    @Operation(
            summary = "Get latest conversation version",
            description =
                    "Retrieve the latest version timestamp for the current user's conversation changes")
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
                                                                ConversationVersionResponse
                                                                        .class))),
                @ApiResponse(
                        responseCode = "401",
                        description = "Unauthorized access",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = CommonResponse.class)))
            })
    public ResponseEntity<ConversationVersionResponse> getLatestVersion(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        long currentUserId = Long.parseLong(jwt.getSubject());
        return conversationService.getLatestVersion(currentUserId);
    }

    @PostMapping("/message")
    @Operation(
            summary = "Send message",
            description = "Send a text or file message to another user")
    public ResponseEntity<MessageResponse> sendMessage(
            @Valid @RequestBody SendMessageRequest request, @AuthenticationPrincipal Jwt jwt) {
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

    @GetMapping(value = "/message", produces = "application/json")
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
                    @RequestParam(name = "conversationId")
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

    @PutMapping("/{conversationId}/message/{messageId}/mark-read")
    @Operation(
            summary = "Mark last read message",
            description = "Mark a message as the last read message in a conversation")
    public ResponseEntity<CommonResponse> markLastReadMessageId(
            @Parameter(description = "Conversation ID", required = true)
                    @PathVariable("conversationId")
                    String conversationId,
            @Parameter(description = "Message ID to mark as read", required = true)
                    @PathVariable("messageId")
                    String messageId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        try {
            long currentUserId = Long.parseLong(jwt.getSubject());
            long messageIdLong = Long.parseLong(messageId);
            return messageService.markLastReadMessageId(
                    conversationId, messageIdLong, currentUserId);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Invalid message ID or user ID format"));
        }
    }
}
