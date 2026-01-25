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
public abstract class AbstractFriendRelationFlowTest extends AbstractBusinessFlowTest {

    protected long aliceId() {
        return testIdBase + 1;
    }

    protected long bobId() {
        return testIdBase + 2;
    }

    protected long charlieId() {
        return testIdBase + 3;
    }

    @BeforeEach
    void setUpTestUsers() {
        // Create test users that exist before any test scenario
        createUser(aliceId(), "alice-" + testIdBase, "Alice", "alice-avatar.png");
        createUser(bobId(), "bob-" + testIdBase, "Bob", "bob-avatar.png");
        createUser(charlieId(), "charlie-" + testIdBase, "Charlie", "charlie-avatar.png");
    }

    @Nested
    @DisplayName("Scenario: Add Friend")
    class AddFriendScenarios {

        @Test
        @DisplayName("When Alice adds Bob as friend, Bob should appear in Alice's friend list")
        void addFriend_oneWay_shouldCreateRelation() throws Exception {
            // When: Alice adds Bob as friend
            executeAddFriendFlow(aliceId(), bobId());

            // Then: Alice should have Bob in her relations
            Relation aliceToBob = storageFacade.getRelationBetweenUser(aliceId(), bobId());
            assertThat(aliceToBob).isNotNull();
            assertThat(RelationFlags.FRIEND.isSet(aliceToBob.getRelationFlags())).isTrue();
            assertThat(aliceToBob.getRelationNickName()).isEqualTo("Bob");
            assertThat(aliceToBob.getRelationAvatar()).isEqualTo("bob-avatar.png");

            // And: Alice's friend list should contain Bob
            UserIds aliceFriends = storageFacade.getFriendIds(aliceId());
            assertThat(aliceFriends.getUserIdsList()).contains(bobId());

            // But: Bob should NOT have Alice in his friend list (one-way friendship)
            Relation bobToAlice = storageFacade.getRelationBetweenUser(bobId(), aliceId());
            assertThat(bobToAlice).isNull();

            UserIds bobFriends = storageFacade.getFriendIds(bobId());
            // Bob has no friends, so either null or empty list
            if (bobFriends != null) {
                assertThat(bobFriends.getUserIdsList()).doesNotContain(aliceId());
            }
        }

        @Test
        @DisplayName(
                "When Alice and Bob add each other, both should be in each other's friend list with synced profiles")
        void addFriend_mutual_shouldCreateBidirectionalRelation() throws Exception {
            // When: Alice adds Bob first
            executeAddFriendFlow(aliceId(), bobId());

            // And: Bob adds Alice back
            executeAddFriendFlow(bobId(), aliceId());

            // Then: Both should have each other as friends
            Relation aliceToBob = storageFacade.getRelationBetweenUser(aliceId(), bobId());
            Relation bobToAlice = storageFacade.getRelationBetweenUser(bobId(), aliceId());

            assertThat(aliceToBob).isNotNull();
            assertThat(bobToAlice).isNotNull();
            assertThat(RelationFlags.FRIEND.isSet(aliceToBob.getRelationFlags())).isTrue();
            assertThat(RelationFlags.FRIEND.isSet(bobToAlice.getRelationFlags())).isTrue();

            // And: Both should have correct friend info synced
            assertThat(aliceToBob.getRelationNickName()).isEqualTo("Bob");
            assertThat(bobToAlice.getRelationNickName()).isEqualTo("Alice");

            // And: Both friend lists should contain each other
            UserIds aliceFriends = storageFacade.getFriendIds(aliceId());
            UserIds bobFriends = storageFacade.getFriendIds(bobId());

            assertThat(aliceFriends.getUserIdsList()).contains(bobId());
            assertThat(bobFriends.getUserIdsList()).contains(aliceId());
        }

        @Test
        @DisplayName("When Alice adds multiple friends, all should appear in her friend list")
        void addFriend_multiple_shouldCreateAllRelations() throws Exception {
            // When: Alice adds both Bob and Charlie
            executeAddFriendFlow(aliceId(), bobId());
            executeAddFriendFlow(aliceId(), charlieId());

            // Then: Alice should have both as friends
            UserIds aliceFriends = storageFacade.getFriendIds(aliceId());
            assertThat(aliceFriends.getUserIdsList())
                    .containsExactlyInAnyOrder(bobId(), charlieId());
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
            executeAddFriendFlow(aliceId(), bobId());

            // When: Alice removes Bob
            executeRemoveFriendFlow(aliceId(), bobId());

            // Then: Bob should not be in Alice's relations
            Relation aliceToBob = storageFacade.getRelationBetweenUser(aliceId(), bobId());
            assertThat(aliceToBob).isNull();

            // And: Alice's friend list should not contain Bob
            UserIds aliceFriends = storageFacade.getFriendIds(aliceId());
            if (aliceFriends != null) {
                assertThat(aliceFriends.getUserIdsList()).doesNotContain(bobId());
            }
        }

        @Test
        @DisplayName(
                "When Alice removes Bob, Bob's friend list should still contain Alice if they were mutual friends")
        void removeFriend_mutual_shouldOnlyRemoveOneSide() throws Exception {
            // Given: Alice and Bob are mutual friends
            executeAddFriendFlow(aliceId(), bobId());
            executeAddFriendFlow(bobId(), aliceId());

            // When: Alice removes Bob
            executeRemoveFriendFlow(aliceId(), bobId());

            // Then: Alice should not have Bob as friend
            UserIds aliceFriends = storageFacade.getFriendIds(aliceId());
            if (aliceFriends != null) {
                assertThat(aliceFriends.getUserIdsList()).doesNotContain(bobId());
            }

            // But: Bob should still have Alice as friend (one-way removal)
            UserIds bobFriends = storageFacade.getFriendIds(bobId());
            assertThat(bobFriends).isNotNull();
            assertThat(bobFriends.getUserIdsList()).contains(aliceId());
        }
    }

    @Nested
    @DisplayName("Scenario: Update Friend Remark Name")
    class UpdateRemarkNameScenarios {

        @Test
        @DisplayName("When Alice sets a remark name for Bob, the remark should be saved")
        void updateRemarkName_shouldSaveRemark() throws Exception {
            // Given: Alice has Bob as friend
            executeAddFriendFlow(aliceId(), bobId());

            // When: Alice sets a remark name for Bob
            executeUpdateFriendRemarkNameFlow(aliceId(), bobId(), "Bobby");

            // Then: The remark name should be saved
            Relation aliceToBob = storageFacade.getRelationBetweenUser(aliceId(), bobId());
            assertThat(aliceToBob.getRelationRemarkName()).isEqualTo("Bobby");

            // And: Bob's original nickname should still be preserved
            assertThat(aliceToBob.getRelationNickName()).isEqualTo("Bob");
        }

        @Test
        @DisplayName(
                "When Alice changes Bob's remark name multiple times, the latest should be saved")
        void updateRemarkName_multiple_shouldKeepLatest() throws Exception {
            // Given: Alice has Bob as friend
            executeAddFriendFlow(aliceId(), bobId());

            // When: Alice changes remark multiple times
            executeUpdateFriendRemarkNameFlow(aliceId(), bobId(), "Bobby");
            executeUpdateFriendRemarkNameFlow(aliceId(), bobId(), "Best Friend Bob");

            // Then: Only the latest remark should be saved
            Relation aliceToBob = storageFacade.getRelationBetweenUser(aliceId(), bobId());
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
            executeAddFriendFlow(aliceId(), bobId());

            // When: Alice blocks Bob
            executeBlockFriendFlow(aliceId(), bobId());

            // Then: Bob should be marked as both friend and blocked
            Relation aliceToBob = storageFacade.getRelationBetweenUser(aliceId(), bobId());
            assertThat(aliceToBob).isNotNull();
            assertThat(RelationFlags.FRIEND.isSet(aliceToBob.getRelationFlags())).isTrue();
            assertThat(RelationFlags.BLOCKED.isSet(aliceToBob.getRelationFlags())).isTrue();
        }

        @Test
        @DisplayName("When Alice blocks Bob and then unblocks, Bob should only be a friend")
        void blockAndUnblock_shouldRestoreFriendStatus() throws Exception {
            // Given: Alice has Bob as friend and blocks him
            executeAddFriendFlow(aliceId(), bobId());
            executeBlockFriendFlow(aliceId(), bobId());

            // When: Alice unblocks Bob
            executeUnblockUserFlow(aliceId(), bobId());

            // Then: Bob should only be a friend (not blocked)
            Relation aliceToBob = storageFacade.getRelationBetweenUser(aliceId(), bobId());
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
            executeBlockStrangerFlow(aliceId(), charlieId());

            // Then: Charlie should be marked as blocked (not friend)
            Relation aliceToCharlie = storageFacade.getRelationBetweenUser(aliceId(), charlieId());
            assertThat(aliceToCharlie).isNotNull();
            assertThat(RelationFlags.BLOCKED.isSet(aliceToCharlie.getRelationFlags())).isTrue();
            assertThat(RelationFlags.FRIEND.isSet(aliceToCharlie.getRelationFlags())).isFalse();
        }

        @Test
        @DisplayName("When Alice unblocks a stranger, the relation should be completely removed")
        void unblockStranger_shouldRemoveRelation() throws Exception {
            // Given: Alice has blocked Charlie (a stranger)
            executeBlockStrangerFlow(aliceId(), charlieId());

            // When: Alice unblocks Charlie
            executeUnblockUserFlow(aliceId(), charlieId());

            // Then: The relation should be completely removed
            Relation aliceToCharlie = storageFacade.getRelationBetweenUser(aliceId(), charlieId());
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
            executeAddFriendFlow(aliceId(), bobId());
            executeBlockFriendFlow(aliceId(), bobId());

            // When: Alice hides the blocked user
            executeHideBlockedUserFlow(aliceId(), bobId());

            // Then: Bob should be blocked and hidden, but no longer a friend
            Relation aliceToBob = storageFacade.getRelationBetweenUser(aliceId(), bobId());
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
            executeAddFriendFlow(aliceId(), bobId());
            UserIds aliceFriends = storageFacade.getFriendIds(aliceId());
            assertThat(aliceFriends).isNotNull();
            assertThat(aliceFriends.getUserIdsList()).contains(bobId());

            // Step 2: Alice sets a remark for Bob
            executeUpdateFriendRemarkNameFlow(aliceId(), bobId(), "Best Friend");
            Relation relation = storageFacade.getRelationBetweenUser(aliceId(), bobId());
            assertThat(relation.getRelationRemarkName()).isEqualTo("Best Friend");

            // Step 3: Alice blocks Bob (after an argument)
            executeBlockFriendFlow(aliceId(), bobId());
            relation = storageFacade.getRelationBetweenUser(aliceId(), bobId());
            assertThat(RelationFlags.BLOCKED.isSet(relation.getRelationFlags())).isTrue();

            // Step 4: Alice unblocks Bob (they made up)
            executeUnblockUserFlow(aliceId(), bobId());
            relation = storageFacade.getRelationBetweenUser(aliceId(), bobId());
            assertThat(RelationFlags.BLOCKED.isSet(relation.getRelationFlags())).isFalse();
            assertThat(RelationFlags.FRIEND.isSet(relation.getRelationFlags())).isTrue();

            // Step 5: Alice removes Bob (they drifted apart)
            executeRemoveFriendFlow(aliceId(), bobId());
            assertThat(storageFacade.getRelationBetweenUser(aliceId(), bobId())).isNull();
            aliceFriends = storageFacade.getFriendIds(aliceId());
            if (aliceFriends != null) {
                assertThat(aliceFriends.getUserIdsList()).doesNotContain(bobId());
            }
        }

        @Test
        @DisplayName("Social network expansion: Alice introduces Bob to Charlie")
        void socialNetworkExpansion() throws Exception {
            // Alice and Bob become mutual friends
            executeAddFriendFlow(aliceId(), bobId());
            executeAddFriendFlow(bobId(), aliceId());

            // Alice and Charlie become mutual friends
            executeAddFriendFlow(aliceId(), charlieId());
            executeAddFriendFlow(charlieId(), aliceId());

            // Bob and Charlie become mutual friends (through Alice's introduction)
            executeAddFriendFlow(bobId(), charlieId());
            executeAddFriendFlow(charlieId(), bobId());

            // Verify: Everyone is friends with everyone
            assertThat(storageFacade.getFriendIds(aliceId()).getUserIdsList())
                    .containsExactlyInAnyOrder(bobId(), charlieId());
            assertThat(storageFacade.getFriendIds(bobId()).getUserIdsList())
                    .containsExactlyInAnyOrder(aliceId(), charlieId());
            assertThat(storageFacade.getFriendIds(charlieId()).getUserIdsList())
                    .containsExactlyInAnyOrder(aliceId(), bobId());
        }

        @Test
        @DisplayName("Conflict resolution: Block stranger who then becomes friend")
        void blockStrangerThenBecomeFriend() throws Exception {
            // Alice blocks Charlie (a stranger who was spamming)
            executeBlockStrangerFlow(aliceId(), charlieId());
            Relation relation = storageFacade.getRelationBetweenUser(aliceId(), charlieId());
            assertThat(RelationFlags.BLOCKED.isSet(relation.getRelationFlags())).isTrue();

            // Later, Alice realizes Charlie is a friend's friend and unblocks
            executeUnblockUserFlow(aliceId(), charlieId());
            assertThat(storageFacade.getRelationBetweenUser(aliceId(), charlieId())).isNull();

            // Now Alice adds Charlie as friend
            executeAddFriendFlow(aliceId(), charlieId());
            relation = storageFacade.getRelationBetweenUser(aliceId(), charlieId());
            assertThat(RelationFlags.FRIEND.isSet(relation.getRelationFlags())).isTrue();
            assertThat(RelationFlags.BLOCKED.isSet(relation.getRelationFlags())).isFalse();
        }
    }
}
