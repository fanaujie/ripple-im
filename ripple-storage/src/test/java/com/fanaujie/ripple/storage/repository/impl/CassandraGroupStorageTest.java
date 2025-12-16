package com.fanaujie.ripple.storage.repository.impl;
//
// import com.datastax.oss.driver.api.core.CqlSession;
// import com.datastax.oss.driver.api.core.cql.ResultSet;
// import com.datastax.oss.driver.api.core.cql.Row;
// import com.datastax.oss.driver.api.core.data.UdtValue;
// import com.fanaujie.ripple.storage.exception.InvalidVersionException;
// import com.fanaujie.ripple.storage.exception.NotFoundGroupException;
// import com.fanaujie.ripple.storage.model.*;
// import com.fanaujie.ripple.storage.service.impl.cassandra.CassandraUserStorageFacade;
// import com.fanaujie.ripple.storage.service.impl.cassandra.CassandraUserStorageFacadeBuilder;
// import org.junit.jupiter.api.AfterEach;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.testcontainers.containers.CassandraContainer;
// import org.testcontainers.junit.jupiter.Container;
// import org.testcontainers.junit.jupiter.Testcontainers;
//
// import java.util.Arrays;
// import java.util.List;
//
// import static org.junit.jupiter.api.Assertions.*;
//
// @Testcontainers
// class CassandraGroupStorageTest {
//
//    @Container
//    CassandraContainer<?> cassandraContainer =
//            new CassandraContainer<>("cassandra:5.0.5").withInitScript("ripple.cql");
//
//    private CqlSession session;
//    private CassandraUserStorageFacade storageFacade;
//
//    @BeforeEach
//    void setUp() {
//        this.session =
//                CqlSession.builder()
//                        .addContactPoint(cassandraContainer.getContactPoint())
//                        .withLocalDatacenter(cassandraContainer.getLocalDatacenter())
//                        .build();
//        this.storageFacade =
//                new CassandraUserStorageFacadeBuilder().cqlSession(session).build();
//    }
//
//    @AfterEach
//    void tearDown() {
//        if (session != null) {
//            session.execute("TRUNCATE ripple.groups_metadata");
//            session.execute("TRUNCATE ripple.group_members");
//            session.execute("TRUNCATE ripple.group_members_version");
//            session.execute("TRUNCATE ripple.user_groups");
//            session.execute("TRUNCATE ripple.user_groups_version");
//            session.execute("TRUNCATE ripple.user_conversations");
//            session.execute("TRUNCATE ripple.user_conversations_version");
//            session.execute("TRUNCATE ripple.user_messages");
//            session.close();
//        }
//    }
//
//    // ==================== createGroup Tests ====================
//
//    @Test
//    void createGroup_shouldCreateGroupMetadataAndMembers() {
//        // Given
//        long groupId = 1001L;
//        String groupName = "Test Group";
//        String groupAvatar = "http://example.com/avatar.png";
//        List<UserProfile> members = Arrays.asList(
//                new UserProfile(2001L, "user1", "User One", "http://avatar1.png"),
//                new UserProfile(2002L, "user2", "User Two", "http://avatar2.png"));
//        long version = System.currentTimeMillis();
//
//        // When
//        storageFacade.createGroup(groupId, groupName, groupAvatar, members, version);
//
//        // Then - verify groups_metadata
//        ResultSet metadataRs =
//                session.execute(
//                        "SELECT * FROM ripple.groups_metadata WHERE group_id = ?", groupId);
//        Row metadataRow = metadataRs.one();
//        assertNotNull(metadataRow);
//        assertEquals(groupName, metadataRow.getString("group_name"));
//        assertEquals(groupAvatar, metadataRow.getString("group_avatar"));
//
//        // Then - verify group_members
//        ResultSet membersRs =
//                session.execute(
//                        "SELECT * FROM ripple.group_members WHERE group_id = ?", groupId);
//        List<Row> memberRows = membersRs.all();
//        assertEquals(2, memberRows.size());
//    }
//
//    @Test
//    void createGroup_shouldCreateVersionRecordWithAllChanges() {
//        // Given
//        long groupId = 1002L;
//        String groupName = "Version Test Group";
//        String groupAvatar = "http://example.com/avatar2.png";
//        List<UserProfile> members = Arrays.asList(
//                new UserProfile(2003L, "user3", "User Three", "http://avatar3.png"));
//        long version = System.currentTimeMillis();
//
//        // When
//        storageFacade.createGroup(groupId, groupName, groupAvatar, members, version);
//
//        // Then - verify group_members_version
//        ResultSet versionRs =
//                session.execute(
//                        "SELECT * FROM ripple.group_members_version WHERE group_id = ?", groupId);
//        Row versionRow = versionRs.one();
//        assertNotNull(versionRow);
//        assertEquals(version, versionRow.getLong("version"));
//
//        List<UdtValue> changes = versionRow.getList("changes", UdtValue.class);
//        assertNotNull(changes);
//        // Should have: op=1 (create_group), op=3 (member_join)
//        assertEquals(2, changes.size());
//
//        // First change: create_group (op=1) with group_name and group_avatar
//        assertEquals(1, changes.get(0).getByte("operation"));
//        assertEquals(groupName, changes.get(0).getString("group_name"));
//        assertEquals(groupAvatar, changes.get(0).getString("group_avatar"));
//
//        // Second change: member_join (op=3)
//        assertEquals(3, changes.get(1).getByte("operation"));
//        assertEquals(2003L, changes.get(1).getLong("user_id"));
//    }
//
//    // ==================== createUserGroupAndConversation Tests ====================
//
//    @Test
//    void createUserGroupAndConversation_shouldCreateUserGroupEntry() {
//        // Given
//        long userId = 3001L;
//        long groupId = 1003L;
//        String groupName = "User Group Test";
//        String groupAvatar = "http://example.com/group.png";
//        long version = System.currentTimeMillis();
//
//        // When
//        storageFacade.createUserGroupAndConversation(userId, groupId, groupName, groupAvatar,
// version);
//
//        // Then - verify user_groups
//        ResultSet userGroupRs =
//                session.execute(
//                        "SELECT * FROM ripple.user_groups WHERE user_id = ? AND group_id = ?",
//                        userId, groupId);
//        Row userGroupRow = userGroupRs.one();
//        assertNotNull(userGroupRow);
//        assertEquals(groupName, userGroupRow.getString("group_name"));
//        assertEquals(groupAvatar, userGroupRow.getString("group_avatar"));
//    }
//
//    @Test
//    void createUserGroupAndConversation_shouldCreateVersionWithJoinOperation() {
//        // Given
//        long userId = 3002L;
//        long groupId = 1004L;
//        String groupName = "Version Test";
//        String groupAvatar = "http://example.com/group2.png";
//        long version = System.currentTimeMillis();
//
//        // When
//        storageFacade.createUserGroupAndConversation(userId, groupId, groupName, groupAvatar,
// version);
//
//        // Then - verify user_groups_version has operation=1 (join)
//        ResultSet versionRs =
//                session.execute(
//                        "SELECT * FROM ripple.user_groups_version WHERE user_id = ?", userId);
//        Row versionRow = versionRs.one();
//        assertNotNull(versionRow);
//        assertEquals(groupId, versionRow.getLong("group_id"));
//        assertEquals((byte) 1, versionRow.getByte("operation")); // op=1: join
//        assertEquals(groupName, versionRow.getString("group_name"));
//        assertEquals(groupAvatar, versionRow.getString("group_avatar"));
//    }
//
//    @Test
//    void createUserGroupAndConversation_shouldCreateConversation() {
//        // Given
//        long userId = 3003L;
//        long groupId = 1005L;
//        String groupName = "Conversation Test";
//        String groupAvatar = "http://example.com/group3.png";
//        long version = System.currentTimeMillis();
//
//        // When
//        storageFacade.createUserGroupAndConversation(userId, groupId, groupName, groupAvatar,
// version);
//
//        // Then - verify user_conversations
//        ResultSet convRs =
//                session.execute(
//                        "SELECT * FROM ripple.user_conversations WHERE owner_id = ?", userId);
//        Row convRow = convRs.one();
//        assertNotNull(convRow);
//        assertEquals(groupId, convRow.getLong("group_id"));
//        assertEquals(groupName, convRow.getString("name"));
//        assertEquals(groupAvatar, convRow.getString("avatar"));
//    }
//
//    // ==================== createGroupMembersProfile Tests ====================
//
//    @Test
//    void createGroupMembersProfile_shouldAddMembersToGroup() {
//        // Given
//        long groupId = 1006L;
//        List<UserProfile> members = Arrays.asList(
//                new UserProfile(2010L, "user10", "User Ten", "http://avatar10.png"),
//                new UserProfile(2011L, "user11", "User Eleven", "http://avatar11.png"));
//        long version = System.currentTimeMillis();
//
//        // When
//        storageFacade.createGroupMembersProfile(groupId, members, version);
//
//        // Then - verify group_members
//        ResultSet membersRs =
//                session.execute(
//                        "SELECT * FROM ripple.group_members WHERE group_id = ?", groupId);
//        List<Row> memberRows = membersRs.all();
//        assertEquals(2, memberRows.size());
//    }
//
//    @Test
//    void createGroupMembersProfile_shouldCreateVersionWithMemberAdditions() {
//        // Given
//        long groupId = 1007L;
//        List<UserProfile> members = Arrays.asList(
//                new UserProfile(2012L, "user12", "User Twelve", "http://avatar12.png"));
//        long version = System.currentTimeMillis();
//
//        // When
//        storageFacade.createGroupMembersProfile(groupId, members, version);
//
//        // Then - verify version record with operation=1 (member add)
//        ResultSet versionRs =
//                session.execute(
//                        "SELECT * FROM ripple.group_members_version WHERE group_id = ?", groupId);
//        Row versionRow = versionRs.one();
//        assertNotNull(versionRow);
//
//        List<UdtValue> changes = versionRow.getList("changes", UdtValue.class);
//        assertEquals(1, changes.size());
//        assertEquals(3, changes.get(0).getByte("operation")); // op=3: member_join
//        assertEquals(2012L, changes.get(0).getLong("user_id"));
//    }
//
//    // ==================== updateGroupName Tests ====================
//
//    @Test
//    void updateGroupName_shouldUpdateMetadataAndCreateVersion() {
//        // Given
//        long groupId = 1008L;
//        String originalName = "Original Name";
//        String newName = "Updated Name";
//        long createVersion = System.currentTimeMillis();
//        long updateVersion = createVersion + 1;
//
//        // Create group first
//        storageFacade.createGroup(groupId, originalName, "avatar.png",
//                Arrays.asList(new UserProfile(2020L, "user20", "User Twenty", "a.png")),
//                createVersion);
//
//        // When
//        storageFacade.updateGroupName(groupId, newName, updateVersion);
//
//        // Then - verify metadata updated
//        ResultSet metadataRs =
//                session.execute(
//                        "SELECT group_name FROM ripple.groups_metadata WHERE group_id = ?",
// groupId);
//        Row row = metadataRs.one();
//        assertEquals(newName, row.getString("group_name"));
//
//        // Then - verify version record with op=5
//        ResultSet versionRs =
//                session.execute(
//                        "SELECT * FROM ripple.group_members_version WHERE group_id = ? AND version
// = ?",
//                        groupId, updateVersion);
//        Row versionRow = versionRs.one();
//        assertNotNull(versionRow);
//
//        List<UdtValue> changes = versionRow.getList("changes", UdtValue.class);
//        assertEquals(1, changes.size());
//        assertEquals(7, changes.get(0).getByte("operation")); // op=7: update_group_name
//        assertEquals(newName, changes.get(0).getString("group_name"));
//    }
//
//    // ==================== updateGroupAvatar Tests ====================
//
//    @Test
//    void updateGroupAvatar_shouldUpdateMetadataAndCreateVersion() {
//        // Given
//        long groupId = 1009L;
//        String originalAvatar = "original.png";
//        String newAvatar = "updated.png";
//        long createVersion = System.currentTimeMillis();
//        long updateVersion = createVersion + 1;
//
//        // Create group first
//        storageFacade.createGroup(groupId, "Test Group", originalAvatar,
//                Arrays.asList(new UserProfile(2021L, "user21", "User TwentyOne", "a.png")),
//                createVersion);
//
//        // When
//        storageFacade.updateGroupAvatar(groupId, newAvatar, updateVersion);
//
//        // Then - verify metadata updated
//        ResultSet metadataRs =
//                session.execute(
//                        "SELECT group_avatar FROM ripple.groups_metadata WHERE group_id = ?",
// groupId);
//        Row row = metadataRs.one();
//        assertEquals(newAvatar, row.getString("group_avatar"));
//
//        // Then - verify version record with op=6
//        ResultSet versionRs =
//                session.execute(
//                        "SELECT * FROM ripple.group_members_version WHERE group_id = ? AND version
// = ?",
//                        groupId, updateVersion);
//        Row versionRow = versionRs.one();
//        assertNotNull(versionRow);
//
//        List<UdtValue> changes = versionRow.getList("changes", UdtValue.class);
//        assertEquals(1, changes.size());
//        assertEquals(8, changes.get(0).getByte("operation")); // op=8: update_group_avatar
//        assertEquals(newAvatar, changes.get(0).getString("group_avatar"));
//    }
//
//    // ==================== updateGroupMemberName Tests ====================
//
//    @Test
//    void updateGroupMemberName_shouldUpdateMemberAndCreateVersion() {
//        // Given
//        long groupId = 1010L;
//        long userId = 2030L;
//        String originalName = "Original Member Name";
//        String newName = "Updated Member Name";
//        long createVersion = System.currentTimeMillis();
//        long updateVersion = createVersion + 1;
//
//        // Create group with member first
//        storageFacade.createGroup(groupId, "Group", "avatar.png",
//                Arrays.asList(new UserProfile(userId, "user30", originalName, "a.png")),
//                createVersion);
//
//        // When
//        storageFacade.updateGroupMemberName(groupId, userId, newName, updateVersion);
//
//        // Then - verify member name updated
//        ResultSet memberRs =
//                session.execute(
//                        "SELECT name FROM ripple.group_members WHERE group_id = ? AND user_id =
// ?",
//                        groupId, userId);
//        Row row = memberRs.one();
//        assertEquals(newName, row.getString("name"));
//
//        // Then - verify version record with op=2
//        ResultSet versionRs =
//                session.execute(
//                        "SELECT * FROM ripple.group_members_version WHERE group_id = ? AND version
// = ?",
//                        groupId, updateVersion);
//        Row versionRow = versionRs.one();
//        assertNotNull(versionRow);
//
//        List<UdtValue> changes = versionRow.getList("changes", UdtValue.class);
//        assertEquals(1, changes.size());
//        assertEquals(5, changes.get(0).getByte("operation")); // op=5: member_update_name
//        assertEquals(userId, changes.get(0).getLong("user_id"));
//        assertEquals(newName, changes.get(0).getString("name"));
//    }
//
//    // ==================== updateGroupMemberAvatar Tests ====================
//
//    @Test
//    void updateGroupMemberAvatar_shouldUpdateMemberAndCreateVersion() {
//        // Given
//        long groupId = 1011L;
//        long userId = 2031L;
//        String originalAvatar = "original_member.png";
//        String newAvatar = "updated_member.png";
//        long createVersion = System.currentTimeMillis();
//        long updateVersion = createVersion + 1;
//
//        // Create group with member first
//        storageFacade.createGroup(groupId, "Group", "avatar.png",
//                Arrays.asList(new UserProfile(userId, "user31", "Name", originalAvatar)),
//                createVersion);
//
//        // When
//        storageFacade.updateGroupMemberAvatar(groupId, userId, newAvatar, updateVersion);
//
//        // Then - verify member avatar updated
//        ResultSet memberRs =
//                session.execute(
//                        "SELECT avatar FROM ripple.group_members WHERE group_id = ? AND user_id =
// ?",
//                        groupId, userId);
//        Row row = memberRs.one();
//        assertEquals(newAvatar, row.getString("avatar"));
//
//        // Then - verify version record with op=4
//        ResultSet versionRs =
//                session.execute(
//                        "SELECT * FROM ripple.group_members_version WHERE group_id = ? AND version
// = ?",
//                        groupId, updateVersion);
//        Row versionRow = versionRs.one();
//        assertNotNull(versionRow);
//
//        List<UdtValue> changes = versionRow.getList("changes", UdtValue.class);
//        assertEquals(1, changes.size());
//        assertEquals(6, changes.get(0).getByte("operation")); // op=6: member_update_avatar
//        assertEquals(userId, changes.get(0).getLong("user_id"));
//        assertEquals(newAvatar, changes.get(0).getString("avatar"));
//    }
//
//    // ==================== removeGroupMember Tests ====================
//
//    @Test
//    void removeGroupMember_shouldDeleteMemberAndCreateVersion() {
//        // Given
//        long groupId = 1012L;
//        long userId = 2032L;
//        long createVersion = System.currentTimeMillis();
//        long removeVersion = createVersion + 1;
//
//        // Create group with member first
//        storageFacade.createGroup(groupId, "Group", "avatar.png",
//                Arrays.asList(new UserProfile(userId, "user32", "Name", "avatar.png")),
//                createVersion);
//
//        // When
//        storageFacade.removeGroupMember(groupId, userId, removeVersion);
//
//        // Then - verify member deleted
//        ResultSet memberRs =
//                session.execute(
//                        "SELECT * FROM ripple.group_members WHERE group_id = ? AND user_id = ?",
//                        groupId, userId);
//        assertNull(memberRs.one());
//
//        // Then - verify version record with op=3
//        ResultSet versionRs =
//                session.execute(
//                        "SELECT * FROM ripple.group_members_version WHERE group_id = ? AND version
// = ?",
//                        groupId, removeVersion);
//        Row versionRow = versionRs.one();
//        assertNotNull(versionRow);
//
//        List<UdtValue> changes = versionRow.getList("changes", UdtValue.class);
//        assertEquals(1, changes.size());
//        assertEquals(4, changes.get(0).getByte("operation")); // op=4: member_quit
//        assertEquals(userId, changes.get(0).getLong("user_id"));
//    }
//
//    // ==================== getGroupMemberIds Tests ====================
//
//    @Test
//    void getGroupMemberIds_shouldReturnMemberIds() throws NotFoundGroupException {
//        // Given
//        long groupId = 1013L;
//        List<UserProfile> members = Arrays.asList(
//                new UserProfile(2040L, "user40", "User Forty", "a.png"),
//                new UserProfile(2041L, "user41", "User FortyOne", "b.png"));
//        storageFacade.createGroup(groupId, "Test", "avatar.png", members,
// System.currentTimeMillis());
//
//        // When
//        List<Long> memberIds = storageFacade.getGroupMemberIds(groupId);
//
//        // Then
//        assertEquals(2, memberIds.size());
//        assertTrue(memberIds.contains(2040L));
//        assertTrue(memberIds.contains(2041L));
//    }
//
//    @Test
//    void getGroupMemberIds_shouldThrowExceptionWhenGroupNotFound() {
//        // Given
//        long nonExistentGroupId = 9999L;
//
//        // When & Then
//        assertThrows(NotFoundGroupException.class,
//                () -> storageFacade.getGroupMemberIds(nonExistentGroupId));
//    }
//
//    // ==================== getGroupMembersInfo Tests ====================
//
//    @Test
//    void getGroupMembersInfo_shouldReturnMemberInfo() throws NotFoundGroupException {
//        // Given
//        long groupId = 1014L;
//        List<UserProfile> members = Arrays.asList(
//                new UserProfile(2042L, "user42", "User FortyTwo", "avatar42.png"));
//        storageFacade.createGroup(groupId, "Test", "avatar.png", members,
// System.currentTimeMillis());
//
//        // When
//        List<GroupMemberInfo> memberInfos = storageFacade.getGroupMembersInfo(groupId);
//
//        // Then
//        assertEquals(1, memberInfos.size());
//        GroupMemberInfo info = memberInfos.get(0);
//        assertEquals(groupId, info.getGroupId());
//        assertEquals(2042L, info.getUserId());
//        assertEquals("User FortyTwo", info.getName());
//        assertEquals("avatar42.png", info.getAvatar());
//    }
//
//    @Test
//    void getGroupMembersInfo_shouldThrowExceptionWhenGroupNotFound() {
//        // Given
//        long nonExistentGroupId = 9998L;
//
//        // When & Then
//        assertThrows(NotFoundGroupException.class,
//                () -> storageFacade.getGroupMembersInfo(nonExistentGroupId));
//    }
//
//    // ==================== getGroupInfo Tests ====================
//
//    @Test
//    void getGroupInfo_shouldReturnGroupInfo() throws NotFoundGroupException {
//        // Given
//        long groupId = 1015L;
//        String groupName = "Info Test Group";
//        String groupAvatar = "info_avatar.png";
//        List<UserProfile> members = Arrays.asList(
//                new UserProfile(2043L, "user43", "User FortyThree", "a.png"),
//                new UserProfile(2044L, "user44", "User FortyFour", "b.png"));
//        storageFacade.createGroup(groupId, groupName, groupAvatar, members,
// System.currentTimeMillis());
//
//        // When
//        GroupInfo groupInfo = storageFacade.getGroupInfo(groupId);
//
//        // Then
//        assertEquals(groupId, groupInfo.getGroupId());
//        assertEquals(groupName, groupInfo.getGroupName());
//        assertEquals(groupAvatar, groupInfo.getGroupAvatar());
//        assertEquals(2, groupInfo.getMemberIds().size());
//        assertTrue(groupInfo.getMemberIds().contains(2043L));
//        assertTrue(groupInfo.getMemberIds().contains(2044L));
//    }
//
//    @Test
//    void getGroupInfo_shouldThrowExceptionWhenGroupNotFound() {
//        // Given
//        long nonExistentGroupId = 9997L;
//
//        // When & Then
//        assertThrows(NotFoundGroupException.class,
//                () -> storageFacade.getGroupInfo(nonExistentGroupId));
//    }
//
//    // ==================== getUserGroupIds Tests ====================
//
//    @Test
//    void getUserGroupIds_shouldReturnGroupIds() {
//        // Given
//        long userId = 3010L;
//        long version = System.currentTimeMillis();
//        storageFacade.createUserGroupAndConversation(userId, 1016L, "Group1", "a.png", version);
//        storageFacade.createUserGroupAndConversation(userId, 1017L, "Group2", "b.png", version +
// 1);
//
//        // When
//        List<Long> groupIds = storageFacade.getUserGroupIds(userId);
//
//        // Then
//        assertEquals(2, groupIds.size());
//        assertTrue(groupIds.contains(1016L));
//        assertTrue(groupIds.contains(1017L));
//    }
//
//    @Test
//    void getUserGroupIds_shouldReturnEmptyListWhenNoGroups() {
//        // Given
//        long userWithNoGroups = 3011L;
//
//        // When
//        List<Long> groupIds = storageFacade.getUserGroupIds(userWithNoGroups);
//
//        // Then
//        assertTrue(groupIds.isEmpty());
//    }
//
//    // ==================== getLatestGroupVersion Tests ====================
//
//    @Test
//    void getLatestGroupVersion_shouldReturnLatestVersion() {
//        // Given
//        long groupId = 1018L;
//        long version1 = 1000L;
//        long version2 = 2000L;
//
//        storageFacade.createGroup(groupId, "Test", "a.png",
//                Arrays.asList(new UserProfile(2050L, "user50", "User Fifty", "a.png")), version1);
//        storageFacade.updateGroupName(groupId, "Updated", version2);
//
//        // When
//        String latestVersion = storageFacade.getLatestGroupVersion(groupId);
//
//        // Then
//        assertNotNull(latestVersion);
//        assertEquals(String.valueOf(version2), latestVersion);
//    }
//
//    @Test
//    void getLatestGroupVersion_shouldReturnNullWhenNoVersions() {
//        // Given
//        long groupId = 9996L; // non-existent group
//
//        // When
//        String latestVersion = storageFacade.getLatestGroupVersion(groupId);
//
//        // Then
//        assertNull(latestVersion);
//    }
//
//    // ==================== removeUserGroup Tests ====================
//
//    @Test
//    void removeUserGroup_shouldDeleteAndCreateVersion() {
//        // Given
//        long userId = 3012L;
//        long groupId = 1019L;
//        long createVersion = System.currentTimeMillis();
//        long removeVersion = createVersion + 1;
//        storageFacade.createUserGroupAndConversation(userId, groupId, "Group", "a.png",
// createVersion);
//
//        // When
//        storageFacade.removeUserGroup(userId, groupId, removeVersion);
//
//        // Then - verify user_groups entry deleted
//        ResultSet userGroupRs =
//                session.execute(
//                        "SELECT * FROM ripple.user_groups WHERE user_id = ? AND group_id = ?",
//                        userId, groupId);
//        assertNull(userGroupRs.one());
//
//        // Then - verify version record with op=2 (quit)
//        ResultSet versionRs =
//                session.execute(
//                        "SELECT * FROM ripple.user_groups_version WHERE user_id = ?", userId);
//        List<Row> versionRows = versionRs.all();
//        assertEquals(2, versionRows.size()); // join + quit
//
//        Row quitRow = versionRows.get(1);
//        assertEquals((byte) 2, quitRow.getByte("operation")); // op=2: quit
//    }
//
//    // ==================== updateUserGroupName Tests ====================
//
//    @Test
//    void updateUserGroupName_shouldUpdateAndCreateVersion() {
//        // Given
//        long userId = 3013L;
//        long groupId = 1020L;
//        String originalName = "Original";
//        String newName = "Updated";
//        long createVersion = System.currentTimeMillis();
//        long updateVersion = createVersion + 1;
//        storageFacade.createUserGroupAndConversation(userId, groupId, originalName, "a.png",
// createVersion);
//
//        // When
//        storageFacade.updateUserGroupName(userId, groupId, newName, updateVersion);
//
//        // Then - verify user_groups updated
//        ResultSet userGroupRs =
//                session.execute(
//                        "SELECT group_name FROM ripple.user_groups WHERE user_id = ? AND group_id
// = ?",
//                        userId, groupId);
//        Row row = userGroupRs.one();
//        assertEquals(newName, row.getString("group_name"));
//
//        // Then - verify version record with op=3 (update_group_name)
//        ResultSet versionRs =
//                session.execute(
//                        "SELECT * FROM ripple.user_groups_version WHERE user_id = ?", userId);
//        List<Row> versionRows = versionRs.all();
//        assertEquals(2, versionRows.size()); // join + update_name
//
//        Row updateRow = versionRows.get(1);
//        assertEquals((byte) 3, updateRow.getByte("operation")); // op=3: update_group_name
//        assertEquals(newName, updateRow.getString("group_name"));
//    }
//
//    // ==================== updateUserGroupAvatar Tests ====================
//
//    @Test
//    void updateUserGroupAvatar_shouldUpdateAndCreateVersion() {
//        // Given
//        long userId = 3014L;
//        long groupId = 1021L;
//        String originalAvatar = "original.png";
//        String newAvatar = "updated.png";
//        long createVersion = System.currentTimeMillis();
//        long updateVersion = createVersion + 1;
//        storageFacade.createUserGroupAndConversation(userId, groupId, "Group", originalAvatar,
// createVersion);
//
//        // When
//        storageFacade.updateUserGroupAvatar(userId, groupId, newAvatar, updateVersion);
//
//        // Then - verify user_groups updated
//        ResultSet userGroupRs =
//                session.execute(
//                        "SELECT group_avatar FROM ripple.user_groups WHERE user_id = ? AND
// group_id = ?",
//                        userId, groupId);
//        Row row = userGroupRs.one();
//        assertEquals(newAvatar, row.getString("group_avatar"));
//
//        // Then - verify version record with op=4 (update_group_avatar)
//        ResultSet versionRs =
//                session.execute(
//                        "SELECT * FROM ripple.user_groups_version WHERE user_id = ?", userId);
//        List<Row> versionRows = versionRs.all();
//        assertEquals(2, versionRows.size()); // join + update_avatar
//
//        Row updateRow = versionRows.get(1);
//        assertEquals((byte) 4, updateRow.getByte("operation")); // op=4: update_group_avatar
//        assertEquals(newAvatar, updateRow.getString("group_avatar"));
//    }
//
//    // ==================== getUserGroupChanges Tests ====================
//
//    @Test
//    void getUserGroupChanges_shouldReturnChangesAfterVersion() throws Exception {
//        // Given
//        long userId = 3015L;
//        long version1 = 1000L;
//        long version2 = 2000L;
//        storageFacade.createUserGroupAndConversation(userId, 1022L, "Group1", "a.png", version1);
//
//        // Create another group (to have a change after the first version)
//        storageFacade.createUserGroupAndConversation(userId, 1023L, "Group2", "b.png", version2);
//
//        // When
//        List<UserGroupVersionChange> changes =
//                storageFacade.getUserGroupChanges(userId, String.valueOf(version1), 10);
//
//        // Then
//        assertEquals(1, changes.size());
//        assertEquals(1023L, changes.get(0).getGroupId());
//        assertEquals((byte) 1, changes.get(0).getOperation()); // op=1: join
//    }
//
//    @Test
//    void getUserGroupChanges_shouldThrowExceptionForInvalidVersion() {
//        // Given
//        long userId = 3016L;
//        String invalidVersion = "not-a-number";
//
//        // When & Then
//        assertThrows(InvalidVersionException.class,
//                () -> storageFacade.getUserGroupChanges(userId, invalidVersion, 10));
//    }
//
//    @Test
//    void getUserGroupChanges_shouldReturnEmptyListWhenNoChanges() throws Exception {
//        // Given
//        long userId = 3017L;
//        long version = 1000L;
//        storageFacade.createUserGroupAndConversation(userId, 1024L, "Group", "a.png", version);
//
//        // When - query with the same version, should return empty since nothing after it
//        List<UserGroupVersionChange> changes =
//                storageFacade.getUserGroupChanges(userId, String.valueOf(version), 10);
//
//        // Then
//        assertTrue(changes.isEmpty());
//    }
//
//    // ==================== getGroupChanges Tests ====================
//
//    @Test
//    void getGroupChanges_shouldReturnChangesAfterVersion() throws Exception {
//        // Given
//        long groupId = 1025L;
//        long version1 = 1000L;
//        long version2 = 2000L;
//
//        storageFacade.createGroup(groupId, "Test", "a.png",
//                Arrays.asList(new UserProfile(2060L, "user60", "User Sixty", "a.png")), version1);
//        storageFacade.updateGroupName(groupId, "Updated Name", version2);
//
//        // When
//        List<GroupVersionChange> changes =
//                storageFacade.getGroupChanges(groupId, String.valueOf(version1), 10);
//
//        // Then
//        assertEquals(1, changes.size());
//        assertEquals(String.valueOf(version2), changes.get(0).getVersion());
//
//        List<ChangeDetail> details = changes.get(0).getChanges();
//        assertEquals(1, details.size());
//        assertEquals((byte) 7, details.get(0).getOperation()); // op=7: update_group_name
//    }
//
//    @Test
//    void getGroupChanges_shouldThrowExceptionForInvalidVersion() {
//        // Given
//        long groupId = 1026L;
//        String invalidVersion = "not-a-number";
//
//        // When & Then
//        assertThrows(InvalidVersionException.class,
//                () -> storageFacade.getGroupChanges(groupId, invalidVersion, 10));
//    }
//
//    @Test
//    void getGroupChanges_shouldReturnAllChangesWithNullVersion() throws Exception {
//        // Given
//        long groupId = 1027L;
//        long version = 1000L;
//
//        storageFacade.createGroup(groupId, "Test", "a.png",
//                Arrays.asList(new UserProfile(2061L, "user61", "User SixtyOne", "a.png")),
// version);
//
//        // When
//        List<GroupVersionChange> changes =
//                storageFacade.getGroupChanges(groupId, null, 10);
//
//        // Then
//        assertEquals(1, changes.size());
//    }
//
//    // ==================== saveGroupCommandMessage Tests ====================
//
//    @Test
//    void saveGroupCommandMessage_shouldSaveCommandMessage() {
//        // Given
//        String conversationId = "group_1028";
//        long messageId = 5001L;
//        long senderId = 2070L;
//        long groupId = 1028L;
//        long timestamp = System.currentTimeMillis();
//        byte commandType = 1; // MEMBER_JOIN
//        String commandData = "{\"userId\": 2070, \"name\": \"User Seventy\"}";
//
//        // When
//        storageFacade.saveGroupCommandMessage(
//                conversationId, messageId, senderId, groupId, timestamp, commandType,
// commandData);
//
//        // Then
//        ResultSet rs =
//                session.execute(
//                        "SELECT * FROM ripple.user_messages WHERE conversation_id = ? AND
// message_id = ?",
//                        conversationId, messageId);
//        Row row = rs.one();
//        assertNotNull(row);
//        assertEquals(senderId, row.getLong("sender_id"));
//        assertEquals(groupId, row.getLong("group_id"));
//        assertEquals(timestamp, row.getLong("send_timestamp"));
//        assertEquals((byte) 3, row.getByte("message_type")); // MESSAGE_TYPE_GROUP_COMMAND
//        assertEquals(commandType, row.getByte("command_type"));
//        assertEquals(commandData, row.getString("command_data"));
//    }
// }
