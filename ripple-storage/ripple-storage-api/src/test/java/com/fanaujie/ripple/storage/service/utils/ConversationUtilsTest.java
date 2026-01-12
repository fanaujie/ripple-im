package com.fanaujie.ripple.storage.service.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConversationUtilsTest {

    @Test
    void generateConversationId_shouldReturnSameIdForSameUserPair() {
        long userId1 = 1000L;
        long userId2 = 2000L;

        String id1 = ConversationUtils.generateConversationId(userId1, userId2);
        String id2 = ConversationUtils.generateConversationId(userId1, userId2);

        assertEquals(id1, id2);
    }

    @Test
    void generateConversationId_shouldReturnSameIdRegardlessOfOrder() {
        long userId1 = 1000L;
        long userId2 = 2000L;

        String id1 = ConversationUtils.generateConversationId(userId1, userId2);
        String id2 = ConversationUtils.generateConversationId(userId2, userId1);

        assertEquals(id1, id2);
    }

    @Test
    void generateConversationId_shouldReturn16CharacterHexString() {
        long userId1 = 1000L;
        long userId2 = 2000L;

        String conversationId = ConversationUtils.generateConversationId(userId1, userId2);

        assertEquals(16, conversationId.length());
        assertTrue(conversationId.matches("[0-9a-f]+"));
    }

    @Test
    void generateConversationId_shouldReturnDifferentIdsForDifferentPairs() {
        String id1 = ConversationUtils.generateConversationId(1000L, 2000L);
        String id2 = ConversationUtils.generateConversationId(1000L, 3000L);
        String id3 = ConversationUtils.generateConversationId(2000L, 3000L);

        assertNotEquals(id1, id2);
        assertNotEquals(id1, id3);
        assertNotEquals(id2, id3);
    }

    @Test
    void generateConversationId_shouldHandleSameUserId() {
        long userId = 1000L;

        String conversationId = ConversationUtils.generateConversationId(userId, userId);

        assertNotNull(conversationId);
        assertEquals(16, conversationId.length());
    }

    @Test
    void generateConversationId_shouldHandleZeroUserIds() {
        String conversationId = ConversationUtils.generateConversationId(0L, 0L);

        assertNotNull(conversationId);
        assertEquals(16, conversationId.length());
    }

    @Test
    void generateConversationId_shouldHandleLargeUserIds() {
        long userId1 = Long.MAX_VALUE;
        long userId2 = Long.MAX_VALUE - 1;

        String conversationId = ConversationUtils.generateConversationId(userId1, userId2);

        assertNotNull(conversationId);
        assertEquals(16, conversationId.length());
    }

    @Test
    void generateConversationId_shouldHandleNegativeUserIds() {
        long userId1 = -1000L;
        long userId2 = -2000L;

        String conversationId = ConversationUtils.generateConversationId(userId1, userId2);

        assertNotNull(conversationId);
        assertEquals(16, conversationId.length());
    }

    @Test
    void generateGroupConversationId_shouldReturnConsistentId() {
        long groupId = 5000L;

        String id1 = ConversationUtils.generateGroupConversationId(groupId);
        String id2 = ConversationUtils.generateGroupConversationId(groupId);

        assertEquals(id1, id2);
    }

    @Test
    void generateGroupConversationId_shouldReturn16CharacterHexString() {
        long groupId = 5000L;

        String conversationId = ConversationUtils.generateGroupConversationId(groupId);

        assertEquals(16, conversationId.length());
        assertTrue(conversationId.matches("[0-9a-f]+"));
    }

    @Test
    void generateGroupConversationId_shouldReturnDifferentIdsForDifferentGroups() {
        String id1 = ConversationUtils.generateGroupConversationId(5000L);
        String id2 = ConversationUtils.generateGroupConversationId(5001L);

        assertNotEquals(id1, id2);
    }

    @Test
    void generateGroupConversationId_shouldHandleZeroGroupId() {
        String conversationId = ConversationUtils.generateGroupConversationId(0L);

        assertNotNull(conversationId);
        assertEquals(16, conversationId.length());
    }

    @Test
    void generateGroupConversationId_shouldHandleLargeGroupId() {
        String conversationId = ConversationUtils.generateGroupConversationId(Long.MAX_VALUE);

        assertNotNull(conversationId);
        assertEquals(16, conversationId.length());
    }

    @Test
    void generateGroupConversationId_shouldDifferFromDirectConversationIds() {
        long userId1 = 1000L;
        long userId2 = 2000L;
        long groupId = 1000L;

        String directId = ConversationUtils.generateConversationId(userId1, userId2);
        String groupId1 = ConversationUtils.generateGroupConversationId(groupId);

        assertNotEquals(directId, groupId1);
    }
}
