package com.fanaujie.ripple.apigateway.service;

import com.fanaujie.ripple.apigateway.dto.*;
import com.fanaujie.ripple.storage.exception.InvalidVersionException;
import com.fanaujie.ripple.storage.model.Conversation;
import com.fanaujie.ripple.storage.model.ConversationState;
import com.fanaujie.ripple.storage.model.ConversationVersionChange;
import com.fanaujie.ripple.storage.model.LastMessageInfo;
import com.fanaujie.ripple.storage.model.PagedConversationResult;
import com.fanaujie.ripple.storage.service.ConversationStateFacade;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ConversationService {

    private static final int MAX_PAGE_SIZE = 200;
    private static final int MAX_SYNC_CHANGES = 200;
    private final RippleStorageFacade storageFacade;
    private final ConversationStateFacade conversationStorage;

    public ConversationService(
            RippleStorageFacade storageFacade, ConversationStateFacade conversationStorage) {
        this.storageFacade = storageFacade;
        this.conversationStorage = conversationStorage;
    }

    public ResponseEntity<ConversationsResponse> getConversations(
            long currentUserId, String nextPageToken, int pageSize) {
        // Validate pageSize
        if (pageSize <= 0 || pageSize > MAX_PAGE_SIZE) {
            return ResponseEntity.badRequest()
                    .body(new ConversationsResponse(400, "Invalid page size", null));
        }
        PagedConversationResult result =
                this.storageFacade.getConversations(currentUserId, nextPageToken, pageSize);
        List<Conversation> conversationList = result.getConversations();

        List<String> conversationIds =
                conversationList.stream()
                        .map(Conversation::getConversationId)
                        .collect(Collectors.toList());

        Map<String, ConversationState> conversationStates =
                conversationStorage.batchGetConversationState(currentUserId, conversationIds);

        List<ConversationItem> conversations = new ArrayList<>();
        for (Conversation conversation : conversationList) {
            String convId = conversation.getConversationId();
            ConversationState state = conversationStates.get(convId);
            Integer unreadCount = state != null ? state.getUnreadCount() : null;
            LastMessageInfo lastMsg = state != null ? state.getLastMessage() : null;
            conversation.setUnreadCount(unreadCount != null ? unreadCount : 0);
            conversations.add(toConversationItem(conversation, lastMsg));
        }

        ConversationsData data =
                new ConversationsData(
                        conversations, result.getNextPageToken(), result.isHasMore(), null);
        if (!data.isHasMore()) {
            String latestVersion = this.storageFacade.getLatestConversationVersion(currentUserId);
            data.setLastVersion(latestVersion);
        }
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
                    this.storageFacade.getConversationChanges(
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

    public ResponseEntity<ConversationSummaryResponse> getConversationSummaries(
            long currentUserId, List<String> conversationIds) {
        if (conversationIds == null || conversationIds.isEmpty()) {
            ConversationSummaryData data = new ConversationSummaryData(new ArrayList<>());
            return ResponseEntity.ok(new ConversationSummaryResponse(200, "success", data));
        }

        Map<String, ConversationState> conversationStates =
                conversationStorage.batchGetConversationState(currentUserId, conversationIds);

        List<ConversationSummary> summaries = new ArrayList<>();
        for (String conversationId : conversationIds) {
            ConversationState state = conversationStates.get(conversationId);
            Integer unreadCount = state != null ? state.getUnreadCount() : null;
            LastMessageInfo lastMsg = state != null ? state.getLastMessage() : null;

            summaries.add(
                    new ConversationSummary(
                            conversationId,
                            unreadCount != null ? unreadCount : 0,
                            lastMsg != null ? lastMsg.getText() : null,
                            lastMsg != null ? lastMsg.getTimestamp() : 0L,
                            lastMsg != null ? lastMsg.getMessageId() : null));
        }

        ConversationSummaryData data = new ConversationSummaryData(summaries);
        return ResponseEntity.ok(new ConversationSummaryResponse(200, "success", data));
    }

    private ConversationItem toConversationItem(
            Conversation conversation, LastMessageInfo lastMsg) {
        return new ConversationItem(
                conversation.getConversationId(),
                conversation.getPeerId() != 0 ? String.valueOf(conversation.getPeerId()) : null,
                conversation.getGroupId() != 0 ? String.valueOf(conversation.getGroupId()) : null,
                conversation.getLastReadMessageId() != 0
                        ? String.valueOf(conversation.getLastReadMessageId())
                        : null,
                conversation.getUnreadCount(),
                conversation.getName(),
                conversation.getAvatar(),
                lastMsg != null ? lastMsg.getText() : null,
                lastMsg != null ? lastMsg.getTimestamp() : 0L,
                lastMsg != null ? lastMsg.getMessageId() : null);
    }

    private ConversationChange toConversationChange(ConversationVersionChange record) {
        return new ConversationChange(
                record.getVersion(),
                record.getOperation() & 0xFF, // byte to int
                record.getConversationId(),
                record.getPeerId() != 0 ? String.valueOf(record.getPeerId()) : null,
                record.getGroupId() != 0 ? String.valueOf(record.getGroupId()) : null,
                record.getLastReadMessageId() != 0
                        ? String.valueOf(record.getLastReadMessageId())
                        : null,
                record.getName(),
                record.getAvatar());
    }
}
