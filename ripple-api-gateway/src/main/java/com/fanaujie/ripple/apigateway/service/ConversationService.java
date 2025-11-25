package com.fanaujie.ripple.apigateway.service;

import com.fanaujie.ripple.apigateway.dto.*;
import com.fanaujie.ripple.storage.exception.InvalidVersionException;
import com.fanaujie.ripple.storage.model.Conversation;
import com.fanaujie.ripple.storage.model.ConversationVersionChange;
import com.fanaujie.ripple.storage.model.PagedConversationResult;
import com.fanaujie.ripple.storage.repository.ConversationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ConversationService {

    private static final int MAX_PAGE_SIZE = 200;
    private static final int MAX_SYNC_CHANGES = 200;
    private final ConversationRepository conversationRepository;

    public ConversationService(ConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    public ResponseEntity<ConversationsResponse> getConversations(
            long currentUserId, String nextPageToken, int pageSize) {
        // Validate pageSize
        if (pageSize <= 0 || pageSize > MAX_PAGE_SIZE) {
            return ResponseEntity.badRequest()
                    .body(new ConversationsResponse(400, "Invalid page size", null));
        }

        PagedConversationResult result =
                this.conversationRepository.getConversations(
                        currentUserId, nextPageToken, pageSize);
        List<ConversationItem> conversations =
                result.getConversations().stream()
                        .map(this::toConversationItem)
                        .collect(Collectors.toList());
        ConversationsData data =
                new ConversationsData(conversations, result.getNextPageToken(), result.isHasMore());

        return ResponseEntity.ok(new ConversationsResponse(200, "success", data));
    }

    public ResponseEntity<ConversationSyncResponse> syncConversations(
            long currentUserId, String version) {
        // If version is null or empty, require full sync
        if (version == null || version.isEmpty()) {
            ConversationSyncData data = new ConversationSyncData(true, null, new ArrayList<>());
            return ResponseEntity.ok(new ConversationSyncResponse(200, "success", data));
        }

        try {
            // Query changes with max batch size
            List<ConversationVersionChange> records =
                    this.conversationRepository.getConversationChanges(
                            currentUserId, version, MAX_SYNC_CHANGES);

            // Convert records to ConversationChange DTOs
            List<ConversationChange> changes =
                    records.stream().map(this::toConversationChange).collect(Collectors.toList());

            // Get latest version (from last record) for next batch sync
            String latestVersion =
                    records.isEmpty() ? version : records.get(records.size() - 1).getVersion();

            ConversationSyncData data = new ConversationSyncData(false, latestVersion, changes);
            return ResponseEntity.ok(new ConversationSyncResponse(200, "success", data));
        } catch (InvalidVersionException e) {
            return ResponseEntity.badRequest()
                    .body(new ConversationSyncResponse(400, e.getMessage(), null));
        }
    }

    public ResponseEntity<ConversationVersionResponse> getLatestVersion(long currentUserId) {
        String latestVersion =
                this.conversationRepository.getLatestConversationVersion(currentUserId);

        if (latestVersion == null) {
            return ResponseEntity.ok(
                    new ConversationVersionResponse(
                            200, "success", new ConversationVersionData(null)));
        }

        ConversationVersionData data = new ConversationVersionData(latestVersion);
        return ResponseEntity.ok(new ConversationVersionResponse(200, "success", data));
    }

    private ConversationItem toConversationItem(Conversation conversation) {
        return new ConversationItem(
                conversation.getConversationId(),
                conversation.getPeerId() != 0 ? String.valueOf(conversation.getPeerId()) : null,
                conversation.getGroupId() != 0 ? String.valueOf(conversation.getGroupId()) : null,
                String.valueOf(conversation.getLastMessageId()),
                conversation.getLastMessage(),
                conversation.getLastMessageTimestamp(),
                conversation.getLastReadMessageId() != 0
                        ? String.valueOf(conversation.getLastReadMessageId())
                        : null,
                conversation.getUnreadCount());
    }

    private ConversationChange toConversationChange(ConversationVersionChange record) {
        return new ConversationChange(
                record.getVersion(),
                record.getOperation() & 0xFF, // byte to int
                record.getConversationId(),
                record.getPeerId() != 0 ? String.valueOf(record.getPeerId()) : null,
                record.getGroupId() != 0 ? String.valueOf(record.getGroupId()) : null,
                record.getLastMessageId() != 0 ? String.valueOf(record.getLastMessageId()) : null,
                record.getLastMessage(),
                record.getLastMessageTimestamp(),
                record.getLastReadMessageId() != 0
                        ? String.valueOf(record.getLastReadMessageId())
                        : null);
    }
}
