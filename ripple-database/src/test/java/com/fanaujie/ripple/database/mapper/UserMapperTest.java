package com.fanaujie.ripple.database.mapper;

import com.fanaujie.ripple.database.config.MyBatisConfig;
import com.fanaujie.ripple.database.model.User;
import com.fanaujie.ripple.database.model.UserProfile;
import com.fanaujie.ripple.database.mapper.UserProfileMapper;
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
class UserMapperTest {

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

    @Autowired private UserMapper userMapper;
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
    void testInsertAndFindByAccount() {
        User user = new User();
        user.setUserId(1000L);
        user.setAccount("testuser");
        user.setPassword("password123");
        user.setRole(User.DEFAULT_ROLE_USER);

        userMapper.insertUser(user);

        // Create corresponding user profile
        UserProfile userProfile = new UserProfile();
        userProfile.setUserId(user.getUserId());
        userProfile.setUserType(0);
        userProfile.setStatus(UserProfile.STATUS_NORMAL);
        userProfile.setNickName("Test User");
        userProfile.setAvatar("avatar.jpg");
        userProfile.setCreatedTime(java.time.Instant.now());
        userProfile.setUpdatedTime(java.time.Instant.now());
        userProfileMapper.insertUserProfile(userProfile);

        User foundUser = userMapper.findByAccount("testuser");
        assertNotNull(foundUser);
        assertEquals("testuser", foundUser.getAccount());
        assertEquals("password123", foundUser.getPassword());
        assertTrue(foundUser.isEnabled());
        assertEquals(User.DEFAULT_ROLE_USER, foundUser.getRole());
        assertNotNull(foundUser.getCreatedTime());
        assertNotNull(foundUser.getUpdatedTime());
    }

    @Test
    void testFindByAccountNotFound() {
        User user = userMapper.findByAccount("nonexistent");
        assertNull(user);
    }

    @Test
    void testUpdateUser() {
        User user = new User();
        user.setUserId(1001L);
        user.setAccount("updatetest");
        user.setPassword("original");
        user.setRole(User.DEFAULT_ROLE_USER);

        userMapper.insertUser(user);

        // Create corresponding user profile
        UserProfile userProfile = new UserProfile();
        userProfile.setUserId(user.getUserId());
        userProfile.setUserType(0);
        userProfile.setStatus(UserProfile.STATUS_NORMAL);
        userProfile.setNickName("Test User");
        userProfile.setAvatar("avatar.jpg");
        userProfile.setCreatedTime(java.time.Instant.now());
        userProfile.setUpdatedTime(java.time.Instant.now());
        userProfileMapper.insertUserProfile(userProfile);

        User updateUser = new User();
        updateUser.setAccount("updatetest");
        updateUser.setPassword("updated");
        updateUser.setRole("ROLE_ADMIN");

        userMapper.updateUser(updateUser);

        // Update user profile status to forbidden
        userProfileMapper.updateStatus(user.getUserId(), UserProfile.STATUS_FORBIDDEN);

        User foundUser = userMapper.findByAccount("updatetest");
        assertNotNull(foundUser);
        assertEquals("updated", foundUser.getPassword());
        assertFalse(foundUser.isEnabled());
        assertEquals("ROLE_ADMIN", foundUser.getRole());
    }

    @Test
    void testChangePassword() {
        String account = "passwordtest";
        String oldPassword = "oldpassword";
        String newPassword = "newpassword";
        User user = new User();
        user.setUserId(1004L);
        user.setAccount(account);
        user.setPassword(oldPassword);
        user.setRole(User.DEFAULT_ROLE_USER);
        UserProfile userProfile = new UserProfile();
        userProfile.setUserId(user.getUserId());

        userProfileMapper.insertUserProfile(userProfile);
        userMapper.insertUser(user);

        userMapper.changePassword(account, newPassword);

        User foundUser = userMapper.findByAccount(account);
        assertNotNull(foundUser);
        assertEquals(newPassword, foundUser.getPassword());
    }

    @Test
    void testFindUserIdByAccount() {
        User user = new User();
        user.setUserId(1007L);
        user.setAccount("useridtest");
        user.setPassword("password123");
        user.setRole(User.DEFAULT_ROLE_USER);

        userMapper.insertUser(user);

        // Create corresponding user profile
        UserProfile userProfile = new UserProfile();
        userProfile.setUserId(user.getUserId());
        userProfile.setUserType(0);
        userProfile.setStatus(UserProfile.STATUS_NORMAL);
        userProfile.setNickName("User ID Test");
        userProfile.setAvatar("avatar.jpg");
        userProfile.setCreatedTime(java.time.Instant.now());
        userProfile.setUpdatedTime(java.time.Instant.now());
        userProfileMapper.insertUserProfile(userProfile);

        Long userId = userMapper.findUserIdByAccount("useridtest");
        assertEquals(1007L, userId.longValue());
    }

    @Test
    void testFindUserIdByAccountNotFound() {
        Long userId = userMapper.findUserIdByAccount("nonexistent");
        assertNull(userId);
    }

    @Test
    void testMultipleUsers() {
        User user1 = new User();
        user1.setUserId(1005L);
        user1.setAccount("user1");
        user1.setPassword("pass1");
        user1.setRole(User.DEFAULT_ROLE_USER);

        User user2 = new User();
        user2.setUserId(1006L);
        user2.setAccount("user2");
        user2.setPassword("pass2");
        user2.setRole("ROLE_ADMIN");

        userMapper.insertUser(user1);
        userMapper.insertUser(user2);

        // Create user profiles
        UserProfile userProfile1 = new UserProfile();
        userProfile1.setUserId(user1.getUserId());
        userProfile1.setUserType(0);
        userProfile1.setStatus(UserProfile.STATUS_NORMAL);
        userProfile1.setNickName("User 1");
        userProfile1.setAvatar("avatar1.jpg");
        userProfile1.setCreatedTime(java.time.Instant.now());
        userProfile1.setUpdatedTime(java.time.Instant.now());
        userProfileMapper.insertUserProfile(userProfile1);

        UserProfile userProfile2 = new UserProfile();
        userProfile2.setUserId(user2.getUserId());
        userProfile2.setUserType(0);
        userProfile2.setStatus(UserProfile.STATUS_FORBIDDEN);
        userProfile2.setNickName("User 2");
        userProfile2.setAvatar("avatar2.jpg");
        userProfile2.setCreatedTime(java.time.Instant.now());
        userProfile2.setUpdatedTime(java.time.Instant.now());
        userProfileMapper.insertUserProfile(userProfile2);

        User found1 = userMapper.findByAccount("user1");
        User found2 = userMapper.findByAccount("user2");

        assertNotNull(found1);
        assertNotNull(found2);
        assertEquals("user1", found1.getAccount());
        assertEquals("user2", found2.getAccount());
        assertTrue(found1.isEnabled());
        assertFalse(found2.isEnabled());
        assertEquals(User.DEFAULT_ROLE_USER, found1.getRole());
        assertEquals("ROLE_ADMIN", found2.getRole());
        assertNotNull(found1.getCreatedTime());
        assertNotNull(found1.getUpdatedTime());
        assertNotNull(found2.getCreatedTime());
        assertNotNull(found2.getUpdatedTime());
    }
}
