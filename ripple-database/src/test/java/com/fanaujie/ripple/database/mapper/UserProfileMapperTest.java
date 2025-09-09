package com.fanaujie.ripple.database.mapper;

import com.fanaujie.ripple.database.config.MyBatisConfig;
import com.fanaujie.ripple.database.model.UserProfile;
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

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = MyBatisConfig.class,
        properties = {
            "mybatis.mapper-locations=classpath:mapper/*.xml",
            "mybatis.type-aliases-package=com.fanaujie.ripple.database.model"
        })
@Testcontainers
@EnableAutoConfiguration
class UserProfileMapperTest {

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

    @Autowired private UserProfileMapper userProfileMapper;
    @Autowired private UserMapper userMapper;

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

    @Test
    void testInsertAndFindUserProfile() {
        UserProfile userProfile = new UserProfile();
        userProfile.setUserId(1);
        userProfile.setUserType(1);
        userProfile.setStatus(UserProfile.STATUS_NORMAL);
        userProfile.setNickName("Test User");
        userProfile.setAvatar("avatar.jpg");
        userProfile.setCreatedTime(java.time.Instant.now());
        userProfile.setUpdatedTime(java.time.Instant.now());

        userProfileMapper.insertUserProfile(userProfile);
        long insertedId = userProfile.getUserId();
        assertTrue(insertedId > 0);

        UserProfile foundProfile = userProfileMapper.findByUserId(insertedId);
        assertNotNull(foundProfile);
        assertEquals(insertedId, foundProfile.getUserId());
        assertEquals(1, foundProfile.getUserType());
        assertEquals(UserProfile.STATUS_NORMAL, foundProfile.getStatus());
        assertEquals("Test User", foundProfile.getNickName());
        assertEquals("avatar.jpg", foundProfile.getAvatar());
        assertNotNull(foundProfile.getCreatedTime());
        assertNotNull(foundProfile.getUpdatedTime());
    }

    @Test
    void testUpdateNickName() {
        UserProfile userProfile = new UserProfile();
        userProfile.setUserId(1);
        userProfile.setUserType(0);
        userProfile.setStatus(UserProfile.STATUS_NORMAL);
        userProfile.setNickName("Original Name");
        userProfile.setCreatedTime(java.time.Instant.now());
        userProfile.setUpdatedTime(java.time.Instant.now());

        userProfileMapper.insertUserProfile(userProfile);
        long insertedId = userProfile.getUserId();

        int updatedRows = userProfileMapper.updateNickName(insertedId, "New Nickname");
        assertEquals(1, updatedRows);

        UserProfile foundProfile = userProfileMapper.findByUserId(insertedId);
        assertEquals("New Nickname", foundProfile.getNickName());
    }

    @Test
    void testUpdateUserPortrait() {
        UserProfile userProfile = new UserProfile();
        userProfile.setUserId(1);
        userProfile.setUserType(0);
        userProfile.setStatus(UserProfile.STATUS_NORMAL);
        userProfile.setAvatar("original.jpg");
        userProfile.setCreatedTime(java.time.Instant.now());
        userProfile.setUpdatedTime(java.time.Instant.now());

        userProfileMapper.insertUserProfile(userProfile);
        long insertedId = userProfile.getUserId();

        int updatedRows = userProfileMapper.updateAvatar(insertedId, "new_avatar.jpg");
        assertEquals(1, updatedRows);

        UserProfile foundProfile = userProfileMapper.findByUserId(insertedId);
        assertEquals("new_avatar.jpg", foundProfile.getAvatar());
    }

    @Test
    void testUpdateStatus() {
        UserProfile userProfile = new UserProfile();
        userProfile.setUserId(1);
        userProfile.setUserType(0);
        userProfile.setStatus(UserProfile.STATUS_NORMAL);
        userProfile.setNickName("Test User");
        userProfile.setCreatedTime(java.time.Instant.now());
        userProfile.setUpdatedTime(java.time.Instant.now());

        userProfileMapper.insertUserProfile(userProfile);
        long insertedId = userProfile.getUserId();

        int updatedRows = userProfileMapper.updateStatus(insertedId, UserProfile.STATUS_FORBIDDEN);
        assertEquals(1, updatedRows);

        UserProfile foundProfile = userProfileMapper.findByUserId(insertedId);
        assertEquals(UserProfile.STATUS_FORBIDDEN, foundProfile.getStatus());
    }

    @Test
    void testUpdateStatusToDeleted() {
        UserProfile userProfile = new UserProfile();
        userProfile.setUserId(1);
        userProfile.setUserType(0);
        userProfile.setStatus(UserProfile.STATUS_NORMAL);
        userProfile.setNickName("Test User");
        userProfile.setCreatedTime(java.time.Instant.now());
        userProfile.setUpdatedTime(java.time.Instant.now());

        userProfileMapper.insertUserProfile(userProfile);
        long insertedId = userProfile.getUserId();

        int updatedRows = userProfileMapper.updateStatus(insertedId, UserProfile.STATUS_DELETED);
        assertEquals(1, updatedRows);

        UserProfile foundProfile = userProfileMapper.findByUserId(insertedId);
        assertEquals(UserProfile.STATUS_DELETED, foundProfile.getStatus());
    }

    @Test
    void testUpdateNonExistentUser() {
        long nonExistentUserId = 999999L;

        int updatedRows = userProfileMapper.updateNickName(nonExistentUserId, "New Name");
        assertEquals(0, updatedRows);

        updatedRows = userProfileMapper.updateAvatar(nonExistentUserId, "new_avatar.jpg");
        assertEquals(0, updatedRows);

        updatedRows = userProfileMapper.updateStatus(nonExistentUserId, UserProfile.STATUS_FORBIDDEN);
        assertEquals(0, updatedRows);
    }
}
