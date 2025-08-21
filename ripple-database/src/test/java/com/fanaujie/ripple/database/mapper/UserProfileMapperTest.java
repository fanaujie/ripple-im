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
                    .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

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

    @Test
    void testInsertAndFindUserProfile() {
        UserProfile userProfile = new UserProfile();
        userProfile.setUserId(1);
        userProfile.setUserType(1);
        userProfile.setNickName("Test User");
        userProfile.setAvatar("avatar.jpg");

        userProfileMapper.insertUserProfile(userProfile);
        long insertedId = userProfile.getUserId();
        assertTrue(insertedId > 0);

        UserProfile foundProfile = userProfileMapper.findById(insertedId);
        assertNotNull(foundProfile);
        assertEquals(insertedId, foundProfile.getUserId());
        assertEquals(1, foundProfile.getUserType());
        assertEquals("Test User", foundProfile.getNickName());
        assertEquals("avatar.jpg", foundProfile.getAvatar());
        assertNotNull(foundProfile.getCreatedTime());
        assertNotNull(foundProfile.getUpdatedTime());
    }

    @Test
    void testUpdateUserProfile() {
        UserProfile userProfile = new UserProfile();
        userProfile.setUserId(1);
        userProfile.setUserType(0);
        userProfile.setNickName("Original Name");
        userProfile.setAvatar("original.jpg");

        userProfileMapper.insertUserProfile(userProfile);
        long insertedId = userProfile.getUserId();

        UserProfile updateProfile = new UserProfile();
        updateProfile.setUserId(1);
        updateProfile.setUserId(insertedId);
        updateProfile.setUserType(1);
        updateProfile.setNickName("Updated Name");
        updateProfile.setAvatar("updated.jpg");

        userProfileMapper.updateUserProfile(updateProfile);

        UserProfile foundProfile = userProfileMapper.findById(insertedId);
        assertNotNull(foundProfile);
        assertEquals(1, foundProfile.getUserType());
        assertEquals("Updated Name", foundProfile.getNickName());
        assertEquals("updated.jpg", foundProfile.getAvatar());
    }

    @Test
    void testDeleteUserProfile() {
        UserProfile userProfile = new UserProfile();
        userProfile.setUserId(1);
        userProfile.setUserType(0);

        userProfileMapper.insertUserProfile(userProfile);
        long insertedId = userProfile.getUserId();
        assertTrue(userProfileMapper.countById(insertedId) > 0);

        userProfileMapper.deleteUserProfile(insertedId);
        assertEquals(0, userProfileMapper.countById(insertedId));
    }

    @Test
    void testUpdateNickName() {
        UserProfile userProfile = new UserProfile();
        userProfile.setUserId(1);
        userProfile.setNickName("Original Name");

        userProfileMapper.insertUserProfile(userProfile);
        long insertedId = userProfile.getUserId();

        userProfileMapper.updateNickName(insertedId, "New Nickname");

        UserProfile foundProfile = userProfileMapper.findById(insertedId);
        assertEquals("New Nickname", foundProfile.getNickName());
    }

    @Test
    void testUpdateUserPortrait() {
        UserProfile userProfile = new UserProfile();
        userProfile.setUserId(1);
        userProfile.setAvatar("original.jpg");

        userProfileMapper.insertUserProfile(userProfile);
        long insertedId = userProfile.getUserId();

        userProfileMapper.updateAvatar(insertedId, "new_avatar.jpg");

        UserProfile foundProfile = userProfileMapper.findById(insertedId);
        assertEquals("new_avatar.jpg", foundProfile.getAvatar());
    }
}
