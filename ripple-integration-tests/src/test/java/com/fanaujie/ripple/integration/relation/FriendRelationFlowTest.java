package com.fanaujie.ripple.integration.relation;

import com.fanaujie.ripple.integration.base.AbstractBusinessFlowTest;
import com.fanaujie.ripple.protobuf.storage.UserIds;
import com.fanaujie.ripple.storage.model.Relation;
import com.fanaujie.ripple.storage.model.RelationFlags;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Friend Relation Business Flow Tests")
class FriendRelationFlowTest extends AbstractBusinessFlowTest {

    // Test users
    protected static final long ALICE_ID = 1001L;
    protected static final long BOB_ID = 2001L;
    protected static final long CHARLIE_ID = 3001L;

    @BeforeEach
    void setUpTestUsers() {
        // Create test users that exist before any test scenario
        createUser(ALICE_ID, "alice", "Alice", "alice-avatar.png");
        createUser(BOB_ID, "bob", "Bob", "bob-avatar.png");
        createUser(CHARLIE_ID, "charlie", "Charlie", "charlie-avatar.png");
    }

    @Nested
    @DisplayName("Scenario: Add Friend")
    class AddFriendScenarios {

        @Test
        @DisplayName("When Alice adds Bob as friend, Bob should appear in Alice's friend list")
        void addFriend_oneWay_shouldCreateRelation() throws Exception {
            // When: Alice adds Bob as friend
            executeAddFriendFlow(ALICE_ID, BOB_ID);

            // Then: Alice should have Bob in her relations
            Relation aliceToBob = storageFacade.getRelationBetweenUser(ALICE_ID, BOB_ID);
            assertThat(aliceToBob).isNotNull();
            assertThat(RelationFlags.FRIEND.isSet(aliceToBob.getRelationFlags())).isTrue();
            assertThat(aliceToBob.getRelationNickName()).isEqualTo("Bob");
            assertThat(aliceToBob.getRelationAvatar()).isEqualTo("bob-avatar.png");

            // And: Alice's friend list should contain Bob
            UserIds aliceFriends = storageFacade.getFriendIds(ALICE_ID);
            assertThat(aliceFriends.getUserIdsList()).contains(BOB_ID);

            // But: Bob should NOT have Alice in his friend list (one-way friendship)
            Relation bobToAlice = storageFacade.getRelationBetweenUser(BOB_ID, ALICE_ID);
            assertThat(bobToAlice).isNull();

            UserIds bobFriends = storageFacade.getFriendIds(BOB_ID);
            // Bob has no friends, so either null or empty list
            if (bobFriends != null) {
                assertThat(bobFriends.getUserIdsList()).doesNotContain(ALICE_ID);
            }
        }

        @Test
        @DisplayName(
                "When Alice and Bob add each other, both should be in each other's friend list with synced profiles")
        void addFriend_mutual_shouldCreateBidirectionalRelation() throws Exception {
            // When: Alice adds Bob first
            executeAddFriendFlow(ALICE_ID, BOB_ID);

            // And: Bob adds Alice back
            executeAddFriendFlow(BOB_ID, ALICE_ID);

            // Then: Both should have each other as friends
            Relation aliceToBob = storageFacade.getRelationBetweenUser(ALICE_ID, BOB_ID);
            Relation bobToAlice = storageFacade.getRelationBetweenUser(BOB_ID, ALICE_ID);

            assertThat(aliceToBob).isNotNull();
            assertThat(bobToAlice).isNotNull();
            assertThat(RelationFlags.FRIEND.isSet(aliceToBob.getRelationFlags())).isTrue();
            assertThat(RelationFlags.FRIEND.isSet(bobToAlice.getRelationFlags())).isTrue();

            // And: Both should have correct friend info synced
            assertThat(aliceToBob.getRelationNickName()).isEqualTo("Bob");
            assertThat(bobToAlice.getRelationNickName()).isEqualTo("Alice");

            // And: Both friend lists should contain each other
            UserIds aliceFriends = storageFacade.getFriendIds(ALICE_ID);
            UserIds bobFriends = storageFacade.getFriendIds(BOB_ID);

            assertThat(aliceFriends.getUserIdsList()).contains(BOB_ID);
            assertThat(bobFriends.getUserIdsList()).contains(ALICE_ID);
        }

        @Test
        @DisplayName("When Alice adds multiple friends, all should appear in her friend list")
        void addFriend_multiple_shouldCreateAllRelations() throws Exception {
            // When: Alice adds both Bob and Charlie
            executeAddFriendFlow(ALICE_ID, BOB_ID);
            executeAddFriendFlow(ALICE_ID, CHARLIE_ID);

            // Then: Alice should have both as friends
            UserIds aliceFriends = storageFacade.getFriendIds(ALICE_ID);
            assertThat(aliceFriends.getUserIdsList()).containsExactlyInAnyOrder(BOB_ID, CHARLIE_ID);
        }
    }

    @Nested
    @DisplayName("Scenario: Remove Friend")
    class RemoveFriendScenarios {

        @Test
        @DisplayName(
                "When Alice removes Bob from friends, Bob should no longer appear in Alice's friend list")
        void removeFriend_shouldDeleteRelation() throws Exception {
            // Given: Alice and Bob are friends
            executeAddFriendFlow(ALICE_ID, BOB_ID);

            // When: Alice removes Bob
            executeRemoveFriendFlow(ALICE_ID, BOB_ID);

            // Then: Bob should not be in Alice's relations
            Relation aliceToBob = storageFacade.getRelationBetweenUser(ALICE_ID, BOB_ID);
            assertThat(aliceToBob).isNull();

            // And: Alice's friend list should not contain Bob
            UserIds aliceFriends = storageFacade.getFriendIds(ALICE_ID);
            if (aliceFriends != null) {
                assertThat(aliceFriends.getUserIdsList()).doesNotContain(BOB_ID);
            }
        }

        @Test
        @DisplayName(
                "When Alice removes Bob, Bob's friend list should still contain Alice if they were mutual friends")
        void removeFriend_mutual_shouldOnlyRemoveOneSide() throws Exception {
            // Given: Alice and Bob are mutual friends
            executeAddFriendFlow(ALICE_ID, BOB_ID);
            executeAddFriendFlow(BOB_ID, ALICE_ID);

            // When: Alice removes Bob
            executeRemoveFriendFlow(ALICE_ID, BOB_ID);

            // Then: Alice should not have Bob as friend
            UserIds aliceFriends = storageFacade.getFriendIds(ALICE_ID);
            if (aliceFriends != null) {
                assertThat(aliceFriends.getUserIdsList()).doesNotContain(BOB_ID);
            }

            // But: Bob should still have Alice as friend (one-way removal)
            UserIds bobFriends = storageFacade.getFriendIds(BOB_ID);
            assertThat(bobFriends).isNotNull();
            assertThat(bobFriends.getUserIdsList()).contains(ALICE_ID);
        }
    }

    @Nested
    @DisplayName("Scenario: Update Friend Remark Name")
    class UpdateRemarkNameScenarios {

        @Test
        @DisplayName("When Alice sets a remark name for Bob, the remark should be saved")
        void updateRemarkName_shouldSaveRemark() throws Exception {
            // Given: Alice has Bob as friend
            executeAddFriendFlow(ALICE_ID, BOB_ID);

            // When: Alice sets a remark name for Bob
            executeUpdateFriendRemarkNameFlow(ALICE_ID, BOB_ID, "Bobby");

            // Then: The remark name should be saved
            Relation aliceToBob = storageFacade.getRelationBetweenUser(ALICE_ID, BOB_ID);
            assertThat(aliceToBob.getRelationRemarkName()).isEqualTo("Bobby");

            // And: Bob's original nickname should still be preserved
            assertThat(aliceToBob.getRelationNickName()).isEqualTo("Bob");
        }

        @Test
        @DisplayName(
                "When Alice changes Bob's remark name multiple times, the latest should be saved")
        void updateRemarkName_multiple_shouldKeepLatest() throws Exception {
            // Given: Alice has Bob as friend
            executeAddFriendFlow(ALICE_ID, BOB_ID);

            // When: Alice changes remark multiple times
            executeUpdateFriendRemarkNameFlow(ALICE_ID, BOB_ID, "Bobby");
            executeUpdateFriendRemarkNameFlow(ALICE_ID, BOB_ID, "Best Friend Bob");

            // Then: Only the latest remark should be saved
            Relation aliceToBob = storageFacade.getRelationBetweenUser(ALICE_ID, BOB_ID);
            assertThat(aliceToBob.getRelationRemarkName()).isEqualTo("Best Friend Bob");
        }
    }

    @Nested
    @DisplayName("Scenario: Block Friend")
    class BlockFriendScenarios {

        @Test
        @DisplayName(
                "When Alice blocks Bob (who is a friend), Bob should be marked as blocked but still a friend")
        void blockFriend_shouldMarkAsBlocked() throws Exception {
            // Given: Alice has Bob as friend
            executeAddFriendFlow(ALICE_ID, BOB_ID);

            // When: Alice blocks Bob
            executeBlockFriendFlow(ALICE_ID, BOB_ID);

            // Then: Bob should be marked as both friend and blocked
            Relation aliceToBob = storageFacade.getRelationBetweenUser(ALICE_ID, BOB_ID);
            assertThat(aliceToBob).isNotNull();
            assertThat(RelationFlags.FRIEND.isSet(aliceToBob.getRelationFlags())).isTrue();
            assertThat(RelationFlags.BLOCKED.isSet(aliceToBob.getRelationFlags())).isTrue();
        }

        @Test
        @DisplayName("When Alice blocks Bob and then unblocks, Bob should only be a friend")
        void blockAndUnblock_shouldRestoreFriendStatus() throws Exception {
            // Given: Alice has Bob as friend and blocks him
            executeAddFriendFlow(ALICE_ID, BOB_ID);
            executeBlockFriendFlow(ALICE_ID, BOB_ID);

            // When: Alice unblocks Bob
            executeUnblockUserFlow(ALICE_ID, BOB_ID);

            // Then: Bob should only be a friend (not blocked)
            Relation aliceToBob = storageFacade.getRelationBetweenUser(ALICE_ID, BOB_ID);
            assertThat(aliceToBob).isNotNull();
            assertThat(RelationFlags.FRIEND.isSet(aliceToBob.getRelationFlags())).isTrue();
            assertThat(RelationFlags.BLOCKED.isSet(aliceToBob.getRelationFlags())).isFalse();
        }
    }

    @Nested
    @DisplayName("Scenario: Block Stranger")
    class BlockStrangerScenarios {

        @Test
        @DisplayName("When Alice blocks Charlie (a stranger), Charlie should be marked as blocked")
        void blockStranger_shouldCreateBlockedRelation() throws Exception {
            // Given: Alice and Charlie have no relationship

            // When: Alice blocks Charlie
            executeBlockStrangerFlow(ALICE_ID, CHARLIE_ID);

            // Then: Charlie should be marked as blocked (not friend)
            Relation aliceToCharlie = storageFacade.getRelationBetweenUser(ALICE_ID, CHARLIE_ID);
            assertThat(aliceToCharlie).isNotNull();
            assertThat(RelationFlags.BLOCKED.isSet(aliceToCharlie.getRelationFlags())).isTrue();
            assertThat(RelationFlags.FRIEND.isSet(aliceToCharlie.getRelationFlags())).isFalse();
        }

        @Test
        @DisplayName("When Alice unblocks a stranger, the relation should be completely removed")
        void unblockStranger_shouldRemoveRelation() throws Exception {
            // Given: Alice has blocked Charlie (a stranger)
            executeBlockStrangerFlow(ALICE_ID, CHARLIE_ID);

            // When: Alice unblocks Charlie
            executeUnblockUserFlow(ALICE_ID, CHARLIE_ID);

            // Then: The relation should be completely removed
            Relation aliceToCharlie = storageFacade.getRelationBetweenUser(ALICE_ID, CHARLIE_ID);
            assertThat(aliceToCharlie).isNull();
        }
    }

    @Nested
    @DisplayName("Scenario: Hide Blocked User")
    class HideBlockedUserScenarios {

        @Test
        @DisplayName(
                "When Alice hides a blocked friend, the friend flag should be removed and hidden flag set")
        void hideBlockedFriend_shouldRemoveFriendAndSetHidden() throws Exception {
            // Given: Alice has Bob as friend and blocks him
            executeAddFriendFlow(ALICE_ID, BOB_ID);
            executeBlockFriendFlow(ALICE_ID, BOB_ID);

            // When: Alice hides the blocked user
            executeHideBlockedUserFlow(ALICE_ID, BOB_ID);

            // Then: Bob should be blocked and hidden, but no longer a friend
            Relation aliceToBob = storageFacade.getRelationBetweenUser(ALICE_ID, BOB_ID);
            assertThat(aliceToBob).isNotNull();
            assertThat(RelationFlags.BLOCKED.isSet(aliceToBob.getRelationFlags())).isTrue();
            assertThat(RelationFlags.HIDDEN.isSet(aliceToBob.getRelationFlags())).isTrue();
            assertThat(RelationFlags.FRIEND.isSet(aliceToBob.getRelationFlags())).isFalse();
        }
    }

    @Nested
    @DisplayName("Scenario: Complex User Journey")
    class ComplexUserJourneyScenarios {

        @Test
        @DisplayName("Complete friend lifecycle: add -> remark -> block -> unblock -> remove")
        void completeFriendLifecycle() throws Exception {
            // Step 1: Alice adds Bob as friend
            executeAddFriendFlow(ALICE_ID, BOB_ID);
            UserIds aliceFriends = storageFacade.getFriendIds(ALICE_ID);
            assertThat(aliceFriends).isNotNull();
            assertThat(aliceFriends.getUserIdsList()).contains(BOB_ID);

            // Step 2: Alice sets a remark for Bob
            executeUpdateFriendRemarkNameFlow(ALICE_ID, BOB_ID, "Best Friend");
            Relation relation = storageFacade.getRelationBetweenUser(ALICE_ID, BOB_ID);
            assertThat(relation.getRelationRemarkName()).isEqualTo("Best Friend");

            // Step 3: Alice blocks Bob (after an argument)
            executeBlockFriendFlow(ALICE_ID, BOB_ID);
            relation = storageFacade.getRelationBetweenUser(ALICE_ID, BOB_ID);
            assertThat(RelationFlags.BLOCKED.isSet(relation.getRelationFlags())).isTrue();

            // Step 4: Alice unblocks Bob (they made up)
            executeUnblockUserFlow(ALICE_ID, BOB_ID);
            relation = storageFacade.getRelationBetweenUser(ALICE_ID, BOB_ID);
            assertThat(RelationFlags.BLOCKED.isSet(relation.getRelationFlags())).isFalse();
            assertThat(RelationFlags.FRIEND.isSet(relation.getRelationFlags())).isTrue();

            // Step 5: Alice removes Bob (they drifted apart)
            executeRemoveFriendFlow(ALICE_ID, BOB_ID);
            assertThat(storageFacade.getRelationBetweenUser(ALICE_ID, BOB_ID)).isNull();
            aliceFriends = storageFacade.getFriendIds(ALICE_ID);
            if (aliceFriends != null) {
                assertThat(aliceFriends.getUserIdsList()).doesNotContain(BOB_ID);
            }
        }

        @Test
        @DisplayName("Social network expansion: Alice introduces Bob to Charlie")
        void socialNetworkExpansion() throws Exception {
            // Alice and Bob become mutual friends
            executeAddFriendFlow(ALICE_ID, BOB_ID);
            executeAddFriendFlow(BOB_ID, ALICE_ID);

            // Alice and Charlie become mutual friends
            executeAddFriendFlow(ALICE_ID, CHARLIE_ID);
            executeAddFriendFlow(CHARLIE_ID, ALICE_ID);

            // Bob and Charlie become mutual friends (through Alice's introduction)
            executeAddFriendFlow(BOB_ID, CHARLIE_ID);
            executeAddFriendFlow(CHARLIE_ID, BOB_ID);

            // Verify: Everyone is friends with everyone
            assertThat(storageFacade.getFriendIds(ALICE_ID).getUserIdsList())
                    .containsExactlyInAnyOrder(BOB_ID, CHARLIE_ID);
            assertThat(storageFacade.getFriendIds(BOB_ID).getUserIdsList())
                    .containsExactlyInAnyOrder(ALICE_ID, CHARLIE_ID);
            assertThat(storageFacade.getFriendIds(CHARLIE_ID).getUserIdsList())
                    .containsExactlyInAnyOrder(ALICE_ID, BOB_ID);
        }

        @Test
        @DisplayName("Conflict resolution: Block stranger who then becomes friend")
        void blockStrangerThenBecomeFriend() throws Exception {
            // Alice blocks Charlie (a stranger who was spamming)
            executeBlockStrangerFlow(ALICE_ID, CHARLIE_ID);
            Relation relation = storageFacade.getRelationBetweenUser(ALICE_ID, CHARLIE_ID);
            assertThat(RelationFlags.BLOCKED.isSet(relation.getRelationFlags())).isTrue();

            // Later, Alice realizes Charlie is a friend's friend and unblocks
            executeUnblockUserFlow(ALICE_ID, CHARLIE_ID);
            assertThat(storageFacade.getRelationBetweenUser(ALICE_ID, CHARLIE_ID)).isNull();

            // Now Alice adds Charlie as friend
            executeAddFriendFlow(ALICE_ID, CHARLIE_ID);
            relation = storageFacade.getRelationBetweenUser(ALICE_ID, CHARLIE_ID);
            assertThat(RelationFlags.FRIEND.isSet(relation.getRelationFlags())).isTrue();
            assertThat(RelationFlags.BLOCKED.isSet(relation.getRelationFlags())).isFalse();
        }
    }
}
