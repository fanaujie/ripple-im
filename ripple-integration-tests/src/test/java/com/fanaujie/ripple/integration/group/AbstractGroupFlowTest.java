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
public abstract class AbstractGroupFlowTest extends AbstractBusinessFlowTest {

    protected long aliceId() {
        return testIdBase + 1;
    }

    protected long bobId() {
        return testIdBase + 2;
    }

    protected long charlieId() {
        return testIdBase + 3;
    }

    protected long davidId() {
        return testIdBase + 4;
    }

    protected long groupFriendsId() {
        return testIdBase + 10001;
    }

    protected long groupWorkId() {
        return testIdBase + 10002;
    }

    private long versionCounter = System.currentTimeMillis();

    @BeforeEach
    void setUpTestUsers() {
        createUser(aliceId(), "alice-" + testIdBase, "Alice", "alice-avatar.png");
        createUser(bobId(), "bob-" + testIdBase, "Bob", "bob-avatar.png");
        createUser(charlieId(), "charlie-" + testIdBase, "Charlie", "charlie-avatar.png");
        createUser(davidId(), "david-" + testIdBase, "David", "david-avatar.png");
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
            List<Long> memberIds = List.of(aliceId(), bobId(), charlieId());
            createGroup(groupFriendsId(), memberIds, nextVersion());

            // Then: All three should be group members
            List<Long> groupMemberIds = storageFacade.getGroupMemberIds(groupFriendsId());
            assertThat(groupMemberIds).containsExactlyInAnyOrder(aliceId(), bobId(), charlieId());

            // And: Each member should have correct profile info
            List<GroupMemberInfo> members = storageFacade.getGroupMembersInfo(groupFriendsId());
            assertThat(members).hasSize(3);

            GroupMemberInfo aliceMember =
                    members.stream()
                            .filter(m -> m.getUserId() == aliceId())
                            .findFirst()
                            .orElseThrow();
            assertThat(aliceMember.getName()).isEqualTo("Alice");
        }

        @Test
        @DisplayName("When Alice creates multiple groups, she should be in all of them")
        void createMultipleGroups_shouldTrackAllMemberships() throws Exception {
            // When: Alice creates two different groups
            createGroup(groupFriendsId(), List.of(aliceId(), bobId()), nextVersion());
            createGroup(groupWorkId(), List.of(aliceId(), charlieId()), nextVersion());

            // Then: Alice should be in both groups
            List<Long> friendsMembers = storageFacade.getGroupMemberIds(groupFriendsId());
            List<Long> workMembers = storageFacade.getGroupMemberIds(groupWorkId());

            assertThat(friendsMembers).contains(aliceId());
            assertThat(workMembers).contains(aliceId());

            // And: Bob should only be in friends group
            assertThat(friendsMembers).contains(bobId());
            assertThat(workMembers).doesNotContain(bobId());
        }
    }

    @Nested
    @DisplayName("Scenario: Invite Members")
    class InviteMembersScenarios {

        @Test
        @DisplayName("When Alice invites David to existing group, David should become a member")
        void inviteMember_shouldAddNewMember() throws Exception {
            // Given: A group with Alice, Bob, and Charlie
            createGroup(groupFriendsId(), List.of(aliceId(), bobId(), charlieId()), nextVersion());

            // When: Alice invites David
            inviteGroupMembers(groupFriendsId(), List.of(davidId()), nextVersion());

            // Then: David should be a member now
            List<Long> memberIds = storageFacade.getGroupMemberIds(groupFriendsId());
            assertThat(memberIds)
                    .containsExactlyInAnyOrder(aliceId(), bobId(), charlieId(), davidId());
        }

        @Test
        @DisplayName("When inviting multiple members at once, all should be added")
        void inviteMultipleMembers_shouldAddAll() throws Exception {
            // Given: A group with only Alice
            createGroup(groupFriendsId(), List.of(aliceId()), nextVersion());

            // When: Alice invites Bob, Charlie, and David
            inviteGroupMembers(
                    groupFriendsId(), List.of(bobId(), charlieId(), davidId()), nextVersion());

            // Then: All should be members
            List<Long> memberIds = storageFacade.getGroupMemberIds(groupFriendsId());
            assertThat(memberIds)
                    .containsExactlyInAnyOrder(aliceId(), bobId(), charlieId(), davidId());
        }
    }

    @Nested
    @DisplayName("Scenario: Quit Group")
    class QuitGroupScenarios {

        @Test
        @DisplayName("When Bob quits the group, he should no longer be a member")
        void quitGroup_shouldRemoveMember() throws Exception {
            // Given: A group with Alice, Bob, and Charlie
            createGroup(groupFriendsId(), List.of(aliceId(), bobId(), charlieId()), nextVersion());

            // When: Bob quits the group
            quitGroup(groupFriendsId(), bobId(), nextVersion());

            // Then: Bob should not be a member anymore
            List<Long> memberIds = storageFacade.getGroupMemberIds(groupFriendsId());
            assertThat(memberIds).containsExactlyInAnyOrder(aliceId(), charlieId());
            assertThat(memberIds).doesNotContain(bobId());
        }

        @Test
        @DisplayName(
                "When all members quit except one, the last member should still be in the group")
        void quitGroup_lastMember_shouldRemain() throws Exception {
            // Given: A group with Alice and Bob
            createGroup(groupFriendsId(), List.of(aliceId(), bobId()), nextVersion());

            // When: Bob quits
            quitGroup(groupFriendsId(), bobId(), nextVersion());

            // Then: Only Alice remains
            List<Long> memberIds = storageFacade.getGroupMemberIds(groupFriendsId());
            assertThat(memberIds).containsExactly(aliceId());
        }
    }

    @Nested
    @DisplayName("Scenario: Complex Group Lifecycle")
    class ComplexGroupLifecycleScenarios {

        @Test
        @DisplayName("Complete group lifecycle: create -> invite -> quit -> invite again")
        void completeGroupLifecycle() throws Exception {
            // Step 1: Create a group with Alice and Bob
            createGroup(groupFriendsId(), List.of(aliceId(), bobId()), nextVersion());
            assertThat(storageFacade.getGroupMemberIds(groupFriendsId()))
                    .containsExactlyInAnyOrder(aliceId(), bobId());

            // Step 2: Invite Charlie
            inviteGroupMembers(groupFriendsId(), List.of(charlieId()), nextVersion());
            assertThat(storageFacade.getGroupMemberIds(groupFriendsId()))
                    .containsExactlyInAnyOrder(aliceId(), bobId(), charlieId());

            // Step 3: Bob quits
            quitGroup(groupFriendsId(), bobId(), nextVersion());
            assertThat(storageFacade.getGroupMemberIds(groupFriendsId()))
                    .containsExactlyInAnyOrder(aliceId(), charlieId());

            // Step 4: Invite David
            inviteGroupMembers(groupFriendsId(), List.of(davidId()), nextVersion());
            assertThat(storageFacade.getGroupMemberIds(groupFriendsId()))
                    .containsExactlyInAnyOrder(aliceId(), charlieId(), davidId());
        }

        @Test
        @DisplayName("User can be in multiple groups simultaneously")
        void multipleGroupMemberships() throws Exception {
            // Alice creates friends group
            createGroup(groupFriendsId(), List.of(aliceId(), bobId()), nextVersion());

            // Charlie creates work group and invites Alice
            createGroup(groupWorkId(), List.of(charlieId(), aliceId()), nextVersion());

            // Verify Alice is in both groups
            List<Long> friendsMembers = storageFacade.getGroupMemberIds(groupFriendsId());
            List<Long> workMembers = storageFacade.getGroupMemberIds(groupWorkId());

            assertThat(friendsMembers).contains(aliceId());
            assertThat(workMembers).contains(aliceId());

            // Alice quits friends group but stays in work group
            quitGroup(groupFriendsId(), aliceId(), nextVersion());

            friendsMembers = storageFacade.getGroupMemberIds(groupFriendsId());
            workMembers = storageFacade.getGroupMemberIds(groupWorkId());

            assertThat(friendsMembers).doesNotContain(aliceId());
            assertThat(workMembers).contains(aliceId());
        }
    }
}
