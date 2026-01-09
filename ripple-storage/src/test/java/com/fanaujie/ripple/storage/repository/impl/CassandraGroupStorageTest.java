package com.fanaujie.ripple.storage.repository.impl;

import com.datastax.oss.driver.api.core.CqlSession;
import com.fanaujie.ripple.storage.exception.InvalidVersionException;
import com.fanaujie.ripple.storage.exception.NotFoundGroupException;
import com.fanaujie.ripple.storage.model.*;
import com.fanaujie.ripple.storage.service.impl.cassandra.CassandraStorageFacade;
import com.fanaujie.ripple.storage.service.impl.cassandra.CassandraStorageFacadeBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class CassandraGroupStorageTest {

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
            session.execute("TRUNCATE ripple.group_members");
            session.execute("TRUNCATE ripple.group_members_version");
            session.execute("TRUNCATE ripple.user_group");
            session.execute("TRUNCATE ripple.user_group_version");
            session.execute("TRUNCATE ripple.user_conversations");
            session.execute("TRUNCATE ripple.user_conversations_version");
            session.execute("TRUNCATE ripple.user_messages");
            session.close();
        }
    }

    // ==================== createGroup Tests ====================

    @Test
    void createGroup_shouldCreateGroupMembers() throws NotFoundGroupException {
        long groupId = 1001L;
        List<UserProfile> members =
                Arrays.asList(
                        new UserProfile(2001L, "user1", "User One", "avatar1.png"),
                        new UserProfile(2002L, "user2", "User Two", "avatar2.png"));
        long version = System.currentTimeMillis();

        storageFacade.createGroup(groupId, members, version);

        List<Long> memberIds = storageFacade.getGroupMemberIds(groupId);
        assertEquals(2, memberIds.size());
        assertTrue(memberIds.contains(2001L));
        assertTrue(memberIds.contains(2002L));
    }

    @Test
    void createGroup_shouldCreateVersionRecord() throws InvalidVersionException {
        long groupId = 1002L;
        List<UserProfile> members =
                Arrays.asList(new UserProfile(2003L, "user3", "User Three", "avatar3.png"));
        long version = System.currentTimeMillis();

        storageFacade.createGroup(groupId, members, version);

        List<GroupVersionChange> changes = storageFacade.getGroupChanges(groupId, null, 10);
        assertNotNull(changes);
        assertEquals(1, changes.size());
        assertEquals(String.valueOf(version), changes.get(0).getVersion());
    }

    // ==================== createUserGroupAndConversation Tests ====================

    @Test
    void createUserGroupAndConversation_shouldCreateUserGroupEntry() {
        long userId = 3001L;
        long groupId = 1003L;
        String groupName = "Test Group";
        String groupAvatar = "group-avatar.png";
        long version = System.currentTimeMillis();

        storageFacade.createUserGroupAndConversation(userId, groupId, groupName, groupAvatar, version);

        List<UserGroup> groups = storageFacade.getUserGroups(userId);
        assertEquals(1, groups.size());
        assertEquals(groupId, groups.get(0).getGroupId());
        assertEquals(groupName, groups.get(0).getGroupName());
        assertEquals(groupAvatar, groups.get(0).getGroupAvatar());
    }

    @Test
    void createUserGroupAndConversation_shouldCreateConversation() {
        long userId = 3002L;
        long groupId = 1004L;
        String groupName = "Conversation Test";
        String groupAvatar = "conv-avatar.png";
        long version = System.currentTimeMillis();

        storageFacade.createUserGroupAndConversation(userId, groupId, groupName, groupAvatar, version);

        PagedConversationResult result = storageFacade.getConversations(userId, null, 10);
        assertEquals(1, result.getConversations().size());
        assertEquals(groupId, result.getConversations().get(0).getGroupId());
        assertEquals(groupName, result.getConversations().get(0).getName());
    }

    // ==================== createGroupMembersProfile Tests ====================

    @Test
    void createGroupMembersProfile_shouldAddMembersToGroup() throws NotFoundGroupException {
        long groupId = 1005L;
        List<UserProfile> initialMembers =
                Arrays.asList(new UserProfile(2010L, "user10", "User Ten", "avatar10.png"));
        long version1 = System.currentTimeMillis();

        storageFacade.createGroup(groupId, initialMembers, version1);

        List<UserProfile> newMembers =
                Arrays.asList(
                        new UserProfile(2011L, "user11", "User Eleven", "avatar11.png"),
                        new UserProfile(2012L, "user12", "User Twelve", "avatar12.png"));
        long version2 = version1 + 1;

        storageFacade.createGroupMembersProfile(groupId, newMembers, version2);

        List<Long> memberIds = storageFacade.getGroupMemberIds(groupId);
        assertEquals(3, memberIds.size());
        assertTrue(memberIds.contains(2010L));
        assertTrue(memberIds.contains(2011L));
        assertTrue(memberIds.contains(2012L));
    }

    // ==================== updateGroupMemberName Tests ====================

    @Test
    void updateGroupMemberName_shouldUpdateMemberName() throws NotFoundGroupException {
        long groupId = 1006L;
        long userId = 2020L;
        String originalName = "Original Name";
        String newName = "Updated Name";
        long version1 = System.currentTimeMillis();

        storageFacade.createGroup(
                groupId,
                Arrays.asList(new UserProfile(userId, "user20", originalName, "avatar.png")),
                version1);

        long version2 = version1 + 1;
        storageFacade.updateGroupMemberName(groupId, userId, newName, version2);

        List<GroupMemberInfo> members = storageFacade.getGroupMembersInfo(groupId);
        assertEquals(1, members.size());
        assertEquals(newName, members.get(0).getName());
    }

    // ==================== updateGroupMemberAvatar Tests ====================

    @Test
    void updateGroupMemberAvatar_shouldUpdateMemberAvatar() throws NotFoundGroupException {
        long groupId = 1007L;
        long userId = 2021L;
        String originalAvatar = "original.png";
        String newAvatar = "updated.png";
        long version1 = System.currentTimeMillis();

        storageFacade.createGroup(
                groupId,
                Arrays.asList(new UserProfile(userId, "user21", "Name", originalAvatar)),
                version1);

        long version2 = version1 + 1;
        storageFacade.updateGroupMemberAvatar(groupId, userId, newAvatar, version2);

        List<GroupMemberInfo> members = storageFacade.getGroupMembersInfo(groupId);
        assertEquals(1, members.size());
        assertEquals(newAvatar, members.get(0).getAvatar());
    }

    // ==================== removeGroupMember Tests ====================

    @Test
    void removeGroupMember_shouldDeleteMember() throws NotFoundGroupException {
        long groupId = 1008L;
        long userId1 = 2030L;
        long userId2 = 2031L;
        long version1 = System.currentTimeMillis();

        storageFacade.createGroup(
                groupId,
                Arrays.asList(
                        new UserProfile(userId1, "user30", "User Thirty", "a.png"),
                        new UserProfile(userId2, "user31", "User ThirtyOne", "b.png")),
                version1);

        long version2 = version1 + 1;
        storageFacade.removeGroupMember(groupId, userId1, version2);

        List<Long> memberIds = storageFacade.getGroupMemberIds(groupId);
        assertEquals(1, memberIds.size());
        assertFalse(memberIds.contains(userId1));
        assertTrue(memberIds.contains(userId2));
    }

    // ==================== getGroupMemberIds Tests ====================

    @Test
    void getGroupMemberIds_shouldReturnMemberIds() throws NotFoundGroupException {
        long groupId = 1009L;
        List<UserProfile> members =
                Arrays.asList(
                        new UserProfile(2040L, "user40", "User Forty", "a.png"),
                        new UserProfile(2041L, "user41", "User FortyOne", "b.png"));
        storageFacade.createGroup(groupId, members, System.currentTimeMillis());

        List<Long> memberIds = storageFacade.getGroupMemberIds(groupId);

        assertEquals(2, memberIds.size());
        assertTrue(memberIds.contains(2040L));
        assertTrue(memberIds.contains(2041L));
    }

    @Test
    void getGroupMemberIds_shouldThrowExceptionWhenGroupNotFound() {
        long nonExistentGroupId = 9999L;

        assertThrows(
                NotFoundGroupException.class, () -> storageFacade.getGroupMemberIds(nonExistentGroupId));
    }

    // ==================== getGroupMembersInfo Tests ====================

    @Test
    void getGroupMembersInfo_shouldReturnMemberInfo() throws NotFoundGroupException {
        long groupId = 1010L;
        List<UserProfile> members =
                Arrays.asList(new UserProfile(2042L, "user42", "User FortyTwo", "avatar42.png"));
        storageFacade.createGroup(groupId, members, System.currentTimeMillis());

        List<GroupMemberInfo> memberInfos = storageFacade.getGroupMembersInfo(groupId);

        assertEquals(1, memberInfos.size());
        GroupMemberInfo info = memberInfos.get(0);
        assertEquals(groupId, info.getGroupId());
        assertEquals(2042L, info.getUserId());
        assertEquals("User FortyTwo", info.getName());
        assertEquals("avatar42.png", info.getAvatar());
    }

    @Test
    void getGroupMembersInfo_shouldThrowExceptionWhenGroupNotFound() {
        long nonExistentGroupId = 9998L;

        assertThrows(
                NotFoundGroupException.class,
                () -> storageFacade.getGroupMembersInfo(nonExistentGroupId));
    }

    // ==================== getUserGroupIds Tests ====================

    @Test
    void getUserGroupIds_shouldReturnGroupIds() {
        long userId = 3010L;
        long version = System.currentTimeMillis();
        storageFacade.createUserGroupAndConversation(userId, 1011L, "Group1", "a.png", version);
        storageFacade.createUserGroupAndConversation(userId, 1012L, "Group2", "b.png", version + 1);

        List<Long> groupIds = storageFacade.getUserGroupIds(userId);

        assertEquals(2, groupIds.size());
        assertTrue(groupIds.contains(1011L));
        assertTrue(groupIds.contains(1012L));
    }

    @Test
    void getUserGroupIds_shouldReturnEmptyListWhenNoGroups() {
        long userWithNoGroups = 3011L;

        List<Long> groupIds = storageFacade.getUserGroupIds(userWithNoGroups);

        assertTrue(groupIds.isEmpty());
    }

    // ==================== getUserGroups Tests ====================

    @Test
    void getUserGroups_shouldReturnUserGroups() {
        long userId = 3012L;
        long version = System.currentTimeMillis();
        storageFacade.createUserGroupAndConversation(userId, 1013L, "Group One", "avatar1.png", version);

        List<UserGroup> groups = storageFacade.getUserGroups(userId);

        assertEquals(1, groups.size());
        assertEquals(1013L, groups.get(0).getGroupId());
        assertEquals("Group One", groups.get(0).getGroupName());
        assertEquals("avatar1.png", groups.get(0).getGroupAvatar());
    }

    // ==================== getGroupMembersPaged Tests ====================

    @Test
    void getGroupMembersPaged_shouldReturnFirstPage() throws NotFoundGroupException {
        long groupId = 1014L;
        List<UserProfile> members =
                Arrays.asList(
                        new UserProfile(2050L, "u50", "User50", "a.png"),
                        new UserProfile(2051L, "u51", "User51", "b.png"),
                        new UserProfile(2052L, "u52", "User52", "c.png"),
                        new UserProfile(2053L, "u53", "User53", "d.png"),
                        new UserProfile(2054L, "u54", "User54", "e.png"));
        storageFacade.createGroup(groupId, members, System.currentTimeMillis());

        PagedGroupMemberResult result = storageFacade.getGroupMembersPaged(groupId, null, 3);

        assertEquals(3, result.getMembers().size());
        assertTrue(result.isHasMore());
        assertNotNull(result.getNextPageToken());
    }

    @Test
    void getGroupMembersPaged_shouldReturnSecondPage() throws NotFoundGroupException {
        long groupId = 1015L;
        List<UserProfile> members =
                Arrays.asList(
                        new UserProfile(2060L, "u60", "User60", "a.png"),
                        new UserProfile(2061L, "u61", "User61", "b.png"),
                        new UserProfile(2062L, "u62", "User62", "c.png"),
                        new UserProfile(2063L, "u63", "User63", "d.png"),
                        new UserProfile(2064L, "u64", "User64", "e.png"));
        storageFacade.createGroup(groupId, members, System.currentTimeMillis());

        PagedGroupMemberResult firstPage = storageFacade.getGroupMembersPaged(groupId, null, 3);
        PagedGroupMemberResult secondPage =
                storageFacade.getGroupMembersPaged(groupId, firstPage.getNextPageToken(), 3);

        assertEquals(2, secondPage.getMembers().size());
        assertFalse(secondPage.isHasMore());
        assertNull(secondPage.getNextPageToken());
    }

    // ==================== getUserGroupsPaged Tests ====================

    @Test
    void getUserGroupsPaged_shouldReturnFirstPage() {
        long userId = 3020L;
        long version = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) {
            storageFacade.createUserGroupAndConversation(
                    userId, 1020L + i, "Group" + i, "avatar.png", version + i);
        }

        PagedUserGroupResult result = storageFacade.getUserGroupsPaged(userId, null, 3);

        assertEquals(3, result.getGroups().size());
        assertTrue(result.isHasMore());
        assertNotNull(result.getNextPageToken());
    }

    @Test
    void getUserGroupsPaged_shouldReturnSecondPage() {
        long userId = 3021L;
        long version = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) {
            storageFacade.createUserGroupAndConversation(
                    userId, 1030L + i, "Group" + i, "avatar.png", version + i);
        }

        PagedUserGroupResult firstPage = storageFacade.getUserGroupsPaged(userId, null, 3);
        PagedUserGroupResult secondPage =
                storageFacade.getUserGroupsPaged(userId, firstPage.getNextPageToken(), 3);

        assertEquals(2, secondPage.getGroups().size());
        assertFalse(secondPage.isHasMore());
        assertNull(secondPage.getNextPageToken());
    }

    // ==================== getLatestGroupVersion Tests ====================

    @Test
    void getLatestGroupVersion_shouldReturnLatestVersion() {
        long groupId = 1040L;
        long version1 = 1000L;
        long version2 = 2000L;

        storageFacade.createGroup(
                groupId,
                Arrays.asList(new UserProfile(2070L, "u70", "User70", "a.png")),
                version1);
        storageFacade.updateGroupMemberName(groupId, 2070L, "Updated Name", version2);

        String latestVersion = storageFacade.getLatestGroupVersion(groupId);

        assertNotNull(latestVersion);
        assertEquals(String.valueOf(version2), latestVersion);
    }

    @Test
    void getLatestGroupVersion_shouldReturnNullWhenNoVersions() {
        long nonExistentGroupId = 9996L;

        String latestVersion = storageFacade.getLatestGroupVersion(nonExistentGroupId);

        assertNull(latestVersion);
    }

    // ==================== getLatestUserGroupVersion Tests ====================

    @Test
    void getLatestUserGroupVersion_shouldReturnLatestVersion() {
        long userId = 3030L;
        long version = System.currentTimeMillis();
        storageFacade.createUserGroupAndConversation(userId, 1050L, "Group", "a.png", version);

        String latestVersion = storageFacade.getLatestUserGroupVersion(userId);

        assertNotNull(latestVersion);
        assertEquals(String.valueOf(version), latestVersion);
    }

    @Test
    void getLatestUserGroupVersion_shouldReturnNullWhenNoVersions() {
        long userId = 3031L;

        String latestVersion = storageFacade.getLatestUserGroupVersion(userId);

        assertNull(latestVersion);
    }

    // ==================== removeUserGroup Tests ====================

    @Test
    void removeUserGroup_shouldDeleteUserGroup() {
        long userId = 3032L;
        long groupId = 1051L;
        long version1 = System.currentTimeMillis();
        storageFacade.createUserGroupAndConversation(userId, groupId, "Group", "a.png", version1);

        long version2 = version1 + 1;
        storageFacade.removeUserGroup(userId, groupId, version2);

        List<Long> groupIds = storageFacade.getUserGroupIds(userId);
        assertTrue(groupIds.isEmpty());
    }

    // ==================== updateUserGroupName Tests ====================

    @Test
    void updateUserGroupName_shouldUpdateName() {
        long userId = 3033L;
        long groupId = 1052L;
        String originalName = "Original";
        String newName = "Updated";
        long version1 = System.currentTimeMillis();
        storageFacade.createUserGroupAndConversation(userId, groupId, originalName, "a.png", version1);

        long version2 = version1 + 1;
        storageFacade.updateUserGroupName(userId, groupId, newName, version2);

        List<UserGroup> groups = storageFacade.getUserGroups(userId);
        assertEquals(1, groups.size());
        assertEquals(newName, groups.get(0).getGroupName());
    }

    // ==================== updateUserGroupAvatar Tests ====================

    @Test
    void updateUserGroupAvatar_shouldUpdateAvatar() {
        long userId = 3034L;
        long groupId = 1053L;
        String originalAvatar = "original.png";
        String newAvatar = "updated.png";
        long version1 = System.currentTimeMillis();
        storageFacade.createUserGroupAndConversation(userId, groupId, "Group", originalAvatar, version1);

        long version2 = version1 + 1;
        storageFacade.updateUserGroupAvatar(userId, groupId, newAvatar, version2);

        List<UserGroup> groups = storageFacade.getUserGroups(userId);
        assertEquals(1, groups.size());
        assertEquals(newAvatar, groups.get(0).getGroupAvatar());
    }

    // ==================== getUserGroupChanges Tests ====================

    @Test
    void getUserGroupChanges_shouldReturnChangesAfterVersion() throws InvalidVersionException {
        long userId = 3035L;
        long version1 = 1000L;
        long version2 = 2000L;
        storageFacade.createUserGroupAndConversation(userId, 1054L, "Group1", "a.png", version1);
        storageFacade.createUserGroupAndConversation(userId, 1055L, "Group2", "b.png", version2);

        List<UserGroupVersionChange> changes =
                storageFacade.getUserGroupChanges(userId, String.valueOf(version1), 10);

        assertEquals(1, changes.size());
        assertEquals(1055L, changes.get(0).getGroupId());
    }

    @Test
    void getUserGroupChanges_shouldThrowExceptionForInvalidVersion() {
        long userId = 3036L;
        String invalidVersion = "not-a-number";

        assertThrows(
                InvalidVersionException.class,
                () -> storageFacade.getUserGroupChanges(userId, invalidVersion, 10));
    }

    // ==================== getGroupChanges Tests ====================

    @Test
    void getGroupChanges_shouldReturnChangesAfterVersion() throws InvalidVersionException {
        long groupId = 1056L;
        long version1 = 1000L;
        long version2 = 2000L;

        storageFacade.createGroup(
                groupId,
                Arrays.asList(new UserProfile(2080L, "u80", "User80", "a.png")),
                version1);
        storageFacade.updateGroupMemberName(groupId, 2080L, "Updated Name", version2);

        List<GroupVersionChange> changes =
                storageFacade.getGroupChanges(groupId, String.valueOf(version1), 10);

        assertEquals(1, changes.size());
        assertEquals(String.valueOf(version2), changes.get(0).getVersion());
    }

    @Test
    void getGroupChanges_shouldThrowExceptionForInvalidVersion() {
        long groupId = 1057L;
        String invalidVersion = "not-a-number";

        assertThrows(
                InvalidVersionException.class,
                () -> storageFacade.getGroupChanges(groupId, invalidVersion, 10));
    }

    @Test
    void getGroupChanges_shouldReturnAllChangesWithNullVersion() throws InvalidVersionException {
        long groupId = 1058L;
        long version = 1000L;

        storageFacade.createGroup(
                groupId,
                Arrays.asList(new UserProfile(2081L, "u81", "User81", "a.png")),
                version);

        List<GroupVersionChange> changes = storageFacade.getGroupChanges(groupId, null, 10);

        assertEquals(1, changes.size());
    }

    // ==================== saveGroupCommandMessage Tests ====================

    @Test
    void saveGroupCommandMessage_shouldSaveCommandMessage() {
        String conversationId = "group_1059";
        long messageId = 5001L;
        long senderId = 2090L;
        long groupId = 1059L;
        long timestamp = System.currentTimeMillis();
        byte commandType = 1;
        String commandData = "{\"userId\": 2090, \"name\": \"User Ninety\"}";

        storageFacade.saveGroupCommandMessage(
                conversationId, messageId, senderId, groupId, timestamp, commandType, commandData);

        Messages messages = storageFacade.getMessages(conversationId, 0, 10);
        assertEquals(1, messages.getMessages().size());
        Message msg = messages.getMessages().get(0);
        assertEquals(messageId, msg.getMessageId());
        assertEquals(senderId, msg.getSenderId());
        assertEquals(groupId, msg.getGroupId());
        assertEquals(commandType, msg.getCommandType());
        assertEquals(commandData, msg.getCommandData());
    }

    // ==================== removeGroupConversation Tests ====================

    @Test
    void removeGroupConversation_shouldRemoveConversation() {
        long userId = 3040L;
        long groupId = 1060L;
        long version = System.currentTimeMillis();
        storageFacade.createUserGroupAndConversation(userId, groupId, "Group", "a.png", version);

        PagedConversationResult before = storageFacade.getConversations(userId, null, 10);
        assertEquals(1, before.getConversations().size());

        storageFacade.removeGroupConversation(userId, groupId, System.currentTimeMillis());

        PagedConversationResult after = storageFacade.getConversations(userId, null, 10);
        assertTrue(after.getConversations().isEmpty());
    }
}
