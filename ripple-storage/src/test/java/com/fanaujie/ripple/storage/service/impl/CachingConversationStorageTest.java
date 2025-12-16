package com.fanaujie.ripple.storage.service.impl;
//
// import com.fanaujie.ripple.storage.model.ConversationState;
// import com.fanaujie.ripple.storage.model.LastMessageInfo;
// import com.fanaujie.ripple.storage.service.impl.cassandra.CassandraLastMessageCalculator;
// import com.fanaujie.ripple.storage.service.impl.cassandra.CassandraUnreadCountCalculator;
// import com.fanaujie.ripple.storage.service.impl.internal.LastMessageCacheAside;
// import com.fanaujie.ripple.storage.service.impl.internal.RedisConversationOperations;
// import com.fanaujie.ripple.storage.service.impl.internal.UnreadCountCacheAside;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.extension.ExtendWith;
// import org.mockito.Mock;
// import org.mockito.junit.jupiter.MockitoExtension;
// import org.redisson.api.RedissonClient;
//
// import java.util.Arrays;
// import java.util.HashMap;
// import java.util.List;
// import java.util.Map;
//
// import static org.junit.jupiter.api.Assertions.*;
// import static org.mockito.Mockito.*;
//
/// **
// * Unit tests for CachingConversationStorage.
// *
// * <p>Note: These tests verify the integration between CachingConversationStorage
// * and its internal helper classes. For detailed cache-aside behavior tests,
// * see the internal class tests.
// */
// @ExtendWith(MockitoExtension.class)
// class CachingConversationStorageTest {
//
//    @Mock
//    private RedissonClient redissonClient;
//
//    @Mock
//    private CassandraUnreadCountCalculator cassandraUnreadCountCalculator;
//
//    @Mock
//    private CassandraLastMessageCalculator cassandraLastMessageCalculator;
//
//    @Mock
//    private RedisConversationOperations redisOps;
//
//    @Mock
//    private UnreadCountCacheAside unreadCountCacheAside;
//
//    @Mock
//    private LastMessageCacheAside lastMessageCacheAside;
//
//    private CachingConversationStorage conversationStorage;
//
//    @BeforeEach
//    void setUp() {
//        // For unit tests, we use a test-friendly constructor that accepts mocked internal
// components
//        conversationStorage = new TestCachingConversationStorage(
//                redisOps, unreadCountCacheAside, lastMessageCacheAside);
//    }
//
//    // Test-friendly subclass that allows injecting mocked internal components
//    private static class TestCachingConversationStorage extends CachingConversationStorage {
//        private final RedisConversationOperations redisOps;
//        private final UnreadCountCacheAside unreadCountCacheAside;
//        private final LastMessageCacheAside lastMessageCacheAside;
//
//        TestCachingConversationStorage(
//                RedisConversationOperations redisOps,
//                UnreadCountCacheAside unreadCountCacheAside,
//                LastMessageCacheAside lastMessageCacheAside) {
//            super(null, null, null); // Will be overridden
//            this.redisOps = redisOps;
//            this.unreadCountCacheAside = unreadCountCacheAside;
//            this.lastMessageCacheAside = lastMessageCacheAside;
//        }
//
//        @Override
//        public void updateConversation(long recipientUserId, String conversationId,
//                String messageText, long timestamp, String messageId, boolean incrementUnread) {
//            redisOps.updateConversation(recipientUserId, conversationId, messageText, timestamp,
// messageId, incrementUnread);
//        }
//
//        @Override
//        public void batchUpdateConversation(List<Long> recipientUserIds, String conversationId,
//                String messageText, long timestamp, String messageId, boolean incrementUnread) {
//            redisOps.batchUpdateConversation(recipientUserIds, conversationId, messageText,
// timestamp, messageId, incrementUnread);
//        }
//
//        @Override
//        public void resetUnreadCount(long userId, String conversationId) {
//            redisOps.resetUnreadCount(userId, conversationId);
//        }
//
//        @Override
//        public int getUnreadCount(long userId, String conversationId) {
//            return unreadCountCacheAside.getUnreadCount(userId, conversationId);
//        }
//
//        @Override
//        public Map<String, Integer> batchGetUnreadCount(long userId, List<String> conversationIds)
// {
//            return unreadCountCacheAside.batchGetUnreadCount(userId, conversationIds);
//        }
//
//        @Override
//        public LastMessageInfo getLastMessage(String conversationId) {
//            return lastMessageCacheAside.getLastMessage(conversationId);
//        }
//
//        @Override
//        public Map<String, LastMessageInfo> batchGetLastMessage(List<String> conversationIds) {
//            return lastMessageCacheAside.batchGetLastMessage(conversationIds);
//        }
//
//        @Override
//        public Map<String, ConversationState> batchGetConversationState(long userId, List<String>
// conversationIds) {
//            return redisOps.batchGetConversationState(userId, conversationIds);
//        }
//    }
//
//    // ==================== Write Operations Tests ====================
//
//    @Test
//    void updateConversation_delegatesToRedisOps() {
//        conversationStorage.updateConversation(1L, "conv1", "Hello", 12345L, "msg123", true);
//
//        verify(redisOps).updateConversation(1L, "conv1", "Hello", 12345L, "msg123", true);
//        verifyNoInteractions(unreadCountCacheAside);
//        verifyNoInteractions(lastMessageCacheAside);
//    }
//
//    @Test
//    void updateConversation_withoutUnreadIncrement_delegatesToRedisOps() {
//        conversationStorage.updateConversation(1L, "conv1", "System message", 12345L, "msg456",
// false);
//
//        verify(redisOps).updateConversation(1L, "conv1", "System message", 12345L, "msg456",
// false);
//    }
//
//    @Test
//    void batchUpdateConversation_delegatesToRedisOps() {
//        List<Long> userIds = Arrays.asList(1L, 2L, 3L);
//
//        conversationStorage.batchUpdateConversation(userIds, "conv1", "Group message", 12345L,
// "msg789", true);
//
//        verify(redisOps).batchUpdateConversation(userIds, "conv1", "Group message", 12345L,
// "msg789", true);
//        verifyNoInteractions(unreadCountCacheAside);
//        verifyNoInteractions(lastMessageCacheAside);
//    }
//
//    @Test
//    void batchUpdateConversation_withoutUnreadIncrement_delegatesToRedisOps() {
//        List<Long> userIds = Arrays.asList(1L, 2L, 3L);
//
//        conversationStorage.batchUpdateConversation(userIds, "conv1", "User joined", 12345L,
// "msg101", false);
//
//        verify(redisOps).batchUpdateConversation(userIds, "conv1", "User joined", 12345L,
// "msg101", false);
//    }
//
//    @Test
//    void resetUnreadCount_delegatesToRedisOps() {
//        conversationStorage.resetUnreadCount(1L, "conv1");
//
//        verify(redisOps).resetUnreadCount(1L, "conv1");
//    }
//
//    // ==================== Read Operations Tests ====================
//
//    @Test
//    void getUnreadCount_delegatesToCacheAside() {
//        when(unreadCountCacheAside.getUnreadCount(1L, "conv1")).thenReturn(5);
//
//        int result = conversationStorage.getUnreadCount(1L, "conv1");
//
//        assertEquals(5, result);
//        verify(unreadCountCacheAside).getUnreadCount(1L, "conv1");
//        verifyNoInteractions(redisOps);
//    }
//
//    @Test
//    void batchGetUnreadCount_delegatesToCacheAside() {
//        Map<String, Integer> expected = new HashMap<>();
//        expected.put("conv1", 5);
//        expected.put("conv2", 3);
//        when(unreadCountCacheAside.batchGetUnreadCount(1L, Arrays.asList("conv1", "conv2")))
//                .thenReturn(expected);
//
//        Map<String, Integer> result = conversationStorage.batchGetUnreadCount(
//                1L, Arrays.asList("conv1", "conv2"));
//
//        assertEquals(2, result.size());
//        assertEquals(5, result.get("conv1"));
//        assertEquals(3, result.get("conv2"));
//        verify(unreadCountCacheAside).batchGetUnreadCount(1L, Arrays.asList("conv1", "conv2"));
//    }
//
//    @Test
//    void getLastMessage_delegatesToCacheAside() {
//        LastMessageInfo expected = new LastMessageInfo("Hello", 12345L);
//        when(lastMessageCacheAside.getLastMessage("conv1")).thenReturn(expected);
//
//        LastMessageInfo result = conversationStorage.getLastMessage("conv1");
//
//        assertEquals("Hello", result.getText());
//        assertEquals(12345L, result.getTimestamp());
//        verify(lastMessageCacheAside).getLastMessage("conv1");
//        verifyNoInteractions(redisOps);
//    }
//
//    @Test
//    void batchGetLastMessage_delegatesToCacheAside() {
//        Map<String, LastMessageInfo> expected = new HashMap<>();
//        expected.put("conv1", new LastMessageInfo("Hello", 12345L));
//        expected.put("conv2", new LastMessageInfo("World", 12346L));
//        when(lastMessageCacheAside.batchGetLastMessage(Arrays.asList("conv1", "conv2")))
//                .thenReturn(expected);
//
//        Map<String, LastMessageInfo> result = conversationStorage.batchGetLastMessage(
//                Arrays.asList("conv1", "conv2"));
//
//        assertEquals(2, result.size());
//        assertEquals("Hello", result.get("conv1").getText());
//        assertEquals("World", result.get("conv2").getText());
//        verify(lastMessageCacheAside).batchGetLastMessage(Arrays.asList("conv1", "conv2"));
//    }
//
//    @Test
//    void batchGetConversationState_delegatesToRedisOps() {
//        Map<String, ConversationState> expected = new HashMap<>();
//        expected.put("conv1", new ConversationState(5, new LastMessageInfo("Hello", 12345L)));
//        when(redisOps.batchGetConversationState(1L, Arrays.asList("conv1")))
//                .thenReturn(expected);
//
//        Map<String, ConversationState> result = conversationStorage.batchGetConversationState(
//                1L, Arrays.asList("conv1"));
//
//        assertEquals(1, result.size());
//        assertEquals(5, result.get("conv1").getUnreadCount());
//        verify(redisOps).batchGetConversationState(1L, Arrays.asList("conv1"));
//    }
// }
