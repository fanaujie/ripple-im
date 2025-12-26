package com.fanaujie.ripple.integration.group;

import com.fanaujie.ripple.integration.base.AbstractBusinessFlowTest;
import com.fanaujie.ripple.storage.model.GroupMemberInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Group Business Flow Tests")
class GroupFlowTest extends AbstractBusinessFlowTest {

    // Test users
    protected static final long ALICE_ID = 1001L;
    protected static final long BOB_ID = 2001L;
    protected static final long CHARLIE_ID = 3001L;
    protected static final long DAVID_ID = 4001L;

    // Test groups
    protected static final long GROUP_FRIENDS_ID = 10001L;
    protected static final long GROUP_WORK_ID = 10002L;

    private long versionCounter = System.currentTimeMillis();

    @BeforeEach
    void setUpTestUsers() {
        createUser(ALICE_ID, "alice", "Alice", "alice-avatar.png");
        createUser(BOB_ID, "bob", "Bob", "bob-avatar.png");
        createUser(CHARLIE_ID, "charlie", "Charlie", "charlie-avatar.png");
        createUser(DAVID_ID, "david", "David", "david-avatar.png");
    }

    private long nextVersion() {
        return ++versionCounter;
    }

    @Nested
    @DisplayName("Scenario: Create Group")
    class CreateGroupScenarios {

        @Test
        @DisplayName("When Alice creates a group with Bob and Charlie, all should be members")
        void createGroup_shouldAddAllMembers() throws Exception {
            // When: Alice creates a group with Bob and Charlie
            List<Long> memberIds = List.of(ALICE_ID, BOB_ID, CHARLIE_ID);
            createGroup(GROUP_FRIENDS_ID, memberIds, nextVersion());

            // Then: All three should be group members
            List<Long> groupMemberIds = storageFacade.getGroupMemberIds(GROUP_FRIENDS_ID);
            assertThat(groupMemberIds).containsExactlyInAnyOrder(ALICE_ID, BOB_ID, CHARLIE_ID);

            // And: Each member should have correct profile info
            List<GroupMemberInfo> members = storageFacade.getGroupMembersInfo(GROUP_FRIENDS_ID);
            assertThat(members).hasSize(3);

            GroupMemberInfo aliceMember =
                    members.stream()
                            .filter(m -> m.getUserId() == ALICE_ID)
                            .findFirst()
                            .orElseThrow();
            assertThat(aliceMember.getName()).isEqualTo("Alice");
        }

        @Test
        @DisplayName("When Alice creates multiple groups, she should be in all of them")
        void createMultipleGroups_shouldTrackAllMemberships() throws Exception {
            // When: Alice creates two different groups
            createGroup(GROUP_FRIENDS_ID, List.of(ALICE_ID, BOB_ID), nextVersion());
            createGroup(GROUP_WORK_ID, List.of(ALICE_ID, CHARLIE_ID), nextVersion());

            // Then: Alice should be in both groups
            List<Long> friendsMembers = storageFacade.getGroupMemberIds(GROUP_FRIENDS_ID);
            List<Long> workMembers = storageFacade.getGroupMemberIds(GROUP_WORK_ID);

            assertThat(friendsMembers).contains(ALICE_ID);
            assertThat(workMembers).contains(ALICE_ID);

            // And: Bob should only be in friends group
            assertThat(friendsMembers).contains(BOB_ID);
            assertThat(workMembers).doesNotContain(BOB_ID);
        }
    }

    @Nested
    @DisplayName("Scenario: Invite Members")
    class InviteMembersScenarios {

        @Test
        @DisplayName("When Alice invites David to existing group, David should become a member")
        void inviteMember_shouldAddNewMember() throws Exception {
            // Given: A group with Alice, Bob, and Charlie
            createGroup(GROUP_FRIENDS_ID, List.of(ALICE_ID, BOB_ID, CHARLIE_ID), nextVersion());

            // When: Alice invites David
            inviteGroupMembers(GROUP_FRIENDS_ID, List.of(DAVID_ID), nextVersion());

            // Then: David should be a member now
            List<Long> memberIds = storageFacade.getGroupMemberIds(GROUP_FRIENDS_ID);
            assertThat(memberIds).containsExactlyInAnyOrder(ALICE_ID, BOB_ID, CHARLIE_ID, DAVID_ID);
        }

        @Test
        @DisplayName("When inviting multiple members at once, all should be added")
        void inviteMultipleMembers_shouldAddAll() throws Exception {
            // Given: A group with only Alice
            createGroup(GROUP_FRIENDS_ID, List.of(ALICE_ID), nextVersion());

            // When: Alice invites Bob, Charlie, and David
            inviteGroupMembers(
                    GROUP_FRIENDS_ID, List.of(BOB_ID, CHARLIE_ID, DAVID_ID), nextVersion());

            // Then: All should be members
            List<Long> memberIds = storageFacade.getGroupMemberIds(GROUP_FRIENDS_ID);
            assertThat(memberIds).containsExactlyInAnyOrder(ALICE_ID, BOB_ID, CHARLIE_ID, DAVID_ID);
        }
    }

    @Nested
    @DisplayName("Scenario: Quit Group")
    class QuitGroupScenarios {

        @Test
        @DisplayName("When Bob quits the group, he should no longer be a member")
        void quitGroup_shouldRemoveMember() throws Exception {
            // Given: A group with Alice, Bob, and Charlie
            createGroup(GROUP_FRIENDS_ID, List.of(ALICE_ID, BOB_ID, CHARLIE_ID), nextVersion());

            // When: Bob quits the group
            quitGroup(GROUP_FRIENDS_ID, BOB_ID, nextVersion());

            // Then: Bob should not be a member anymore
            List<Long> memberIds = storageFacade.getGroupMemberIds(GROUP_FRIENDS_ID);
            assertThat(memberIds).containsExactlyInAnyOrder(ALICE_ID, CHARLIE_ID);
            assertThat(memberIds).doesNotContain(BOB_ID);
        }

        @Test
        @DisplayName(
                "When all members quit except one, the last member should still be in the group")
        void quitGroup_lastMember_shouldRemain() throws Exception {
            // Given: A group with Alice and Bob
            createGroup(GROUP_FRIENDS_ID, List.of(ALICE_ID, BOB_ID), nextVersion());

            // When: Bob quits
            quitGroup(GROUP_FRIENDS_ID, BOB_ID, nextVersion());

            // Then: Only Alice remains
            List<Long> memberIds = storageFacade.getGroupMemberIds(GROUP_FRIENDS_ID);
            assertThat(memberIds).containsExactly(ALICE_ID);
        }
    }

    @Nested
    @DisplayName("Scenario: Complex Group Lifecycle")
    class ComplexGroupLifecycleScenarios {

        @Test
        @DisplayName("Complete group lifecycle: create -> invite -> quit -> invite again")
        void completeGroupLifecycle() throws Exception {
            // Step 1: Create a group with Alice and Bob
            createGroup(GROUP_FRIENDS_ID, List.of(ALICE_ID, BOB_ID), nextVersion());
            assertThat(storageFacade.getGroupMemberIds(GROUP_FRIENDS_ID))
                    .containsExactlyInAnyOrder(ALICE_ID, BOB_ID);

            // Step 2: Invite Charlie
            inviteGroupMembers(GROUP_FRIENDS_ID, List.of(CHARLIE_ID), nextVersion());
            assertThat(storageFacade.getGroupMemberIds(GROUP_FRIENDS_ID))
                    .containsExactlyInAnyOrder(ALICE_ID, BOB_ID, CHARLIE_ID);

            // Step 3: Bob quits
            quitGroup(GROUP_FRIENDS_ID, BOB_ID, nextVersion());
            assertThat(storageFacade.getGroupMemberIds(GROUP_FRIENDS_ID))
                    .containsExactlyInAnyOrder(ALICE_ID, CHARLIE_ID);

            // Step 4: Invite David
            inviteGroupMembers(GROUP_FRIENDS_ID, List.of(DAVID_ID), nextVersion());
            assertThat(storageFacade.getGroupMemberIds(GROUP_FRIENDS_ID))
                    .containsExactlyInAnyOrder(ALICE_ID, CHARLIE_ID, DAVID_ID);
        }

        @Test
        @DisplayName("User can be in multiple groups simultaneously")
        void multipleGroupMemberships() throws Exception {
            // Alice creates friends group
            createGroup(GROUP_FRIENDS_ID, List.of(ALICE_ID, BOB_ID), nextVersion());

            // Charlie creates work group and invites Alice
            createGroup(GROUP_WORK_ID, List.of(CHARLIE_ID, ALICE_ID), nextVersion());

            // Verify Alice is in both groups
            List<Long> friendsMembers = storageFacade.getGroupMemberIds(GROUP_FRIENDS_ID);
            List<Long> workMembers = storageFacade.getGroupMemberIds(GROUP_WORK_ID);

            assertThat(friendsMembers).contains(ALICE_ID);
            assertThat(workMembers).contains(ALICE_ID);

            // Alice quits friends group but stays in work group
            quitGroup(GROUP_FRIENDS_ID, ALICE_ID, nextVersion());

            friendsMembers = storageFacade.getGroupMemberIds(GROUP_FRIENDS_ID);
            workMembers = storageFacade.getGroupMemberIds(GROUP_WORK_ID);

            assertThat(friendsMembers).doesNotContain(ALICE_ID);
            assertThat(workMembers).contains(ALICE_ID);
        }
    }
}
