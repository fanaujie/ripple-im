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
    void testFindRelationBySourceAndTarget_Found() {
        long sourceUserId = 1000L;
        long targetUserId = 2000L;

        createTestUserProfile(sourceUserId, "Source User", "source_avatar.jpg");
        createTestUserProfile(targetUserId, "Target User", "target_avatar.jpg");

        relationMapper.insertRelation(
                sourceUserId, targetUserId, "Friend", UserRelation.FRIEND_FLAG);

        UserRelation relation =
                relationMapper.findRelationBySourceAndTarget(sourceUserId, targetUserId);

        assertNotNull(relation);
        assertEquals(sourceUserId, relation.getSourceUserId());
        assertEquals(targetUserId, relation.getTargetUserId());
        assertEquals("Friend", relation.getTargetUserDisplayName());
        assertEquals(UserRelation.FRIEND_FLAG, relation.getRelationFlags());
        assertNotNull(relation.getCreatedTime());
        assertNotNull(relation.getUpdatedTime());
    }

    @Test
    void testFindRelationBySourceAndTarget_NotFound() {
        UserRelation relation = relationMapper.findRelationBySourceAndTarget(9999L, 8888L);
        assertNull(relation);
    }

    @Test
    void testFindRelationBySourceAndTarget_WithDifferentFlags() {
        long sourceUserId = 1001L;
        long targetUserId = 2001L;

        createTestUserProfile(sourceUserId, "User1", "avatar1.jpg");
        createTestUserProfile(targetUserId, "User2", "avatar2.jpg");

        byte combinedFlags = (byte) (UserRelation.FRIEND_FLAG | UserRelation.BLOCKED_FLAG);
        relationMapper.insertRelation(sourceUserId, targetUserId, "Blocked Friend", combinedFlags);

        UserRelation relation =
                relationMapper.findRelationBySourceAndTarget(sourceUserId, targetUserId);

        assertNotNull(relation);
        assertEquals(combinedFlags, relation.getRelationFlags());
        assertEquals("Blocked Friend", relation.getTargetUserDisplayName());
    }

    @Test
    void testInsertRelation_Success() {
        long sourceUserId = 1002L;
        long targetUserId = 2002L;

        createTestUserProfile(sourceUserId, "Source User", "source.jpg");
        createTestUserProfile(targetUserId, "Target User", "target.jpg");

        relationMapper.insertRelation(
                sourceUserId, targetUserId, "Best Friend", UserRelation.FRIEND_FLAG);

        UserRelation relation =
                relationMapper.findRelationBySourceAndTarget(sourceUserId, targetUserId);

        assertNotNull(relation);
        assertEquals(sourceUserId, relation.getSourceUserId());
        assertEquals(targetUserId, relation.getTargetUserId());
        assertEquals("Best Friend", relation.getTargetUserDisplayName());
        assertEquals(UserRelation.FRIEND_FLAG, relation.getRelationFlags());
    }

    @Test
    void testInsertRelation_WithDifferentFlags() {
        long sourceUserId = 1003L;
        long targetUserId1 = 2003L;
        long targetUserId2 = 2004L;
        long targetUserId3 = 2005L;

        createTestUserProfile(sourceUserId, "Source", "source.jpg");
        createTestUserProfile(targetUserId1, "Friend", "friend.jpg");
        createTestUserProfile(targetUserId2, "Blocked", "blocked.jpg");
        createTestUserProfile(targetUserId3, "Hidden", "hidden.jpg");

        relationMapper.insertRelation(
                sourceUserId, targetUserId1, "Friend", UserRelation.FRIEND_FLAG);
        relationMapper.insertRelation(
                sourceUserId, targetUserId2, "Blocked User", UserRelation.BLOCKED_FLAG);
        relationMapper.insertRelation(
                sourceUserId, targetUserId3, "Hidden User", UserRelation.HIDDEN_FLAG);

        UserRelation friendRelation =
                relationMapper.findRelationBySourceAndTarget(sourceUserId, targetUserId1);
        UserRelation blockedRelation =
                relationMapper.findRelationBySourceAndTarget(sourceUserId, targetUserId2);
        UserRelation hiddenRelation =
                relationMapper.findRelationBySourceAndTarget(sourceUserId, targetUserId3);

        assertEquals(UserRelation.FRIEND_FLAG, friendRelation.getRelationFlags());
        assertEquals(UserRelation.BLOCKED_FLAG, blockedRelation.getRelationFlags());
        assertEquals(UserRelation.HIDDEN_FLAG, hiddenRelation.getRelationFlags());
    }

    @Test
    void testInsertRelation_WithNullDisplayName() {
        long sourceUserId = 1004L;
        long targetUserId = 2006L;

        createTestUserProfile(sourceUserId, "Source", "source.jpg");
        createTestUserProfile(targetUserId, "Target", "target.jpg");

        relationMapper.insertRelation(sourceUserId, targetUserId, null, UserRelation.FRIEND_FLAG);

        UserRelation relation =
                relationMapper.findRelationBySourceAndTarget(sourceUserId, targetUserId);

        assertNotNull(relation);
        assertNull(relation.getTargetUserDisplayName());
        assertEquals(UserRelation.FRIEND_FLAG, relation.getRelationFlags());
    }

    @Test
    void testUpdateRelationFlags_Success() {
        long sourceUserId = 1005L;
        long targetUserId = 2007L;

        createTestUserProfile(sourceUserId, "Source", "source.jpg");
        createTestUserProfile(targetUserId, "Target", "target.jpg");

        relationMapper.insertRelation(
                sourceUserId, targetUserId, "Friend", UserRelation.FRIEND_FLAG);

        byte newFlags = (byte) (UserRelation.FRIEND_FLAG | UserRelation.HIDDEN_FLAG);
        relationMapper.updateRelationFlags(sourceUserId, targetUserId, newFlags);

        UserRelation relation =
                relationMapper.findRelationBySourceAndTarget(sourceUserId, targetUserId);

        assertNotNull(relation);
        assertEquals(newFlags, relation.getRelationFlags());
        assertEquals("Friend", relation.getTargetUserDisplayName());
    }

    @Test
    void testUpdateRelationFlags_RemoveFlags() {
        long sourceUserId = 1006L;
        long targetUserId = 2008L;

        createTestUserProfile(sourceUserId, "Source", "source.jpg");
        createTestUserProfile(targetUserId, "Target", "target.jpg");

        byte initialFlags = (byte) (UserRelation.FRIEND_FLAG | UserRelation.BLOCKED_FLAG);
        relationMapper.insertRelation(sourceUserId, targetUserId, "Blocked Friend", initialFlags);

        relationMapper.updateRelationFlags(sourceUserId, targetUserId, UserRelation.FRIEND_FLAG);

        UserRelation relation =
                relationMapper.findRelationBySourceAndTarget(sourceUserId, targetUserId);

        assertNotNull(relation);
        assertEquals(UserRelation.FRIEND_FLAG, relation.getRelationFlags());
        assertEquals("Blocked Friend", relation.getTargetUserDisplayName());
    }

    @Test
    void testUpdateRelationFlags_SetToZero() {
        long sourceUserId = 1007L;
        long targetUserId = 2009L;

        createTestUserProfile(sourceUserId, "Source", "source.jpg");
        createTestUserProfile(targetUserId, "Target", "target.jpg");

        relationMapper.insertRelation(
                sourceUserId, targetUserId, "Friend", UserRelation.FRIEND_FLAG);

        relationMapper.updateRelationFlags(sourceUserId, targetUserId, (byte) 0);

        UserRelation relation =
                relationMapper.findRelationBySourceAndTarget(sourceUserId, targetUserId);

        assertNotNull(relation);
        assertEquals(0, relation.getRelationFlags());
    }

    @Test
    void testUpdateDisplayName_Success() {
        long sourceUserId = 1008L;
        long targetUserId = 2010L;

        createTestUserProfile(sourceUserId, "Source", "source.jpg");
        createTestUserProfile(targetUserId, "Target", "target.jpg");

        relationMapper.insertRelation(
                sourceUserId, targetUserId, "Old Name", UserRelation.FRIEND_FLAG);

        int result = relationMapper.updateDisplayName(sourceUserId, targetUserId, "New Name");

        assertEquals(1, result);

        UserRelation relation =
                relationMapper.findRelationBySourceAndTarget(sourceUserId, targetUserId);
        assertNotNull(relation);
        assertEquals("New Name", relation.getTargetUserDisplayName());
        assertEquals(UserRelation.FRIEND_FLAG, relation.getRelationFlags());
    }

    @Test
    void testUpdateDisplayName_NotFound() {
        int result = relationMapper.updateDisplayName(9999L, 8888L, "Non-existent");
        assertEquals(0, result);
    }

    @Test
    void testUpdateDisplayName_WithNull() {
        long sourceUserId = 1009L;
        long targetUserId = 2011L;

        createTestUserProfile(sourceUserId, "Source", "source.jpg");
        createTestUserProfile(targetUserId, "Target", "target.jpg");

        relationMapper.insertRelation(
                sourceUserId, targetUserId, "Original Name", UserRelation.FRIEND_FLAG);

        int result = relationMapper.updateDisplayName(sourceUserId, targetUserId, null);

        assertEquals(1, result);

        UserRelation relation =
                relationMapper.findRelationBySourceAndTarget(sourceUserId, targetUserId);
        assertNotNull(relation);
        assertNull(relation.getTargetUserDisplayName());
    }

    @Test
    void testUpdateDisplayName_WithEmptyString() {
        long sourceUserId = 1010L;
        long targetUserId = 2012L;

        createTestUserProfile(sourceUserId, "Source", "source.jpg");
        createTestUserProfile(targetUserId, "Target", "target.jpg");

        relationMapper.insertRelation(
                sourceUserId, targetUserId, "Original Name", UserRelation.FRIEND_FLAG);

        int result = relationMapper.updateDisplayName(sourceUserId, targetUserId, "");

        assertEquals(1, result);

        UserRelation relation =
                relationMapper.findRelationBySourceAndTarget(sourceUserId, targetUserId);
        assertNotNull(relation);
        assertEquals("", relation.getTargetUserDisplayName());
    }

    @Test
    void testFindAllRelationsBySourceUserId_MultipleRelations() {
        long sourceUserId = 1011L;
        long targetUserId1 = 2013L;
        long targetUserId2 = 2014L;
        long targetUserId3 = 2015L;

        createTestUserProfile(sourceUserId, "Source User", "source.jpg");
        createTestUserProfile(targetUserId1, "Friend User", "friend.jpg");
        createTestUserProfile(targetUserId2, "Blocked User", "blocked.jpg");
        createTestUserProfile(targetUserId3, "Hidden User", "hidden.jpg");

        relationMapper.insertRelation(
                sourceUserId, targetUserId1, "My Friend", UserRelation.FRIEND_FLAG);
        relationMapper.insertRelation(
                sourceUserId, targetUserId2, "Blocked", UserRelation.BLOCKED_FLAG);
        relationMapper.insertRelation(
                sourceUserId, targetUserId3, "Hidden", UserRelation.HIDDEN_FLAG);

        List<RelationWithProfile> relations =
                relationMapper.findAllRelationsBySourceUserId(sourceUserId);

        assertNotNull(relations);
        assertEquals(3, relations.size());

        RelationWithProfile relation1 =
                relations.stream()
                        .filter(r -> r.getTargetUserId() == targetUserId1)
                        .findFirst()
                        .orElse(null);
        assertNotNull(relation1);
        assertEquals("My Friend", relation1.getTargetUserDisplayName());
        assertEquals(UserRelation.FRIEND_FLAG, relation1.getRelationFlags());
        assertEquals("Friend User", relation1.getTargetNickName());
        assertEquals("friend.jpg", relation1.getTargetAvatar());

        RelationWithProfile relation2 =
                relations.stream()
                        .filter(r -> r.getTargetUserId() == targetUserId2)
                        .findFirst()
                        .orElse(null);
        assertNotNull(relation2);
        assertEquals("Blocked", relation2.getTargetUserDisplayName());
        assertEquals(UserRelation.BLOCKED_FLAG, relation2.getRelationFlags());
        assertEquals("Blocked User", relation2.getTargetNickName());
        assertEquals("blocked.jpg", relation2.getTargetAvatar());

        RelationWithProfile relation3 =
                relations.stream()
                        .filter(r -> r.getTargetUserId() == targetUserId3)
                        .findFirst()
                        .orElse(null);
        assertNotNull(relation3);
        assertEquals("Hidden", relation3.getTargetUserDisplayName());
        assertEquals(UserRelation.HIDDEN_FLAG, relation3.getRelationFlags());
        assertEquals("Hidden User", relation3.getTargetNickName());
        assertEquals("hidden.jpg", relation3.getTargetAvatar());
    }

    @Test
    void testFindAllRelationsBySourceUserId_NoRelations() {
        List<RelationWithProfile> relations = relationMapper.findAllRelationsBySourceUserId(9999L);

        assertNotNull(relations);
        assertTrue(relations.isEmpty());
    }

    @Test
    void testFindAllRelationsBySourceUserId_SingleRelation() {
        long sourceUserId = 1012L;
        long targetUserId = 2016L;

        createTestUserProfile(sourceUserId, "Source", "source.jpg");
        createTestUserProfile(targetUserId, "Target", "target.jpg");

        relationMapper.insertRelation(
                sourceUserId, targetUserId, "Only Friend", UserRelation.FRIEND_FLAG);

        List<RelationWithProfile> relations =
                relationMapper.findAllRelationsBySourceUserId(sourceUserId);

        assertNotNull(relations);
        assertEquals(1, relations.size());

        RelationWithProfile relation = relations.get(0);
        assertEquals(sourceUserId, relation.getSourceUserId());
        assertEquals(targetUserId, relation.getTargetUserId());
        assertEquals("Only Friend", relation.getTargetUserDisplayName());
        assertEquals(UserRelation.FRIEND_FLAG, relation.getRelationFlags());
        assertEquals("Target", relation.getTargetNickName());
        assertEquals("target.jpg", relation.getTargetAvatar());
    }

    @Test
    void testFindAllRelationsBySourceUserId_VerifyProfileJoin() {
        long sourceUserId = 1013L;
        long targetUserId = 2017L;

        createTestUserProfile(sourceUserId, "Source", "source.jpg");
        createTestUserProfile(targetUserId, "Real Target Name", "real_target.jpg");

        relationMapper.insertRelation(
                sourceUserId, targetUserId, "Custom Display Name", UserRelation.FRIEND_FLAG);

        List<RelationWithProfile> relations =
                relationMapper.findAllRelationsBySourceUserId(sourceUserId);

        assertNotNull(relations);
        assertEquals(1, relations.size());

        RelationWithProfile relation = relations.get(0);
        assertEquals("Custom Display Name", relation.getTargetUserDisplayName());
        assertEquals("Real Target Name", relation.getTargetNickName());
        assertEquals("real_target.jpg", relation.getTargetAvatar());
    }
}
