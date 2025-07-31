package com.fanaujie.ripple.database.mapper;

import com.fanaujie.ripple.database.model.UserProfile;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class UserProfileMapperTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4.5")
            .withDatabaseName("test_ripple")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private UserProfileMapper userProfileMapper;

    @BeforeEach
    void setUp() {
        Flyway flyway = Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();
    }

    @AfterEach
    void tearDown() {
        Flyway flyway = Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
        flyway.clean();
    }

    @Test
    void testInsertAndFindUserProfile() {
        UserProfile userProfile = new UserProfile();
        userProfile.setAccount("testuser");
        userProfile.setUserType(1);
        userProfile.setNickName("Test User");
        userProfile.setUserPortrait("avatar.jpg");

        userProfileMapper.insertUserProfile(userProfile);

        UserProfile foundProfile = userProfileMapper.findByAccount("testuser");
        assertNotNull(foundProfile);
        assertEquals("testuser", foundProfile.getAccount());
        assertEquals(1, foundProfile.getUserType());
        assertEquals("Test User", foundProfile.getNickName());
        assertEquals("avatar.jpg", foundProfile.getUserPortrait());
        assertNotNull(foundProfile.getCreatedTime());
        assertNotNull(foundProfile.getUpdatedTime());
    }

    @Test
    void testUpdateUserProfile() {
        UserProfile userProfile = new UserProfile();
        userProfile.setAccount("testuser");
        userProfile.setUserType(0);
        userProfile.setNickName("Original Name");
        userProfile.setUserPortrait("original.jpg");

        userProfileMapper.insertUserProfile(userProfile);

        UserProfile updateProfile = new UserProfile();
        updateProfile.setAccount("testuser");
        updateProfile.setUserType(1);
        updateProfile.setNickName("Updated Name");
        updateProfile.setUserPortrait("updated.jpg");

        userProfileMapper.updateUserProfile(updateProfile);

        UserProfile foundProfile = userProfileMapper.findByAccount("testuser");
        assertNotNull(foundProfile);
        assertEquals(1, foundProfile.getUserType());
        assertEquals("Updated Name", foundProfile.getNickName());
        assertEquals("updated.jpg", foundProfile.getUserPortrait());
    }

    @Test
    void testDeleteUserProfile() {
        UserProfile userProfile = new UserProfile();
        userProfile.setAccount("testuser");
        userProfile.setUserType(0);

        userProfileMapper.insertUserProfile(userProfile);
        assertTrue(userProfileMapper.countByAccount("testuser") > 0);

        userProfileMapper.deleteUserProfile("testuser");
        assertEquals(0, userProfileMapper.countByAccount("testuser"));
    }

    @Test
    void testUpdateNickName() {
        UserProfile userProfile = new UserProfile();
        userProfile.setAccount("testuser");
        userProfile.setNickName("Original Name");

        userProfileMapper.insertUserProfile(userProfile);

        userProfileMapper.updateNickName("testuser", "New Nickname");

        UserProfile foundProfile = userProfileMapper.findByAccount("testuser");
        assertEquals("New Nickname", foundProfile.getNickName());
    }

    @Test
    void testUpdateUserPortrait() {
        UserProfile userProfile = new UserProfile();
        userProfile.setAccount("testuser");
        userProfile.setUserPortrait("original.jpg");

        userProfileMapper.insertUserProfile(userProfile);

        userProfileMapper.updateUserPortrait("testuser", "new_avatar.jpg");

        UserProfile foundProfile = userProfileMapper.findByAccount("testuser");
        assertEquals("new_avatar.jpg", foundProfile.getUserPortrait());
    }
}