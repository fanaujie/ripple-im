package com.fanaujie.ripple.storage.repository.impl;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.fanaujie.ripple.protobuf.msgapiserver.RelationEvent;
import com.fanaujie.ripple.protobuf.storage.UserIds;
import com.fanaujie.ripple.storage.exception.*;
import com.fanaujie.ripple.storage.model.*;
import com.fanaujie.ripple.storage.service.impl.cassandra.CassandraStorageFacade;
import com.fanaujie.ripple.storage.service.impl.cassandra.CassandraStorageFacadeBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class CassandraRelationStorageTest {

    @Container
    static CassandraContainer<?> cassandraContainer =
            new CassandraContainer<>("cassandra:5.0.2").withInitScript("ripple.cql");

    private CqlSession session;
    private CassandraStorageFacade storageFacade;

    @BeforeEach
    void setUp() {
        this.session =
                CqlSession.builder()
                        .addContactPoint(cassandraContainer.getContactPoint())
                        .withLocalDatacenter(cassandraContainer.getLocalDatacenter())
                        .build();
        storageFacade = new CassandraStorageFacadeBuilder().cqlSession(session).build();
    }

    @AfterEach
    void tearDown() {
        if (session != null) {
            session.execute("TRUNCATE ripple.user");
            session.execute("TRUNCATE ripple.user_profile");
            session.execute("TRUNCATE ripple.user_relations");
            session.execute("TRUNCATE ripple.user_relation_version");
            session.execute("TRUNCATE ripple.user_conversations");
            session.execute("TRUNCATE ripple.user_conversations_version");
            session.close();
        }
    }

    private void createUserProfile(long userId, String account, String nickName, String avatar) {
        User user = new User(userId, account, "password", User.DEFAULT_ROLE_USER, (byte) 0);
        storageFacade.insertUser(user, nickName, avatar);
    }

    // ==================== addFriend Tests ====================

    @Test
    void addFriend_shouldCreateFriendRelation()
            throws NotFoundUserProfileException, RelationAlreadyExistsException {
        long userId = 1001L;
        long friendId = 2001L;
        createUserProfile(userId, "user1", "User One", "avatar1.png");
        createUserProfile(friendId, "friend1", "Friend One", "avatar2.png");

        RelationEvent event =
                RelationEvent.newBuilder().setUserId(userId).setTargetUserId(friendId).build();

        storageFacade.addFriend(event, System.currentTimeMillis());

        Relation relation = storageFacade.getRelationBetweenUser(userId, friendId);
        assertNotNull(relation);
        assertEquals(userId, relation.getSourceUserId());
        assertEquals(friendId, relation.getRelationUserId());
        assertTrue(RelationFlags.FRIEND.isSet(relation.getRelationFlags()));
    }

    @Test
    void addFriend_shouldThrowException_whenAlreadyFriends()
            throws NotFoundUserProfileException, RelationAlreadyExistsException {
        long userId = 1002L;
        long friendId = 2002L;
        createUserProfile(userId, "user2", "User Two", "avatar1.png");
        createUserProfile(friendId, "friend2", "Friend Two", "avatar2.png");

        RelationEvent event =
                RelationEvent.newBuilder().setUserId(userId).setTargetUserId(friendId).build();
        storageFacade.addFriend(event, System.currentTimeMillis());

        assertThrows(RelationAlreadyExistsException.class, () -> storageFacade.addFriend(event, System.currentTimeMillis()));
    }

    @Test
    void addFriend_shouldPopulateFriendInfo()
            throws NotFoundUserProfileException, RelationAlreadyExistsException {
        long userId = 1003L;
        long friendId = 2003L;
        createUserProfile(userId, "user3", "User Three", "avatar1.png");
        createUserProfile(friendId, "friend3", "Friend Three", "friend-avatar.png");

        RelationEvent event =
                RelationEvent.newBuilder().setUserId(userId).setTargetUserId(friendId).build();
        storageFacade.addFriend(event, System.currentTimeMillis());

        Relation relation = storageFacade.getRelationBetweenUser(userId, friendId);
        assertNotNull(relation);
        assertEquals("Friend Three", relation.getRelationNickName());
        assertEquals("friend-avatar.png", relation.getRelationAvatar());
    }

    // ==================== removeFriend Tests ====================

    @Test
    void removeFriend_shouldDeleteFriendRelation()
            throws NotFoundUserProfileException, RelationAlreadyExistsException,
                    NotFoundRelationException {
        long userId = 1004L;
        long friendId = 2004L;
        createUserProfile(userId, "user4", "User Four", "avatar1.png");
        createUserProfile(friendId, "friend4", "Friend Four", "avatar2.png");

        RelationEvent addEvent =
                RelationEvent.newBuilder().setUserId(userId).setTargetUserId(friendId).build();
        storageFacade.addFriend(addEvent, System.currentTimeMillis());

        RelationEvent removeEvent =
                RelationEvent.newBuilder().setUserId(userId).setTargetUserId(friendId).build();
        storageFacade.removeFriend(removeEvent, System.currentTimeMillis());

        Relation relation = storageFacade.getRelationBetweenUser(userId, friendId);
        assertNull(relation);
    }

    @Test
    void removeFriend_shouldThrowException_whenNotFriends() {
        long userId = 1005L;
        long notFriendId = 2005L;

        RelationEvent event =
                RelationEvent.newBuilder().setUserId(userId).setTargetUserId(notFriendId).build();

        assertThrows(NotFoundRelationException.class, () -> storageFacade.removeFriend(event, System.currentTimeMillis()));
    }

    // ==================== getRelations Tests (Pagination) ====================

    @Test
    void getRelations_shouldReturnFirstPage()
            throws NotFoundUserProfileException, RelationAlreadyExistsException {
        long userId = 1006L;
        createUserProfile(userId, "user6", "User Six", "avatar.png");
        createFriendsForPagination(userId, 5);

        PagedRelationResult result = storageFacade.getRelations(userId, null, 3);

        assertNotNull(result);
        assertEquals(3, result.getRelations().size());
        assertTrue(result.isHasMore());
        assertNotNull(result.getNextPageToken());
    }

    @Test
    void getRelations_shouldReturnSecondPageWithToken()
            throws NotFoundUserProfileException, RelationAlreadyExistsException {
        long userId = 1007L;
        createUserProfile(userId, "user7", "User Seven", "avatar.png");
        createFriendsForPagination(userId, 5);

        PagedRelationResult firstPage = storageFacade.getRelations(userId, null, 3);
        PagedRelationResult secondPage =
                storageFacade.getRelations(userId, firstPage.getNextPageToken(), 3);

        assertNotNull(secondPage);
        assertEquals(2, secondPage.getRelations().size());
        assertFalse(secondPage.isHasMore());
        assertNull(secondPage.getNextPageToken());
    }

    @Test
    void getRelations_shouldReturnEmptyResult_whenNoRelations() {
        long userId = 1008L;

        PagedRelationResult result = storageFacade.getRelations(userId, null, 10);

        assertNotNull(result);
        assertTrue(result.getRelations().isEmpty());
        assertFalse(result.isHasMore());
        assertNull(result.getNextPageToken());
    }

    // ==================== getRelationBetweenUser Tests ====================

    @Test
    void getRelationBetweenUser_shouldReturnRelation()
            throws NotFoundUserProfileException, RelationAlreadyExistsException {
        long userId = 1009L;
        long friendId = 2009L;
        createUserProfile(userId, "user9", "User Nine", "avatar1.png");
        createUserProfile(friendId, "friend9", "Friend Nine", "avatar2.png");

        RelationEvent event =
                RelationEvent.newBuilder().setUserId(userId).setTargetUserId(friendId).build();
        storageFacade.addFriend(event, System.currentTimeMillis());

        Relation relation = storageFacade.getRelationBetweenUser(userId, friendId);
        assertNotNull(relation);
        assertEquals(userId, relation.getSourceUserId());
        assertEquals(friendId, relation.getRelationUserId());
    }

    @Test
    void getRelationBetweenUser_shouldReturnNull_whenNoRelation() {
        long userId = 1010L;
        long notFriendId = 2010L;

        Relation relation = storageFacade.getRelationBetweenUser(userId, notFriendId);
        assertNull(relation);
    }

    // ==================== getFriendIds Tests ====================

    @Test
    void getFriendIds_shouldReturnFriendIds()
            throws NotFoundUserProfileException, RelationAlreadyExistsException {
        long userId = 1011L;
        createUserProfile(userId, "user11", "User Eleven", "avatar.png");

        long friendId1 = 2011L;
        long friendId2 = 2012L;
        createUserProfile(friendId1, "friend11", "Friend Eleven", "avatar1.png");
        createUserProfile(friendId2, "friend12", "Friend Twelve", "avatar2.png");

        RelationEvent event1 =
                RelationEvent.newBuilder().setUserId(userId).setTargetUserId(friendId1).build();
        RelationEvent event2 =
                RelationEvent.newBuilder().setUserId(userId).setTargetUserId(friendId2).build();
        storageFacade.addFriend(event1, System.currentTimeMillis());
        storageFacade.addFriend(event2, System.currentTimeMillis());

        UserIds friendIds = storageFacade.getFriendIds(userId);
        assertNotNull(friendIds);
        assertEquals(2, friendIds.getUserIdsCount());
        assertTrue(friendIds.getUserIdsList().contains(friendId1));
        assertTrue(friendIds.getUserIdsList().contains(friendId2));
    }

    @Test
    void getFriendIds_shouldReturnEmptyList_whenNoFriends() {
        long userId = 1012L;

        UserIds friendIds = storageFacade.getFriendIds(userId);
        assertNotNull(friendIds);
        assertEquals(0, friendIds.getUserIdsCount());
    }

    // ==================== updateFriendRemarkName Tests ====================

    @Test
    void updateFriendRemarkName_shouldUpdateRemarkName()
            throws NotFoundUserProfileException, RelationAlreadyExistsException,
                    NotFoundRelationException {
        long userId = 1013L;
        long friendId = 2013L;
        createUserProfile(userId, "user13", "User Thirteen", "avatar1.png");
        createUserProfile(friendId, "friend13", "Friend Thirteen", "avatar2.png");

        RelationEvent addEvent =
                RelationEvent.newBuilder().setUserId(userId).setTargetUserId(friendId).build();
        storageFacade.addFriend(addEvent, System.currentTimeMillis());

        RelationEvent updateEvent =
                RelationEvent.newBuilder()
                        .setUserId(userId)
                        .setTargetUserId(friendId)
                        .setTargetUserRemarkName("Best Friend")
                        .build();
        storageFacade.updateFriendRemarkName(updateEvent, System.currentTimeMillis());

        Relation relation = storageFacade.getRelationBetweenUser(userId, friendId);
        assertEquals("Best Friend", relation.getRelationRemarkName());
    }

    @Test
    void updateFriendRemarkName_shouldThrowException_whenNotFriends() {
        long userId = 1014L;
        long notFriendId = 2014L;

        RelationEvent event =
                RelationEvent.newBuilder()
                        .setUserId(userId)
                        .setTargetUserId(notFriendId)
                        .setTargetUserRemarkName("Test")
                        .build();

        assertThrows(NotFoundRelationException.class, () -> storageFacade.updateFriendRemarkName(event, System.currentTimeMillis()));
    }

    // ==================== blockFriend Tests ====================

    @Test
    void blockFriend_shouldSetBlockedFlag()
            throws NotFoundUserProfileException, RelationAlreadyExistsException,
                    BlockAlreadyExistsException {
        long userId = 1015L;
        long friendId = 2015L;
        createUserProfile(userId, "user15", "User Fifteen", "avatar1.png");
        createUserProfile(friendId, "friend15", "Friend Fifteen", "avatar2.png");

        RelationEvent addEvent =
                RelationEvent.newBuilder().setUserId(userId).setTargetUserId(friendId).build();
        storageFacade.addFriend(addEvent, System.currentTimeMillis());

        RelationEvent blockEvent =
                RelationEvent.newBuilder().setUserId(userId).setTargetUserId(friendId).build();
        storageFacade.blockFriend(blockEvent, System.currentTimeMillis());

        Relation relation = storageFacade.getRelationBetweenUser(userId, friendId);
        assertTrue(RelationFlags.BLOCKED.isSet(relation.getRelationFlags()));
        assertTrue(RelationFlags.FRIEND.isSet(relation.getRelationFlags()));
    }

    @Test
    void blockFriend_shouldThrowException_whenAlreadyBlocked()
            throws NotFoundUserProfileException, RelationAlreadyExistsException,
                    BlockAlreadyExistsException {
        long userId = 1016L;
        long friendId = 2016L;
        createUserProfile(userId, "user16", "User Sixteen", "avatar1.png");
        createUserProfile(friendId, "friend16", "Friend Sixteen", "avatar2.png");

        RelationEvent addEvent =
                RelationEvent.newBuilder().setUserId(userId).setTargetUserId(friendId).build();
        storageFacade.addFriend(addEvent, System.currentTimeMillis());

        RelationEvent blockEvent =
                RelationEvent.newBuilder().setUserId(userId).setTargetUserId(friendId).build();
        storageFacade.blockFriend(blockEvent, System.currentTimeMillis());

        assertThrows(BlockAlreadyExistsException.class, () -> storageFacade.blockFriend(blockEvent, System.currentTimeMillis()));
    }

    @Test
    void blockFriend_shouldCreateBlockRelation_whenNotFriends()
            throws BlockAlreadyExistsException {
        long userId = 1017L;
        long strangerId = 2017L;

        RelationEvent blockEvent =
                RelationEvent.newBuilder().setUserId(userId).setTargetUserId(strangerId).build();
        storageFacade.blockFriend(blockEvent, System.currentTimeMillis());

        Relation relation = storageFacade.getRelationBetweenUser(userId, strangerId);
        assertNotNull(relation);
        assertTrue(RelationFlags.BLOCKED.isSet(relation.getRelationFlags()));
        assertFalse(RelationFlags.FRIEND.isSet(relation.getRelationFlags()));
    }

    // ==================== blockStranger Tests ====================

    @Test
    void blockStranger_shouldBlockStranger()
            throws NotFoundUserProfileException, StrangerHasRelationshipException {
        long userId = 1018L;
        long strangerId = 2018L;
        createUserProfile(userId, "user18", "User Eighteen", "avatar1.png");
        createUserProfile(strangerId, "stranger18", "Stranger Eighteen", "avatar2.png");

        RelationEvent event =
                RelationEvent.newBuilder().setUserId(userId).setTargetUserId(strangerId).build();
        storageFacade.blockStranger(event, System.currentTimeMillis());

        Relation relation = storageFacade.getRelationBetweenUser(userId, strangerId);
        assertNotNull(relation);
        assertTrue(RelationFlags.BLOCKED.isSet(relation.getRelationFlags()));
    }

    @Test
    void blockStranger_shouldThrowException_whenAlreadyHasRelation()
            throws NotFoundUserProfileException, RelationAlreadyExistsException {
        long userId = 1019L;
        long friendId = 2019L;
        createUserProfile(userId, "user19", "User Nineteen", "avatar1.png");
        createUserProfile(friendId, "friend19", "Friend Nineteen", "avatar2.png");

        RelationEvent addEvent =
                RelationEvent.newBuilder().setUserId(userId).setTargetUserId(friendId).build();
        storageFacade.addFriend(addEvent, System.currentTimeMillis());

        RelationEvent blockEvent =
                RelationEvent.newBuilder().setUserId(userId).setTargetUserId(friendId).build();

        assertThrows(
                StrangerHasRelationshipException.class, () -> storageFacade.blockStranger(blockEvent, System.currentTimeMillis()));
    }

    // ==================== unblockUser Tests ====================

    @Test
    void unblockUser_shouldRemoveBlockedFlag()
            throws NotFoundUserProfileException, RelationAlreadyExistsException,
                    BlockAlreadyExistsException, NotFoundBlockException {
        long userId = 1020L;
        long friendId = 2020L;
        createUserProfile(userId, "user20", "User Twenty", "avatar1.png");
        createUserProfile(friendId, "friend20", "Friend Twenty", "avatar2.png");

        RelationEvent addEvent =
                RelationEvent.newBuilder().setUserId(userId).setTargetUserId(friendId).build();
        storageFacade.addFriend(addEvent, System.currentTimeMillis());

        RelationEvent blockEvent =
                RelationEvent.newBuilder().setUserId(userId).setTargetUserId(friendId).build();
        storageFacade.blockFriend(blockEvent, System.currentTimeMillis());

        RelationEvent unblockEvent =
                RelationEvent.newBuilder().setUserId(userId).setTargetUserId(friendId).build();
        storageFacade.unblockUser(unblockEvent, System.currentTimeMillis());

        Relation relation = storageFacade.getRelationBetweenUser(userId, friendId);
        assertNotNull(relation);
        assertFalse(RelationFlags.BLOCKED.isSet(relation.getRelationFlags()));
        assertTrue(RelationFlags.FRIEND.isSet(relation.getRelationFlags()));
    }

    @Test
    void unblockUser_shouldDeleteRelation_whenStranger()
            throws BlockAlreadyExistsException, NotFoundBlockException {
        long userId = 1021L;
        long strangerId = 2021L;

        RelationEvent blockEvent =
                RelationEvent.newBuilder().setUserId(userId).setTargetUserId(strangerId).build();
        storageFacade.blockFriend(blockEvent, System.currentTimeMillis());

        RelationEvent unblockEvent =
                RelationEvent.newBuilder().setUserId(userId).setTargetUserId(strangerId).build();
        storageFacade.unblockUser(unblockEvent, System.currentTimeMillis());

        Relation relation = storageFacade.getRelationBetweenUser(userId, strangerId);
        assertNull(relation);
    }

    @Test
    void unblockUser_shouldThrowException_whenNotBlocked() {
        long userId = 1022L;
        long notBlockedId = 2022L;

        RelationEvent event =
                RelationEvent.newBuilder().setUserId(userId).setTargetUserId(notBlockedId).build();

        assertThrows(NotFoundBlockException.class, () -> storageFacade.unblockUser(event, System.currentTimeMillis()));
    }

    // ==================== hideBlockedUser Tests ====================

    @Test
    void hideBlockedUser_shouldSetHiddenFlag()
            throws NotFoundUserProfileException, RelationAlreadyExistsException,
                    BlockAlreadyExistsException, NotFoundBlockException {
        long userId = 1023L;
        long friendId = 2023L;
        createUserProfile(userId, "user23", "User TwentyThree", "avatar1.png");
        createUserProfile(friendId, "friend23", "Friend TwentyThree", "avatar2.png");

        RelationEvent addEvent =
                RelationEvent.newBuilder().setUserId(userId).setTargetUserId(friendId).build();
        storageFacade.addFriend(addEvent, System.currentTimeMillis());

        RelationEvent blockEvent =
                RelationEvent.newBuilder().setUserId(userId).setTargetUserId(friendId).build();
        storageFacade.blockFriend(blockEvent, System.currentTimeMillis());

        RelationEvent hideEvent =
                RelationEvent.newBuilder().setUserId(userId).setTargetUserId(friendId).build();
        storageFacade.hideBlockedUser(hideEvent, System.currentTimeMillis());

        Relation relation = storageFacade.getRelationBetweenUser(userId, friendId);
        assertNotNull(relation);
        assertTrue(RelationFlags.BLOCKED.isSet(relation.getRelationFlags()));
        assertTrue(RelationFlags.HIDDEN.isSet(relation.getRelationFlags()));
        assertFalse(RelationFlags.FRIEND.isSet(relation.getRelationFlags()));
    }

    @Test
    void hideBlockedUser_shouldThrowException_whenNotBlocked() {
        long userId = 1024L;
        long notBlockedId = 2024L;

        RelationEvent event =
                RelationEvent.newBuilder().setUserId(userId).setTargetUserId(notBlockedId).build();

        assertThrows(NotFoundBlockException.class, () -> storageFacade.hideBlockedUser(event, System.currentTimeMillis()));
    }

    // ==================== isBlocked Tests ====================

    @Test
    void isBlocked_shouldReturnTrue_whenUserBlocked()
            throws NotFoundUserProfileException, RelationAlreadyExistsException,
                    BlockAlreadyExistsException {
        long userId = 1040L;
        long friendId = 2040L;
        createUserProfile(userId, "user40", "User Forty", "avatar1.png");
        createUserProfile(friendId, "friend40", "Friend Forty", "avatar2.png");

        // Add friend first
        RelationEvent addEvent =
                RelationEvent.newBuilder().setUserId(userId).setTargetUserId(friendId).build();
        storageFacade.addFriend(addEvent, System.currentTimeMillis());

        // Block the friend
        RelationEvent blockEvent =
                RelationEvent.newBuilder().setUserId(userId).setTargetUserId(friendId).build();
        storageFacade.blockFriend(blockEvent, System.currentTimeMillis());

        // userId blocked friendId, so isBlocked(userId, friendId) should return true
        assertTrue(storageFacade.isBlocked(userId, friendId));
    }

    @Test
    void isBlocked_shouldReturnFalse_whenNoRelation() {
        long userId = 1041L;
        long strangerId = 2041L;

        // No relation exists between them
        assertFalse(storageFacade.isBlocked(userId, strangerId));
    }

    @Test
    void isBlocked_shouldReturnFalse_whenNotBlocked()
            throws NotFoundUserProfileException, RelationAlreadyExistsException {
        long userId = 1042L;
        long friendId = 2042L;
        createUserProfile(userId, "user42", "User FortyTwo", "avatar1.png");
        createUserProfile(friendId, "friend42", "Friend FortyTwo", "avatar2.png");

        // Add friend only (not blocked)
        RelationEvent addEvent =
                RelationEvent.newBuilder().setUserId(userId).setTargetUserId(friendId).build();
        storageFacade.addFriend(addEvent, System.currentTimeMillis());

        // They are friends but not blocked
        assertFalse(storageFacade.isBlocked(userId, friendId));
    }

    @Test
    void isBlocked_shouldReturnFalse_afterUnblock()
            throws NotFoundUserProfileException, RelationAlreadyExistsException,
                    BlockAlreadyExistsException, NotFoundBlockException {
        long userId = 1043L;
        long friendId = 2043L;
        createUserProfile(userId, "user43", "User FortyThree", "avatar1.png");
        createUserProfile(friendId, "friend43", "Friend FortyThree", "avatar2.png");

        // Add friend
        RelationEvent addEvent =
                RelationEvent.newBuilder().setUserId(userId).setTargetUserId(friendId).build();
        storageFacade.addFriend(addEvent, System.currentTimeMillis());

        // Block
        RelationEvent blockEvent =
                RelationEvent.newBuilder().setUserId(userId).setTargetUserId(friendId).build();
        storageFacade.blockFriend(blockEvent, System.currentTimeMillis());

        // Verify blocked
        assertTrue(storageFacade.isBlocked(userId, friendId));

        // Unblock
        RelationEvent unblockEvent =
                RelationEvent.newBuilder().setUserId(userId).setTargetUserId(friendId).build();
        storageFacade.unblockUser(unblockEvent, System.currentTimeMillis());

        // Verify not blocked anymore
        assertFalse(storageFacade.isBlocked(userId, friendId));
    }

    @Test
    void isBlocked_shouldCheckCorrectDirection()
            throws NotFoundUserProfileException, RelationAlreadyExistsException,
                    BlockAlreadyExistsException {
        long aliceId = 1044L;
        long bobId = 2044L;
        createUserProfile(aliceId, "alice44", "Alice", "avatar1.png");
        createUserProfile(bobId, "bob44", "Bob", "avatar2.png");

        // Alice adds Bob as friend
        RelationEvent addEvent =
                RelationEvent.newBuilder().setUserId(aliceId).setTargetUserId(bobId).build();
        storageFacade.addFriend(addEvent, System.currentTimeMillis());

        // Alice blocks Bob
        RelationEvent blockEvent =
                RelationEvent.newBuilder().setUserId(aliceId).setTargetUserId(bobId).build();
        storageFacade.blockFriend(blockEvent, System.currentTimeMillis());

        // Alice blocked Bob: isBlocked(alice, bob) = true
        assertTrue(storageFacade.isBlocked(aliceId, bobId));

        // Bob did NOT block Alice: isBlocked(bob, alice) = false
        assertFalse(storageFacade.isBlocked(bobId, aliceId));
    }

    // ==================== getRelationChanges Tests ====================

    @Test
    void getRelationChanges_shouldReturnChangesAfterVersion() throws Exception {
        long userId = 1025L;
        long friendId = 2025L;
        createUserProfile(userId, "user25", "User TwentyFive", "avatar1.png");
        createUserProfile(friendId, "friend25", "Friend TwentyFive", "avatar2.png");

        RelationEvent event =
                RelationEvent.newBuilder().setUserId(userId).setTargetUserId(friendId).build();
        storageFacade.addFriend(event, System.currentTimeMillis());

        Thread.sleep(10);
        long afterVersionLong = System.currentTimeMillis();
        Thread.sleep(10);

        RelationEvent remarkEvent =
                RelationEvent.newBuilder()
                        .setUserId(userId)
                        .setTargetUserId(friendId)
                        .setTargetUserRemarkName("Best Friend")
                        .build();
        storageFacade.updateFriendRemarkName(remarkEvent, System.currentTimeMillis());

        String afterVersion = String.valueOf(afterVersionLong);

        List<RelationVersionChange> changes =
                storageFacade.getRelationChanges(userId, afterVersion, 10);

        assertNotNull(changes);
        assertEquals(1, changes.size());
        assertEquals(friendId, changes.get(0).getRelationUserId());
    }

    @Test
    void getRelationChanges_shouldReturnEmptyList_whenNoChanges() throws Exception {
        long userId = 1026L;
        long friendId = 2026L;
        createUserProfile(userId, "user26", "User TwentySix", "avatar1.png");
        createUserProfile(friendId, "friend26", "Friend TwentySix", "avatar2.png");

        RelationEvent event =
                RelationEvent.newBuilder().setUserId(userId).setTargetUserId(friendId).build();
        storageFacade.addFriend(event, System.currentTimeMillis());

        Thread.sleep(10);
        String afterVersion = String.valueOf(System.currentTimeMillis());

        List<RelationVersionChange> changes =
                storageFacade.getRelationChanges(userId, afterVersion, 10);

        assertNotNull(changes);
        assertTrue(changes.isEmpty());
    }

    @Test
    void getRelationChanges_shouldThrowException_whenNullVersion() {
        long userId = 1027L;

        assertThrows(
                InvalidVersionException.class,
                () -> storageFacade.getRelationChanges(userId, null, 10));
    }

    @Test
    void getRelationChanges_shouldThrowException_whenEmptyVersion() {
        long userId = 1028L;

        assertThrows(
                InvalidVersionException.class,
                () -> storageFacade.getRelationChanges(userId, "", 10));
    }

    // ==================== getLatestRelationVersion Tests ====================

    @Test
    void getLatestRelationVersion_shouldReturnLatestVersion() throws Exception {
        long userId = 1029L;
        long friendId = 2029L;
        createUserProfile(userId, "user29", "User TwentyNine", "avatar1.png");
        createUserProfile(friendId, "friend29", "Friend TwentyNine", "avatar2.png");

        RelationEvent event =
                RelationEvent.newBuilder().setUserId(userId).setTargetUserId(friendId).build();
        storageFacade.addFriend(event, System.currentTimeMillis());

        String latestVersion = storageFacade.getLatestRelationVersion(userId);

        assertNotNull(latestVersion);
        assertFalse(latestVersion.isEmpty());

        long timestamp = Long.parseLong(latestVersion);
        assertTrue(timestamp > 0);
    }

    @Test
    void getLatestRelationVersion_shouldReturnNull_whenNoVersionsExist() {
        long userId = 1030L;

        String latestVersion = storageFacade.getLatestRelationVersion(userId);

        assertNull(latestVersion);
    }

    // ==================== Helper Methods ====================

    private void createFriendsForPagination(long userId, int count)
            throws NotFoundUserProfileException, RelationAlreadyExistsException {
        for (int i = 0; i < count; i++) {
            long friendId = 3000L + i;
            createUserProfile(friendId, "friend_page_" + i, "Friend " + i, "avatar.png");
            RelationEvent event =
                    RelationEvent.newBuilder().setUserId(userId).setTargetUserId(friendId).build();
            storageFacade.addFriend(event, System.currentTimeMillis());
        }
    }
}
