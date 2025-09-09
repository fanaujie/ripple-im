package com.fanaujie.ripple.database.mapper;

import com.fanaujie.ripple.database.config.MyBatisConfig;
import com.fanaujie.ripple.database.model.RelationWithProfile;
import com.fanaujie.ripple.database.model.UserProfile;
import com.fanaujie.ripple.database.model.UserRelation;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = MyBatisConfig.class,
        properties = {
            "mybatis.mapper-locations=classpath:mapper/*.xml",
            "mybatis.type-aliases-package=com.fanaujie.ripple.database.model"
        })
@Testcontainers
@EnableAutoConfiguration
class RelationMapperTest {

    @Container
    static MySQLContainer<?> mysql =
            new MySQLContainer<>("mysql:8.4.5")
                    .withDatabaseName("test_ripple")
                    .withUsername("test")
                    .withPassword("test")
                    .withUrlParam("useAffectedRows", "true");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired private RelationMapper relationMapper;
    @Autowired private UserProfileMapper userProfileMapper;

    @BeforeEach
    void setUp() {
        Flyway flyway =
                Flyway.configure()
                        .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                        .locations("classpath:db/migration")
                        .load();
        flyway.migrate();
    }

    @AfterEach
    void tearDown() {
        Flyway flyway =
                Flyway.configure()
                        .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                        .locations("classpath:db/migration")
                        .cleanDisabled(false)
                        .load();
        flyway.clean();
    }

    private void createTestUserProfile(long userId, String nickName, String avatar) {
        UserProfile userProfile = new UserProfile();
        userProfile.setUserId(userId);
        userProfile.setUserType(0);
        userProfile.setStatus(UserProfile.STATUS_NORMAL);
        userProfile.setNickName(nickName);
        userProfile.setAvatar(avatar);
        userProfile.setCreatedTime(Instant.now());
        userProfile.setUpdatedTime(Instant.now());
        userProfileMapper.insertUserProfile(userProfile);
    }

    @Test
    void testFindBySourceAndTargetExistingRelation() {
        // Setup test data
        long sourceUserId = 1L;
        long targetUserId = 2L;
        byte relationFlags = UserRelation.FRIEND_FLAG;

        relationMapper.upsertRelationFlags(sourceUserId, targetUserId, relationFlags);

        // Test finding existing relation
        UserRelation result =
                relationMapper.findRelationBySourceAndTarget(sourceUserId, targetUserId);

        assertNotNull(result);
        assertEquals(sourceUserId, result.getSourceUserId());
        assertEquals(targetUserId, result.getTargetUserId());
        assertEquals(relationFlags, result.getRelationFlags());
        assertNotNull(result.getCreatedTime());
        assertNotNull(result.getUpdatedTime());
    }

    @Test
    void testFindBySourceAndTargetNonExistingRelation() {
        // Test finding non-existing relation
        UserRelation result = relationMapper.findRelationBySourceAndTarget(999L, 888L);

        assertNull(result);
    }

    @Test
    void testFindBySourceAndTargetWithDifferentFlags() {
        long sourceUserId = 1L;
        long targetUserId1 = 2L;
        long targetUserId2 = 3L;
        long targetUserId3 = 4L;

        // Create relations with different flags
        relationMapper.upsertRelationFlags(sourceUserId, targetUserId1, UserRelation.FRIEND_FLAG);
        relationMapper.upsertRelationFlags(sourceUserId, targetUserId2, UserRelation.BLOCKED_FLAG);
        relationMapper.upsertRelationFlags(
                sourceUserId,
                targetUserId3,
                (byte) (UserRelation.FRIEND_FLAG | UserRelation.HIDDEN_FLAG));

        // Test each relation
        UserRelation friend =
                relationMapper.findRelationBySourceAndTarget(sourceUserId, targetUserId1);
        assertEquals(UserRelation.FRIEND_FLAG, friend.getRelationFlags());

        UserRelation blocked =
                relationMapper.findRelationBySourceAndTarget(sourceUserId, targetUserId2);
        assertEquals(UserRelation.BLOCKED_FLAG, blocked.getRelationFlags());

        UserRelation friendHidden =
                relationMapper.findRelationBySourceAndTarget(sourceUserId, targetUserId3);
        assertEquals(
                (byte) (UserRelation.FRIEND_FLAG | UserRelation.HIDDEN_FLAG),
                friendHidden.getRelationFlags());
    }

    @Test
    void testUpsertRelationFlagsNewRelation() {
        long sourceUserId = 1L;
        long targetUserId = 2L;
        byte relationFlags = UserRelation.FRIEND_FLAG;

        // Insert new relation
        relationMapper.upsertRelationFlags(sourceUserId, targetUserId, relationFlags);

        // Verify insertion
        UserRelation result =
                relationMapper.findRelationBySourceAndTarget(sourceUserId, targetUserId);
        assertNotNull(result);
        assertEquals(sourceUserId, result.getSourceUserId());
        assertEquals(targetUserId, result.getTargetUserId());
        assertEquals(relationFlags, result.getRelationFlags());
        assertNotNull(result.getCreatedTime());
        assertNotNull(result.getUpdatedTime());
    }

    @Test
    void testUpsertRelationFlagsUpdateExisting() {
        long sourceUserId = 1L;
        long targetUserId = 2L;

        // Insert initial relation
        relationMapper.upsertRelationFlags(sourceUserId, targetUserId, UserRelation.FRIEND_FLAG);
        UserRelation initial =
                relationMapper.findRelationBySourceAndTarget(sourceUserId, targetUserId);

        // Update relation flags
        byte newFlags = (byte) (UserRelation.FRIEND_FLAG | UserRelation.BLOCKED_FLAG);
        relationMapper.upsertRelationFlags(sourceUserId, targetUserId, newFlags);

        // Verify update
        UserRelation updated =
                relationMapper.findRelationBySourceAndTarget(sourceUserId, targetUserId);
        assertNotNull(updated);
        assertEquals(initial.getId(), updated.getId()); // Same record
        assertEquals(newFlags, updated.getRelationFlags());
        assertEquals(initial.getCreatedTime(), updated.getCreatedTime()); // Created time unchanged
        assertTrue(
                updated.getUpdatedTime().isAfter(initial.getUpdatedTime())); // Updated time changed
    }

    @Test
    void testUpsertRelationFlagsCombinations() {
        long sourceUserId = 1L;
        long targetUserId = 2L;

        // Test different flag combinations
        byte[] flagCombinations = {
            UserRelation.FRIEND_FLAG,
            UserRelation.BLOCKED_FLAG,
            UserRelation.HIDDEN_FLAG,
            (byte) (UserRelation.FRIEND_FLAG | UserRelation.HIDDEN_FLAG),
            (byte) (UserRelation.BLOCKED_FLAG | UserRelation.HIDDEN_FLAG),
            (byte) (UserRelation.FRIEND_FLAG | UserRelation.BLOCKED_FLAG | UserRelation.HIDDEN_FLAG)
        };

        for (byte flags : flagCombinations) {
            relationMapper.upsertRelationFlags(sourceUserId, targetUserId, flags);
            UserRelation result =
                    relationMapper.findRelationBySourceAndTarget(sourceUserId, targetUserId);
            assertEquals(flags, result.getRelationFlags());
        }
    }

    @Test
    void testUpdateDisplayNameExistingRelation() {
        long sourceUserId = 1L;
        long targetUserId = 2L;
        String displayName = "Custom Display Name";

        // Create relation first
        relationMapper.upsertRelationFlags(sourceUserId, targetUserId, UserRelation.FRIEND_FLAG);
        UserRelation initial =
                relationMapper.findRelationBySourceAndTarget(sourceUserId, targetUserId);

        // Update display name
        int updatedRows = relationMapper.updateDisplayName(sourceUserId, targetUserId, displayName);
        assertEquals(1, updatedRows);

        // Verify update
        UserRelation updated =
                relationMapper.findRelationBySourceAndTarget(sourceUserId, targetUserId);
        assertNotNull(updated);
        assertEquals(displayName, updated.getTargetUserDisplayName());
        assertTrue(updated.getUpdatedTime().isAfter(initial.getUpdatedTime()));
    }

    @Test
    void testUpdateDisplayNameToNull() {
        long sourceUserId = 1L;
        long targetUserId = 2L;

        // Create relation with initial display name
        relationMapper.upsertRelationFlags(sourceUserId, targetUserId, UserRelation.FRIEND_FLAG);
        relationMapper.updateDisplayName(sourceUserId, targetUserId, "Initial Name");

        // Update to null
        int updatedRows = relationMapper.updateDisplayName(sourceUserId, targetUserId, null);
        assertEquals(1, updatedRows);

        UserRelation result =
                relationMapper.findRelationBySourceAndTarget(sourceUserId, targetUserId);
        assertNull(result.getTargetUserDisplayName());
    }

    @Test
    void testFindAllRelationsBySourceUserIdNoRelations() {
        List<RelationWithProfile> relations = relationMapper.findAllRelationsBySourceUserId(999L);

        assertTrue(relations.isEmpty());
    }

    @Test
    void testFindAllRelationsBySourceUserIdMultipleRelations() {
        long sourceUserId = 1L;
        long targetUserId1 = 2L;
        long targetUserId2 = 3L;
        long targetUserId3 = 4L;

        // Create test user profiles
        createTestUserProfile(targetUserId1, "Target User 1", "avatar1.jpg");
        createTestUserProfile(targetUserId2, "Target User 2", "avatar2.jpg");
        createTestUserProfile(targetUserId3, "Target User 3", "avatar3.jpg");

        // Create relations
        relationMapper.upsertRelationFlags(sourceUserId, targetUserId1, UserRelation.FRIEND_FLAG);
        relationMapper.upsertRelationFlags(sourceUserId, targetUserId2, UserRelation.BLOCKED_FLAG);
        relationMapper.upsertRelationFlags(sourceUserId, targetUserId3, UserRelation.HIDDEN_FLAG);

        // Update display names
        int updatedRows1 =
                relationMapper.updateDisplayName(sourceUserId, targetUserId1, "Friend 1");
        int updatedRows2 =
                relationMapper.updateDisplayName(sourceUserId, targetUserId2, "Blocked User");
        assertEquals(1, updatedRows1);
        assertEquals(1, updatedRows2);

        List<RelationWithProfile> relations =
                relationMapper.findAllRelationsBySourceUserId(sourceUserId);

        assertEquals(3, relations.size());

        // Verify each relation contains both relation and profile data
        for (RelationWithProfile relation : relations) {
            assertEquals(sourceUserId, relation.getSourceUserId());
            assertTrue(relation.getTargetUserId() > 0);
            assertTrue(relation.getRelationFlags() > 0);
            assertNotNull(relation.getCreatedTime());
            assertNotNull(relation.getUpdatedTime());

            // Profile data should be present
            assertNotNull(relation.getTargetNickName());
            assertNotNull(relation.getTargetAvatar());
        }

        // Find specific relations and verify
        RelationWithProfile friend =
                relations.stream()
                        .filter(r -> r.getTargetUserId() == targetUserId1)
                        .findFirst()
                        .orElse(null);
        assertNotNull(friend);
        assertEquals(UserRelation.FRIEND_FLAG, friend.getRelationFlags());
        assertEquals("Friend 1", friend.getTargetUserDisplayName());
        assertEquals("Target User 1", friend.getTargetNickName());
        assertEquals("avatar1.jpg", friend.getTargetAvatar());
    }

    @Test
    void testFindAllRelationsBySourceUserIdWithoutProfiles() {
        long sourceUserId = 1L;
        long targetUserId1 = 2L;
        long targetUserId2 = 3L;

        // Create relations without user profiles
        relationMapper.upsertRelationFlags(sourceUserId, targetUserId1, UserRelation.FRIEND_FLAG);
        relationMapper.upsertRelationFlags(sourceUserId, targetUserId2, UserRelation.BLOCKED_FLAG);

        List<RelationWithProfile> relations =
                relationMapper.findAllRelationsBySourceUserId(sourceUserId);

        assertEquals(2, relations.size());

        // Verify relation data is present but profile data is null due to LEFT JOIN
        for (RelationWithProfile relation : relations) {
            assertEquals(sourceUserId, relation.getSourceUserId());
            assertTrue(relation.getTargetUserId() > 0);
            assertTrue(relation.getRelationFlags() > 0);
            assertNotNull(relation.getCreatedTime());
            assertNotNull(relation.getUpdatedTime());

            // Profile data should be null due to LEFT JOIN with non-existing profiles
            assertNull(relation.getTargetNickName());
            assertNull(relation.getTargetAvatar());
        }
    }

    @Test
    void testFindAllRelationsBySourceUserIdMixedProfiles() {
        long sourceUserId = 1L;
        long targetUserId1 = 2L; // Will have profile
        long targetUserId2 = 3L; // Will not have profile

        // Create profile for only one target user
        createTestUserProfile(targetUserId1, "User With Profile", "profile.jpg");

        // Create relations for both users
        relationMapper.upsertRelationFlags(sourceUserId, targetUserId1, UserRelation.FRIEND_FLAG);
        relationMapper.upsertRelationFlags(sourceUserId, targetUserId2, UserRelation.BLOCKED_FLAG);

        List<RelationWithProfile> relations =
                relationMapper.findAllRelationsBySourceUserId(sourceUserId);

        assertEquals(2, relations.size());

        // Find and verify relation with profile
        RelationWithProfile withProfile =
                relations.stream()
                        .filter(r -> r.getTargetUserId() == targetUserId1)
                        .findFirst()
                        .orElse(null);
        assertNotNull(withProfile);
        assertEquals("User With Profile", withProfile.getTargetNickName());
        assertEquals("profile.jpg", withProfile.getTargetAvatar());

        // Find and verify relation without profile
        RelationWithProfile withoutProfile =
                relations.stream()
                        .filter(r -> r.getTargetUserId() == targetUserId2)
                        .findFirst()
                        .orElse(null);
        assertNotNull(withoutProfile);
        assertNull(withoutProfile.getTargetNickName());
        assertNull(withoutProfile.getTargetAvatar());
    }

    @Test
    void testUpdateDisplayNameNonExistentRelation() {
        long nonExistentSourceUserId = 999L;
        long nonExistentTargetUserId = 888L;

        // Try to update display name for non-existent relation
        int updatedRows =
                relationMapper.updateDisplayName(
                        nonExistentSourceUserId, nonExistentTargetUserId, "Display Name");

        assertEquals(0, updatedRows);
    }
}
