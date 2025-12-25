package com.fanaujie.ripple.apigateway.service;

import com.fanaujie.ripple.apigateway.dto.*;
import com.fanaujie.ripple.cache.service.ConversationSummaryStorage;
import com.fanaujie.ripple.cache.service.UserProfileStorage;
import com.fanaujie.ripple.storage.exception.InvalidVersionException;
import com.fanaujie.ripple.storage.model.*;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = {ConversationService.class})
class ConversationServiceTest {

    @MockitoBean
    private RippleStorageFacade storageFacade;

    @MockitoBean
    private UserProfileStorage userProfileStorage;

    @MockitoBean
    private ConversationSummaryStorage conversationSummaryStorage;

    @Autowired
    private ConversationService conversationService;

    private static final long USER_ID = 1L;
    private static final String CONVERSATION_ID = "conv1";
    private static final String VERSION = "v1";

    @BeforeEach
    void setUp() {
        reset(storageFacade, userProfileStorage, conversationSummaryStorage);
    }

    // ==================== getConversations Tests ====================

    @Test
    void getConversations_Success() {
        // Given
        List<Conversation> conversationList = new ArrayList<>();
        Conversation conv = new Conversation();
        conv.setConversationId(CONVERSATION_ID);
        conv.setPeerId(2L);
        conv.setName("Chat");
        conversationList.add(conv);

        PagedConversationResult pagedResult = new PagedConversationResult(conversationList, null, false);
        when(storageFacade.getConversations(USER_ID, null, 50)).thenReturn(pagedResult);
        when(storageFacade.getLatestConversationVersion(USER_ID)).thenReturn("v1");

        Map<String, ConversationSummaryInfo> summaries = new HashMap<>();
        ConversationSummaryInfo summary = new ConversationSummaryInfo();
        summary.setUnreadCount(5);
        LastMessageInfo lastMsg = new LastMessageInfo();
        lastMsg.setText("Hello");
        lastMsg.setTimestamp(System.currentTimeMillis());
        summary.setLastMessage(lastMsg);
        summaries.put(CONVERSATION_ID, summary);
        when(conversationSummaryStorage.batchGetConversationSummary(eq(USER_ID), anyList()))
                .thenReturn(summaries);

        // When
        ResponseEntity<ConversationsResponse> response =
                conversationService.getConversations(USER_ID, null, 50);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertEquals("success", response.getBody().getMessage());
        assertNotNull(response.getBody().getData());
        assertEquals(1, response.getBody().getData().getConversations().size());
        assertEquals(5, response.getBody().getData().getConversations().get(0).getUnreadCount());

        verify(storageFacade).getConversations(USER_ID, null, 50);
        verify(storageFacade).getLatestConversationVersion(USER_ID);
    }

    @Test
    void getConversations_WithPagination() {
        // Given
        List<Conversation> conversationList = new ArrayList<>();
        PagedConversationResult pagedResult = new PagedConversationResult(conversationList, "nextToken", true);
        when(storageFacade.getConversations(USER_ID, "token", 20)).thenReturn(pagedResult);
        when(conversationSummaryStorage.batchGetConversationSummary(eq(USER_ID), anyList()))
                .thenReturn(new HashMap<>());

        // When
        ResponseEntity<ConversationsResponse> response =
                conversationService.getConversations(USER_ID, "token", 20);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertTrue(response.getBody().getData().isHasMore());
        assertEquals("nextToken", response.getBody().getData().getNextPageToken());

        verify(storageFacade).getConversations(USER_ID, "token", 20);
        // Should not get latest version when hasMore is true
        verify(storageFacade, never()).getLatestConversationVersion(USER_ID);
    }

    @Test
    void getConversations_InvalidPageSize_Zero_ReturnsBadRequest() {
        // When
        ResponseEntity<ConversationsResponse> response =
                conversationService.getConversations(USER_ID, null, 0);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertEquals("Invalid page size", response.getBody().getMessage());

        verifyNoInteractions(storageFacade);
    }

    @Test
    void getConversations_InvalidPageSize_TooLarge_ReturnsBadRequest() {
        // When
        ResponseEntity<ConversationsResponse> response =
                conversationService.getConversations(USER_ID, null, 500);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());
        assertEquals("Invalid page size", response.getBody().getMessage());

        verifyNoInteractions(storageFacade);
    }

    @Test
    void getConversations_EmptyResult() {
        // Given
        PagedConversationResult pagedResult = new PagedConversationResult(new ArrayList<>(), null, false);
        when(storageFacade.getConversations(USER_ID, null, 50)).thenReturn(pagedResult);
        when(storageFacade.getLatestConversationVersion(USER_ID)).thenReturn("v1");
        when(conversationSummaryStorage.batchGetConversationSummary(eq(USER_ID), anyList()))
                .thenReturn(new HashMap<>());

        // When
        ResponseEntity<ConversationsResponse> response =
                conversationService.getConversations(USER_ID, null, 50);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().getData().getConversations().isEmpty());
        assertFalse(response.getBody().getData().isHasMore());
    }

    // ==================== syncConversations Tests ====================

    @Test
    void syncConversations_NullVersion_RequiresFullSync() {
        // When
        ResponseEntity<ConversationSyncResponse> response =
                conversationService.syncConversations(USER_ID, null);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertNotNull(response.getBody().getData());
        assertTrue(response.getBody().getData().isFullSync());
        assertTrue(response.getBody().getData().getChanges().isEmpty());

        verifyNoInteractions(storageFacade);
    }

    @Test
    void syncConversations_EmptyVersion_RequiresFullSync() {
        // When
        ResponseEntity<ConversationSyncResponse> response =
                conversationService.syncConversations(USER_ID, "");

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().getData().isFullSync());

        verifyNoInteractions(storageFacade);
    }

    @Test
    void syncConversations_WithChanges_Success() throws InvalidVersionException {
        // Given
        List<ConversationVersionChange> changes = new ArrayList<>();
        ConversationVersionChange change = new ConversationVersionChange();
        change.setVersion("v2");
        change.setOperation((byte) 1);
        change.setConversationId(CONVERSATION_ID);
        change.setPeerId(2L);
        change.setName("Chat");
        changes.add(change);

        when(storageFacade.getConversationChanges(USER_ID, VERSION, 200)).thenReturn(changes);

        // When
        ResponseEntity<ConversationSyncResponse> response =
                conversationService.syncConversations(USER_ID, VERSION);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertNotNull(response.getBody().getData());
        assertFalse(response.getBody().getData().isFullSync());
        assertEquals(1, response.getBody().getData().getChanges().size());
        assertEquals("v2", response.getBody().getData().getLatestVersion());

        verify(storageFacade).getConversationChanges(USER_ID, VERSION, 200);
    }

    @Test
    void syncConversations_NoChanges_Success() throws InvalidVersionException {
        // Given
        when(storageFacade.getConversationChanges(USER_ID, VERSION, 200))
                .thenReturn(new ArrayList<>());

        // When
        ResponseEntity<ConversationSyncResponse> response =
                conversationService.syncConversations(USER_ID, VERSION);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertFalse(response.getBody().getData().isFullSync());
        assertTrue(response.getBody().getData().getChanges().isEmpty());
        assertEquals(VERSION, response.getBody().getData().getLatestVersion());
    }

    @Test
    void syncConversations_InvalidVersion_ReturnsBadRequest() throws InvalidVersionException {
        // Given
        String invalidVersion = "invalid";
        when(storageFacade.getConversationChanges(USER_ID, invalidVersion, 200))
                .thenThrow(new InvalidVersionException("Invalid version format"));

        // When
        ResponseEntity<ConversationSyncResponse> response =
                conversationService.syncConversations(USER_ID, invalidVersion);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().getCode());

        verify(storageFacade).getConversationChanges(USER_ID, invalidVersion, 200);
    }

    // ==================== getConversationSummaries Tests ====================

    @Test
    void getConversationSummaries_Success() {
        // Given
        List<String> conversationIds = Arrays.asList("conv1", "conv2");
        Map<String, ConversationSummaryInfo> summaries = new HashMap<>();

        ConversationSummaryInfo summary1 = new ConversationSummaryInfo();
        summary1.setUnreadCount(5);
        LastMessageInfo lastMsg1 = new LastMessageInfo();
        lastMsg1.setText("Hello");
        lastMsg1.setTimestamp(1000L);
        lastMsg1.setMessageId("msg1");
        summary1.setLastMessage(lastMsg1);
        summaries.put("conv1", summary1);

        ConversationSummaryInfo summary2 = new ConversationSummaryInfo();
        summary2.setUnreadCount(0);
        summaries.put("conv2", summary2);

        when(conversationSummaryStorage.batchGetConversationSummary(USER_ID, conversationIds))
                .thenReturn(summaries);

        // When
        ResponseEntity<ConversationSummaryResponse> response =
                conversationService.getConversationSummaries(USER_ID, conversationIds);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertEquals(2, response.getBody().getData().getSummaries().size());

        verify(conversationSummaryStorage).batchGetConversationSummary(USER_ID, conversationIds);
    }

    @Test
    void getConversationSummaries_EmptyList_ReturnsEmptySummaries() {
        // When
        ResponseEntity<ConversationSummaryResponse> response =
                conversationService.getConversationSummaries(USER_ID, new ArrayList<>());

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getCode());
        assertTrue(response.getBody().getData().getSummaries().isEmpty());

        verifyNoInteractions(conversationSummaryStorage);
    }

    @Test
    void getConversationSummaries_NullList_ReturnsEmptySummaries() {
        // When
        ResponseEntity<ConversationSummaryResponse> response =
                conversationService.getConversationSummaries(USER_ID, null);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().getData().getSummaries().isEmpty());

        verifyNoInteractions(conversationSummaryStorage);
    }

    @Test
    void getConversationSummaries_PartialResults() {
        // Given - some conversations have summaries, others don't
        List<String> conversationIds = Arrays.asList("conv1", "conv2", "conv3");
        Map<String, ConversationSummaryInfo> summaries = new HashMap<>();

        ConversationSummaryInfo summary1 = new ConversationSummaryInfo();
        summary1.setUnreadCount(5);
        summaries.put("conv1", summary1);
        // conv2 and conv3 have no summary

        when(conversationSummaryStorage.batchGetConversationSummary(USER_ID, conversationIds))
                .thenReturn(summaries);

        // When
        ResponseEntity<ConversationSummaryResponse> response =
                conversationService.getConversationSummaries(USER_ID, conversationIds);

        // Then
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(3, response.getBody().getData().getSummaries().size());

        // First one should have unread count
        ConversationSummary first = response.getBody().getData().getSummaries().get(0);
        assertEquals("conv1", first.getConversationId());
        assertEquals(5, first.getUnreadCount());

        // Others should have default values
        ConversationSummary second = response.getBody().getData().getSummaries().get(1);
        assertEquals("conv2", second.getConversationId());
        assertEquals(0, second.getUnreadCount());
    }
}
