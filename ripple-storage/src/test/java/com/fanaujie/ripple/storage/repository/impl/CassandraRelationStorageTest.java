package com.fanaujie.ripple.storage.repository.impl;

import com.datastax.driver.core.utils.UUIDs;
import com.datastax.oss.driver.api.core.CqlSession;
import com.fanaujie.ripple.storage.exception.BlockAlreadyExistsException;
import com.fanaujie.ripple.storage.exception.InvalidVersionException;
import com.fanaujie.ripple.storage.exception.NotFoundBlockException;
import com.fanaujie.ripple.storage.exception.NotFoundRelationException;
import com.fanaujie.ripple.storage.exception.RelationAlreadyExistsException;
import com.fanaujie.ripple.storage.model.PagedRelationResult;
import com.fanaujie.ripple.storage.model.Relation;
import com.fanaujie.ripple.storage.model.RelationFlags;
import com.fanaujie.ripple.storage.model.RelationVersionRecord;
import com.fanaujie.ripple.storage.model.UserProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class CassandraRelationStorageTest {

    @Container
    CassandraContainer cassandraContainer =
            new CassandraContainer("cassandra:5.0.5").withInitScript("ripple.cql");

    private CqlSession session;
    private CassandraRelationRepository relationRepository;

    @BeforeEach
    void setUp() throws IOException {
        this.session =
                CqlSession.builder()
                        .addContactPoint(cassandraContainer.getContactPoint())
                        .withLocalDatacenter(cassandraContainer.getLocalDatacenter())
                        .build();

        relationRepository = new CassandraRelationRepository(session);
    }

    @AfterEach
    void tearDown() {
        if (session != null) {
            session.execute("TRUNCATE ripple.user_relations");
            session.execute("TRUNCATE ripple.user_relation_version");
            session.close();
        }
    }

    // ==================== addFriend Tests ====================

    @Test
    void testAddFriend() throws RelationAlreadyExistsException {
        long initiatorId = 1000L;
        UserProfile friendProfile = new UserProfile(2000L, "friend1", "Friend One", "avatar1.jpg");

        relationRepository.addFriend(initiatorId, friendProfile);

        // Verify friend was added
        assertTrue(relationRepository.isFriends(initiatorId, friendProfile.getUserId()));

        // Verify relation details
        PagedRelationResult result = relationRepository.getRelations(initiatorId, null, 10);
        assertEquals(1, result.getRelations().size());
        Relation relation = result.getRelations().get(0);
        assertEquals(initiatorId, relation.getSourceUserId());
        assertEquals(2000L, relation.getRelationUserId());
        assertEquals("Friend One", relation.getRelationNickName());
        assertEquals("avatar1.jpg", relation.getRelationAvatar());
        assertEquals("Friend One", relation.getRelationRemarkName());
        assertTrue(RelationFlags.FRIEND.isSet((byte) relation.getRelationFlags()));
    }

    @Test
    void testAddFriendAlreadyExists() throws RelationAlreadyExistsException {
        long initiatorId = 1001L;
        UserProfile friendProfile = new UserProfile(2001L, "friend2", "Friend Two", "avatar2.jpg");

        // Add friend first time
        relationRepository.addFriend(initiatorId, friendProfile);

        // Try to add same friend again
        assertThrows(
                RelationAlreadyExistsException.class,
                () -> {
                    relationRepository.addFriend(initiatorId, friendProfile);
                });
    }

    @Test
    void testAddMultipleFriends() throws RelationAlreadyExistsException {
        long initiatorId = 1002L;
        UserProfile friend1 = new UserProfile(2002L, "friend3", "Friend Three", "avatar3.jpg");
        UserProfile friend2 = new UserProfile(2003L, "friend4", "Friend Four", "avatar4.jpg");
        UserProfile friend3 = new UserProfile(2004L, "friend5", "Friend Five", "avatar5.jpg");

        relationRepository.addFriend(initiatorId, friend1);
        relationRepository.addFriend(initiatorId, friend2);
        relationRepository.addFriend(initiatorId, friend3);

        // Verify all friends were added
        List<Long> friendIds = relationRepository.getFriendIds(initiatorId);
        assertEquals(3, friendIds.size());
        assertTrue(friendIds.contains(2002L));
        assertTrue(friendIds.contains(2003L));
        assertTrue(friendIds.contains(2004L));
    }

    @Test
    void testAddFriendUnblocksAndUnhides()
            throws BlockAlreadyExistsException,
                    NotFoundBlockException,
                    RelationAlreadyExistsException {
        long userId = 1022L;
        long blockedUserId = 2040L;
        UserProfile blockedProfile =
                new UserProfile(blockedUserId, "blocked5", "Blocked User 5", "blocked5.jpg");

        // Block user
        relationRepository.addBlock(userId, blockedUserId, false, blockedProfile);

        // Hide the block
        relationRepository.hideBlock(userId, blockedUserId);

        // Verify BLOCKED and HIDDEN flags are set, FRIEND is not
        PagedRelationResult beforeAddFriend = relationRepository.getRelations(userId, null, 10);
        assertEquals(1, beforeAddFriend.getRelations().size());
        Relation relationBefore = beforeAddFriend.getRelations().get(0);
        assertFalse(RelationFlags.FRIEND.isSet((byte) relationBefore.getRelationFlags()));
        assertTrue(RelationFlags.BLOCKED.isSet((byte) relationBefore.getRelationFlags()));
        assertTrue(RelationFlags.HIDDEN.isSet((byte) relationBefore.getRelationFlags()));

        // Add as friend (should unblock and unhide)
        relationRepository.addFriend(userId, blockedProfile);

        // Verify only FRIEND flag is set, BLOCKED and HIDDEN are cleared
        PagedRelationResult afterAddFriend = relationRepository.getRelations(userId, null, 10);
        assertEquals(1, afterAddFriend.getRelations().size());
        Relation relationAfter = afterAddFriend.getRelations().get(0);
        assertTrue(RelationFlags.FRIEND.isSet((byte) relationAfter.getRelationFlags()));
        assertFalse(RelationFlags.BLOCKED.isSet((byte) relationAfter.getRelationFlags()));
        assertFalse(RelationFlags.HIDDEN.isSet((byte) relationAfter.getRelationFlags()));

        // Verify isFriends returns true
        assertTrue(relationRepository.isFriends(userId, blockedUserId));
    }

    // ==================== removeFriend Tests ====================

    @Test
    void testRemoveFriend() throws RelationAlreadyExistsException, NotFoundRelationException {
        long initiatorId = 1003L;
        UserProfile friendProfile = new UserProfile(2005L, "friend6", "Friend Six", "avatar6.jpg");

        // Add friend first
        relationRepository.addFriend(initiatorId, friendProfile);
        assertTrue(relationRepository.isFriends(initiatorId, friendProfile.getUserId()));

        // Remove friend
        relationRepository.removeFriend(initiatorId, friendProfile.getUserId());
        assertFalse(relationRepository.isFriends(initiatorId, friendProfile.getUserId()));

        // Verify relation is deleted
        PagedRelationResult result = relationRepository.getRelations(initiatorId, null, 10);
        assertTrue(result.getRelations().isEmpty());
    }

    @Test
    void testRemoveFriendNotFound() {
        long initiatorId = 1004L;
        long friendId = 2006L;

        assertThrows(
                NotFoundRelationException.class,
                () -> {
                    relationRepository.removeFriend(initiatorId, friendId);
                });
    }

    // ==================== updateRelationRemarkName Tests ====================

    @Test
    void testUpdateRelationRemarkName()
            throws RelationAlreadyExistsException, NotFoundRelationException {
        long sourceUserId = 1005L;
        UserProfile friendProfile =
                new UserProfile(2007L, "friend7", "Friend Seven", "avatar7.jpg");
        String newRemarkName = "My Best Friend";

        // Add friend first
        relationRepository.addFriend(sourceUserId, friendProfile);

        // Update remark name
        relationRepository.updateRelationRemarkName(
                sourceUserId, friendProfile.getUserId(), newRemarkName);

        // Verify remark name was updated
        PagedRelationResult result = relationRepository.getRelations(sourceUserId, null, 10);
        assertEquals(1, result.getRelations().size());
        assertEquals(newRemarkName, result.getRelations().get(0).getRelationRemarkName());
        // Verify other fields remain unchanged
        assertEquals("Friend Seven", result.getRelations().get(0).getRelationNickName());
        assertEquals("avatar7.jpg", result.getRelations().get(0).getRelationAvatar());
    }

    @Test
    void testUpdateRelationRemarkNameNotFound() {
        assertThrows(
                NotFoundRelationException.class,
                () -> {
                    relationRepository.updateRelationRemarkName(9999L, 8888L, "New Remark");
                });
    }

    // ==================== isFriends Tests ====================

    @Test
    void testIsFriends() throws RelationAlreadyExistsException {
        long userId1 = 1006L;
        UserProfile friendProfile =
                new UserProfile(2008L, "friend8", "Friend Eight", "avatar8.jpg");

        // Before adding friend
        assertFalse(relationRepository.isFriends(userId1, friendProfile.getUserId()));

        // Add friend
        relationRepository.addFriend(userId1, friendProfile);

        // After adding friend
        assertTrue(relationRepository.isFriends(userId1, friendProfile.getUserId()));
    }

    @Test
    void testIsFriendsNotExists() {
        assertFalse(relationRepository.isFriends(9999L, 8888L));
    }

    // ==================== addBlock Tests ====================

    @Test
    void testAddBlockNonFriend() throws BlockAlreadyExistsException {
        long userId = 1007L;
        long blockedUserId = 2009L;
        UserProfile blockedProfile =
                new UserProfile(blockedUserId, "blocked1", "Blocked User", "blocked1.jpg");

        // Add block for non-friend
        relationRepository.addBlock(userId, blockedUserId, false, blockedProfile);

        // Verify block was added
        PagedRelationResult result = relationRepository.getRelations(userId, null, 10);
        assertEquals(1, result.getRelations().size());
        Relation relation = result.getRelations().get(0);
        assertEquals(blockedUserId, relation.getRelationUserId());
        assertTrue(RelationFlags.BLOCKED.isSet((byte) relation.getRelationFlags()));
        assertFalse(RelationFlags.FRIEND.isSet((byte) relation.getRelationFlags()));
    }

    @Test
    void testAddBlockFriend() throws RelationAlreadyExistsException, BlockAlreadyExistsException {
        long userId = 1008L;
        UserProfile friendProfile = new UserProfile(2010L, "friend9", "Friend Nine", "avatar9.jpg");

        // Add as friend first
        relationRepository.addFriend(userId, friendProfile);
        assertTrue(relationRepository.isFriends(userId, friendProfile.getUserId()));

        // Block the friend
        relationRepository.addBlock(userId, friendProfile.getUserId(), true, friendProfile);

        // Verify both FRIEND and BLOCKED flags are set
        PagedRelationResult result = relationRepository.getRelations(userId, null, 10);
        assertEquals(1, result.getRelations().size());
        Relation relation = result.getRelations().get(0);
        assertTrue(RelationFlags.FRIEND.isSet((byte) relation.getRelationFlags()));
        assertTrue(RelationFlags.BLOCKED.isSet((byte) relation.getRelationFlags()));
    }

    @Test
    void testAddBlockAlreadyExists() throws BlockAlreadyExistsException {
        long userId = 1009L;
        long blockedUserId = 2011L;
        UserProfile blockedProfile =
                new UserProfile(blockedUserId, "blocked2", "Blocked User 2", "blocked2.jpg");

        // Add block first time
        relationRepository.addBlock(userId, blockedUserId, false, blockedProfile);

        // Try to block again
        assertThrows(
                BlockAlreadyExistsException.class,
                () -> {
                    relationRepository.addBlock(userId, blockedUserId, false, blockedProfile);
                });
    }

    // ==================== removeBlock Tests ====================

    @Test
    void testRemoveBlockNonFriend() throws BlockAlreadyExistsException, NotFoundBlockException {
        long userId = 1010L;
        long blockedUserId = 2012L;
        UserProfile blockedProfile =
                new UserProfile(blockedUserId, "blocked3", "Blocked User 3", "blocked3.jpg");

        // Add block
        relationRepository.addBlock(userId, blockedUserId, false, blockedProfile);

        // Remove block
        relationRepository.removeBlock(userId, blockedUserId);

        // Verify relation is deleted (no friend flag, only block flag was set)
        PagedRelationResult result = relationRepository.getRelations(userId, null, 10);
        assertTrue(result.getRelations().isEmpty());
    }

    @Test
    void testRemoveBlockFriend()
            throws RelationAlreadyExistsException,
                    BlockAlreadyExistsException,
                    NotFoundBlockException {
        long userId = 1011L;
        UserProfile friendProfile =
                new UserProfile(2013L, "friend10", "Friend Ten", "avatar10.jpg");

        // Add as friend
        relationRepository.addFriend(userId, friendProfile);

        // Block the friend
        relationRepository.addBlock(userId, friendProfile.getUserId(), true, friendProfile);

        // Remove block
        relationRepository.removeBlock(userId, friendProfile.getUserId());

        // Verify FRIEND flag remains but BLOCKED flag is cleared
        PagedRelationResult result = relationRepository.getRelations(userId, null, 10);
        assertEquals(1, result.getRelations().size());
        Relation relation = result.getRelations().get(0);
        assertTrue(RelationFlags.FRIEND.isSet((byte) relation.getRelationFlags()));
        assertFalse(RelationFlags.BLOCKED.isSet((byte) relation.getRelationFlags()));
    }

    @Test
    void testRemoveBlockNotFound() {
        assertThrows(
                NotFoundBlockException.class,
                () -> {
                    relationRepository.removeBlock(9999L, 8888L);
                });
    }

    // ==================== hideBlock Tests ====================

    @Test
    void testHideBlock() throws BlockAlreadyExistsException, NotFoundBlockException {
        long userId = 1012L;
        long blockedUserId = 2014L;
        UserProfile blockedProfile =
                new UserProfile(blockedUserId, "blocked4", "Blocked User 4", "blocked4.jpg");

        // Add block
        relationRepository.addBlock(userId, blockedUserId, false, blockedProfile);

        // Hide block
        relationRepository.hideBlock(userId, blockedUserId);

        // Verify HIDDEN flag is set
        PagedRelationResult result = relationRepository.getRelations(userId, null, 10);
        assertEquals(1, result.getRelations().size());
        Relation relation = result.getRelations().get(0);
        assertTrue(RelationFlags.BLOCKED.isSet((byte) relation.getRelationFlags()));
        assertTrue(RelationFlags.HIDDEN.isSet((byte) relation.getRelationFlags()));
    }

    @Test
    void testHideBlockNotFound() {
        assertThrows(
                NotFoundBlockException.class,
                () -> {
                    relationRepository.hideBlock(9999L, 8888L);
                });
    }

    @Test
    void testHideBlockNonBlocked() throws RelationAlreadyExistsException {
        long userId = 1013L;
        UserProfile friendProfile =
                new UserProfile(2015L, "friend11", "Friend Eleven", "avatar11.jpg");

        // Add friend (not blocked)
        relationRepository.addFriend(userId, friendProfile);

        // Try to hide non-blocked relation
        assertThrows(
                NotFoundBlockException.class,
                () -> {
                    relationRepository.hideBlock(userId, friendProfile.getUserId());
                });
    }

    @Test
    void testHideBlockRemovesFriendFlag()
            throws RelationAlreadyExistsException,
                    BlockAlreadyExistsException,
                    NotFoundBlockException {
        long userId = 1021L;
        UserProfile friendProfile =
                new UserProfile(2039L, "friend17", "Friend Seventeen", "avatar17.jpg");

        // Add as friend first
        relationRepository.addFriend(userId, friendProfile);

        // Block the friend (now has both FRIEND and BLOCKED flags)
        relationRepository.addBlock(userId, friendProfile.getUserId(), true, friendProfile);

        // Verify both flags are set
        PagedRelationResult beforeHide = relationRepository.getRelations(userId, null, 10);
        assertEquals(1, beforeHide.getRelations().size());
        Relation relationBefore = beforeHide.getRelations().get(0);
        assertTrue(RelationFlags.FRIEND.isSet((byte) relationBefore.getRelationFlags()));
        assertTrue(RelationFlags.BLOCKED.isSet((byte) relationBefore.getRelationFlags()));

        // Hide the block
        relationRepository.hideBlock(userId, friendProfile.getUserId());

        // Verify FRIEND flag is cleared, but BLOCKED and HIDDEN flags are set
        PagedRelationResult afterHide = relationRepository.getRelations(userId, null, 10);
        assertEquals(1, afterHide.getRelations().size());
        Relation relationAfter = afterHide.getRelations().get(0);
        assertFalse(RelationFlags.FRIEND.isSet((byte) relationAfter.getRelationFlags()));
        assertTrue(RelationFlags.BLOCKED.isSet((byte) relationAfter.getRelationFlags()));
        assertTrue(RelationFlags.HIDDEN.isSet((byte) relationAfter.getRelationFlags()));
    }

    // ==================== getFriendIds Tests ====================

    @Test
    void testGetFriendIds() throws RelationAlreadyExistsException {
        long userId = 1014L;
        UserProfile friend1 = new UserProfile(2016L, "friend12", "Friend Twelve", "avatar12.jpg");
        UserProfile friend2 = new UserProfile(2017L, "friend13", "Friend Thirteen", "avatar13.jpg");
        UserProfile friend3 = new UserProfile(2018L, "friend14", "Friend Fourteen", "avatar14.jpg");

        relationRepository.addFriend(userId, friend1);
        relationRepository.addFriend(userId, friend2);
        relationRepository.addFriend(userId, friend3);

        List<Long> friendIds = relationRepository.getFriendIds(userId);
        assertEquals(3, friendIds.size());
        assertTrue(friendIds.contains(2016L));
        assertTrue(friendIds.contains(2017L));
        assertTrue(friendIds.contains(2018L));
    }

    @Test
    void testGetFriendIdsEmpty() {
        List<Long> friendIds = relationRepository.getFriendIds(9999L);
        assertTrue(friendIds.isEmpty());
    }

    @Test
    void testGetFriendIdsExcludesBlocked()
            throws RelationAlreadyExistsException, BlockAlreadyExistsException {
        long userId = 1015L;
        UserProfile friend1 = new UserProfile(2019L, "friend15", "Friend Fifteen", "avatar15.jpg");
        UserProfile friend2 = new UserProfile(2020L, "friend16", "Friend Sixteen", "avatar16.jpg");

        // Add two friends
        relationRepository.addFriend(userId, friend1);
        relationRepository.addFriend(userId, friend2);

        // Block one friend (should still have FRIEND flag)
        relationRepository.addBlock(userId, friend2.getUserId(), true, friend2);

        // getFriendIds should return both (FRIEND flag is still set even when blocked)
        List<Long> friendIds = relationRepository.getFriendIds(userId);
        assertEquals(2, friendIds.size());
        assertTrue(friendIds.contains(2019L));
        assertTrue(friendIds.contains(2020L));
    }

    // ==================== getRelations Tests (Pagination) ====================

    @Test
    void testGetRelationsFirstPage() throws RelationAlreadyExistsException {
        long sourceUserId = 1016L;

        // Add multiple friends
        for (int i = 0; i < 5; i++) {
            long friendId = 2021L + i;
            UserProfile friend =
                    new UserProfile(friendId, "friend" + i, "Friend " + i, "avatar" + i + ".jpg");
            relationRepository.addFriend(sourceUserId, friend);
        }

        PagedRelationResult result = relationRepository.getRelations(sourceUserId, null, 3);

        assertNotNull(result);
        assertEquals(3, result.getRelations().size());
        assertTrue(result.isHasMore());
        assertNotNull(result.getNextPageToken());

        // Relations should be ordered by relation_user_id ASC
        List<Relation> relations = result.getRelations();
        assertEquals(2021L, relations.get(0).getRelationUserId());
        assertEquals(2022L, relations.get(1).getRelationUserId());
        assertEquals(2023L, relations.get(2).getRelationUserId());
    }

    @Test
    void testGetRelationsWithPagination() throws RelationAlreadyExistsException {
        long sourceUserId = 1017L;

        // Add multiple friends
        for (int i = 0; i < 7; i++) {
            long friendId = 2026L + i;
            UserProfile friend =
                    new UserProfile(friendId, "friend" + i, "Friend " + i, "avatar" + i + ".jpg");
            relationRepository.addFriend(sourceUserId, friend);
        }

        // First page
        PagedRelationResult firstPage = relationRepository.getRelations(sourceUserId, null, 3);
        assertEquals(3, firstPage.getRelations().size());
        assertTrue(firstPage.isHasMore());
        assertNotNull(firstPage.getNextPageToken());

        // Second page
        PagedRelationResult secondPage =
                relationRepository.getRelations(sourceUserId, firstPage.getNextPageToken(), 3);
        assertEquals(3, secondPage.getRelations().size());
        assertTrue(secondPage.isHasMore());
        assertNotNull(secondPage.getNextPageToken());

        // Third page (last)
        PagedRelationResult thirdPage =
                relationRepository.getRelations(sourceUserId, secondPage.getNextPageToken(), 3);
        assertEquals(1, thirdPage.getRelations().size());
        assertFalse(thirdPage.isHasMore());
        assertNull(thirdPage.getNextPageToken());
    }

    @Test
    void testGetRelationsEmpty() {
        long sourceUserId = 1018L;

        PagedRelationResult result = relationRepository.getRelations(sourceUserId, null, 10);

        assertNotNull(result);
        assertTrue(result.getRelations().isEmpty());
        assertFalse(result.isHasMore());
        assertNull(result.getNextPageToken());
    }

    @Test
    void testGetRelationsExactPageSize() throws RelationAlreadyExistsException {
        long sourceUserId = 1019L;

        // Add exactly pageSize friends
        for (int i = 0; i < 3; i++) {
            long friendId = 2033L + i;
            UserProfile friend =
                    new UserProfile(friendId, "friend" + i, "Friend " + i, "avatar" + i + ".jpg");
            relationRepository.addFriend(sourceUserId, friend);
        }

        PagedRelationResult result = relationRepository.getRelations(sourceUserId, null, 3);

        assertEquals(3, result.getRelations().size());
        assertFalse(result.isHasMore());
        assertNull(result.getNextPageToken());
    }

    @Test
    void testGetRelationsIncludesAllFlags()
            throws RelationAlreadyExistsException, BlockAlreadyExistsException {
        long sourceUserId = 1020L;
        UserProfile friend1 = new UserProfile(2036L, "friend_a", "Friend A", "avatarA.jpg");
        UserProfile friend2 = new UserProfile(2037L, "friend_b", "Friend B", "avatarB.jpg");
        UserProfile blocked = new UserProfile(2038L, "blocked_c", "Blocked C", "blockedC.jpg");

        // Add regular friend
        relationRepository.addFriend(sourceUserId, friend1);

        // Add friend and block them
        relationRepository.addFriend(sourceUserId, friend2);
        relationRepository.addBlock(sourceUserId, friend2.getUserId(), true, friend2);

        // Add blocked only
        relationRepository.addBlock(sourceUserId, blocked.getUserId(), false, blocked);

        PagedRelationResult result = relationRepository.getRelations(sourceUserId, null, 10);
        assertEquals(3, result.getRelations().size());

        // Check flags for each relation
        for (Relation relation : result.getRelations()) {
            if (relation.getRelationUserId() == 2036L) {
                // Regular friend
                assertTrue(RelationFlags.FRIEND.isSet((byte) relation.getRelationFlags()));
                assertFalse(RelationFlags.BLOCKED.isSet((byte) relation.getRelationFlags()));
            } else if (relation.getRelationUserId() == 2037L) {
                // Friend and blocked
                assertTrue(RelationFlags.FRIEND.isSet((byte) relation.getRelationFlags()));
                assertTrue(RelationFlags.BLOCKED.isSet((byte) relation.getRelationFlags()));
            } else if (relation.getRelationUserId() == 2038L) {
                // Blocked only
                assertFalse(RelationFlags.FRIEND.isSet((byte) relation.getRelationFlags()));
                assertTrue(RelationFlags.BLOCKED.isSet((byte) relation.getRelationFlags()));
            }
        }
    }

    @Test
    void testInvalidPageToken() {
        assertThrows(
                NumberFormatException.class,
                () -> {
                    relationRepository.getRelations(1021L, "invalid_token", 10);
                });
    }

    // ==================== getRelationChanges Tests ====================

    @Test
    void testGetRelationChangesWithValidVersion()
            throws RelationAlreadyExistsException,
                    InvalidVersionException,
                    NotFoundRelationException,
                    InterruptedException {
        long userId = 3000L;
        UserProfile friend1 = new UserProfile(4000L, "friend100", "Friend 100", "avatar100.jpg");
        UserProfile friend2 = new UserProfile(4001L, "friend101", "Friend 101", "avatar101.jpg");

        // Capture version before adding any friends
        UUID versionBeforeFriend1 = UUIDs.timeBased();
        Thread.sleep(10);

        // Add first friend
        relationRepository.addFriend(userId, friend1);
        Thread.sleep(10);

        // Get changes after versionBeforeFriend1
        List<RelationVersionRecord> changes1 =
                relationRepository.getRelationChanges(userId, versionBeforeFriend1.toString(), 10);
        assertEquals(1, changes1.size());
        assertEquals(4000L, changes1.get(0).getRelationUserId());
        String firstVersionTimestamp = changes1.get(0).getVersion();

        // Capture version before adding second friend
        UUID versionBeforeFriend2 = UUIDs.timeBased();
        Thread.sleep(10);

        // Add second friend
        relationRepository.addFriend(userId, friend2);
        Thread.sleep(10);

        // Query changes after versionBeforeFriend1 (should get both friends)
        List<RelationVersionRecord> changes2 =
                relationRepository.getRelationChanges(userId, versionBeforeFriend1.toString(), 10);
        assertEquals(2, changes2.size());
        assertEquals(4000L, changes2.get(0).getRelationUserId());
        assertEquals(4001L, changes2.get(1).getRelationUserId());

        // Verify versions are in ascending order (timestamp comparison)
        long timestamp1 = Long.parseLong(changes2.get(0).getVersion());
        long timestamp2 = Long.parseLong(changes2.get(1).getVersion());
        assertTrue(timestamp1 < timestamp2, "Versions should be in ascending order");

        // Capture version before update
        UUID versionBeforeUpdate = UUIDs.timeBased();
        Thread.sleep(10);

        // Update remark name
        relationRepository.updateRelationRemarkName(userId, 4001L, "Best Friend");

        // Query changes after versionBeforeUpdate
        List<RelationVersionRecord> changes3 =
                relationRepository.getRelationChanges(userId, versionBeforeUpdate.toString(), 10);
        assertEquals(1, changes3.size());
        assertEquals(4001L, changes3.get(0).getRelationUserId());
        assertEquals("Best Friend", changes3.get(0).getRemarkName());
    }

    @Test
    void testGetRelationChangesWithNoChanges() throws InvalidVersionException {
        long userId = 3001L;

        // Query with a version, but no changes exist
        List<RelationVersionRecord> changes =
                relationRepository.getRelationChanges(userId, UUIDs.startOf(0).toString(), 10);
        assertEquals(0, changes.size());
    }

    @Test
    void testGetRelationChangesWithLimit()
            throws RelationAlreadyExistsException, InvalidVersionException, InterruptedException {
        long userId = 3002L;

        // Add multiple friends to create multiple version records
        for (int i = 0; i < 5; i++) {
            UserProfile friend =
                    new UserProfile(5000L + i, "friend" + i, "Friend " + i, "avatar" + i + ".jpg");
            relationRepository.addFriend(userId, friend);
            Thread.sleep(10); // Ensure different timestamps
        }

        // Query with limit of 3
        List<RelationVersionRecord> changes =
                relationRepository.getRelationChanges(userId, UUIDs.startOf(0).toString(), 3);
        assertEquals(3, changes.size());

        // Verify versions (timestamps) are in ascending order
        for (int i = 0; i < changes.size() - 1; i++) {
            long currentTimestamp = Long.parseLong(changes.get(i).getVersion());
            long nextTimestamp = Long.parseLong(changes.get(i + 1).getVersion());
            assertTrue(
                    currentTimestamp < nextTimestamp,
                    "Version at index " + i + " should be less than version at index " + (i + 1));
        }
    }

    @Test
    void testGetRelationChangesWithNullVersion() {
        long userId = 3003L;

        InvalidVersionException exception =
                assertThrows(
                        InvalidVersionException.class,
                        () -> {
                            relationRepository.getRelationChanges(userId, null, 10);
                        });
        assertTrue(exception.getMessage().contains("cannot be null or empty"));
    }

    @Test
    void testGetRelationChangesWithEmptyVersion() {
        long userId = 3004L;

        InvalidVersionException exception =
                assertThrows(
                        InvalidVersionException.class,
                        () -> {
                            relationRepository.getRelationChanges(userId, "", 10);
                        });
        assertTrue(exception.getMessage().contains("cannot be null or empty"));
    }

    @Test
    void testGetRelationChangesWithInvalidUUID() {
        long userId = 3005L;

        InvalidVersionException exception =
                assertThrows(
                        InvalidVersionException.class,
                        () -> {
                            relationRepository.getRelationChanges(
                                    userId, "invalid-uuid-format", 10);
                        });
        assertTrue(exception.getMessage().contains("Invalid version format"));
    }

    @Test
    void testGetRelationChangesVersionOrder()
            throws RelationAlreadyExistsException,
                    NotFoundRelationException,
                    InvalidVersionException,
                    InterruptedException {
        long userId = 3006L;
        UserProfile friend = new UserProfile(6000L, "friend200", "Friend 200", "avatar200.jpg");

        // Add friend
        relationRepository.addFriend(userId, friend);
        Thread.sleep(10);

        // Update remark name
        relationRepository.updateRelationRemarkName(userId, 6000L, "Updated Name");
        Thread.sleep(10);

        // Remove friend
        relationRepository.removeFriend(userId, 6000L);

        // Get all changes
        List<RelationVersionRecord> changes =
                relationRepository.getRelationChanges(userId, UUIDs.startOf(0).toString(), 10);
        assertEquals(3, changes.size());

        // Verify order: ADD -> UPDATE -> DELETE
        assertEquals(1, changes.get(0).getOperation()); // ADD
        assertEquals(2, changes.get(1).getOperation()); // UPDATE
        assertEquals(3, changes.get(2).getOperation()); // DELETE

        // Verify versions (timestamps) are in ascending order
        String version1 = changes.get(0).getVersion();
        String version2 = changes.get(1).getVersion();
        String version3 = changes.get(2).getVersion();

        long timestamp1 = Long.parseLong(version1);
        long timestamp2 = Long.parseLong(version2);
        long timestamp3 = Long.parseLong(version3);

        assertTrue(timestamp1 < timestamp2, "Version 1 should be less than version 2");
        assertTrue(timestamp2 < timestamp3, "Version 2 should be less than version 3");
        assertTrue(timestamp1 < timestamp3, "Versions should be in ascending order");
    }
}
