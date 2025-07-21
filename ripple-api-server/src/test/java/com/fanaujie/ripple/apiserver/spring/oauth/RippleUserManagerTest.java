package com.fanaujie.ripple.apiserver.spring.oauth;

import com.fanaujie.ripple.apiserver.spring.mapper.UserMapper;
import com.fanaujie.ripple.apiserver.spring.model.mapper.User;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class RippleUserManagerTest {

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
    private RippleUserManager rippleUserManager;

    @Autowired
    private UserMapper userMapper;

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
    void testCreateUser() {
        User testUser = new User();
        testUser.setUsername("testuser");
        testUser.setPassword("password123");
        testUser.setEnabled(true);
        testUser.setAuthorities(new ArrayList<>());

        rippleUserManager.createUser(testUser);

        assertTrue(rippleUserManager.userExists("testuser"));
        UserDetails loadedUser = rippleUserManager.loadUserByUsername("testuser");
        assertEquals("testuser", loadedUser.getUsername());
        assertEquals("password123", loadedUser.getPassword());
        assertTrue(loadedUser.isEnabled());
    }

    @Test
    void testCreateUserWithDefaultRole() {
        User testUser = new User();
        testUser.setUsername("testuser");
        testUser.setPassword("password123");
        testUser.setEnabled(true);
        testUser.setAuthorities(new ArrayList<>());

        rippleUserManager.createUser(testUser);

        User createdUser = userMapper.findByUsername("testuser");
        assertNotNull(createdUser);
        assertNotEquals(0, createdUser.getId());

        Long defaultRoleId = userMapper.getDefaultRoleId();
        if (defaultRoleId != null) {
            assertTrue(userMapper.hasUserRole(createdUser.getId(), defaultRoleId));
        }
    }

    @Test
    void testUpdateUser() {
        User testUser = new User();
        testUser.setUsername("testuser");
        testUser.setPassword("password123");
        testUser.setEnabled(true);
        testUser.setAuthorities(new ArrayList<>());

        rippleUserManager.createUser(testUser);

        User updatedUser = new User();
        updatedUser.setUsername("testuser");
        updatedUser.setPassword("newpassword");
        updatedUser.setEnabled(false);
        updatedUser.setAuthorities(new ArrayList<>());

        rippleUserManager.updateUser(updatedUser);

        UserDetails loadedUser = rippleUserManager.loadUserByUsername("testuser");
        assertEquals("testuser", loadedUser.getUsername());
        assertEquals("newpassword", loadedUser.getPassword());
        assertFalse(loadedUser.isEnabled());
    }

    @Test
    void testDeleteUser() {
        User testUser = new User();
        testUser.setUsername("testuser");
        testUser.setPassword("password123");
        testUser.setEnabled(true);
        testUser.setAuthorities(new ArrayList<>());

        rippleUserManager.createUser(testUser);
        assertTrue(rippleUserManager.userExists("testuser"));

        rippleUserManager.deleteUser("testuser");
        assertFalse(rippleUserManager.userExists("testuser"));
    }

    @Test
    void testDeleteUserWithRoles() {
        User testUser = new User();
        testUser.setUsername("testuser");
        testUser.setPassword("password123");
        testUser.setEnabled(true);
        testUser.setAuthorities(new ArrayList<>());

        rippleUserManager.createUser(testUser);
        assertTrue(rippleUserManager.userExists("testuser"));

        rippleUserManager.deleteUser("testuser");
        assertFalse(rippleUserManager.userExists("testuser"));

        User deletedUser = userMapper.findByUsername("testuser");
        assertNull(deletedUser);
    }

    @Test
    void testDeleteNonExistentUser() {
        assertFalse(rippleUserManager.userExists("nonexistent"));
        assertDoesNotThrow(() -> rippleUserManager.deleteUser("nonexistent"));
    }

    @Test
    void testUserExists() {
        assertFalse(rippleUserManager.userExists("testuser"));

        User testUser = new User();
        testUser.setUsername("testuser");
        testUser.setPassword("password123");
        testUser.setEnabled(true);
        testUser.setAuthorities(new ArrayList<>());

        rippleUserManager.createUser(testUser);
        assertTrue(rippleUserManager.userExists("testuser"));
    }

    @Test
    void testLoadUserByUsername() {
        User testUser = new User();
        testUser.setUsername("testuser");
        testUser.setPassword("password123");
        testUser.setEnabled(true);
        testUser.setAuthorities(new ArrayList<>());

        rippleUserManager.createUser(testUser);

        UserDetails loadedUser = rippleUserManager.loadUserByUsername("testuser");
        assertNotNull(loadedUser);
        assertEquals("testuser", loadedUser.getUsername());
        assertEquals("password123", loadedUser.getPassword());
        assertTrue(loadedUser.isEnabled());
        assertTrue(loadedUser.isAccountNonExpired());
        assertTrue(loadedUser.isAccountNonLocked());
        assertTrue(loadedUser.isCredentialsNonExpired());
    }

    @Test
    void testLoadUserByUsernameNotFound() {
        assertThrows(UsernameNotFoundException.class, () -> {
            rippleUserManager.loadUserByUsername("nonexistent");
        });
    }

    @Test
    void testChangePasswordWithValidCredentials() {
        User testUser = new User();
        testUser.setUsername("testuser");
        testUser.setPassword("oldpassword");
        testUser.setEnabled(true);
        testUser.setAuthorities(new ArrayList<>());

        rippleUserManager.createUser(testUser);

        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        "testuser", "oldpassword"));

        assertDoesNotThrow(() -> {
            rippleUserManager.changePassword("oldpassword", "newpassword");
        });

        UserDetails updatedUser = rippleUserManager.loadUserByUsername("testuser");
        assertEquals("newpassword", updatedUser.getPassword());
    }

    @Test
    void testChangePasswordWithInvalidOldPassword() {
        User testUser = new User();
        testUser.setUsername("testuser");
        testUser.setPassword("oldpassword");
        testUser.setEnabled(true);
        testUser.setAuthorities(new ArrayList<>());

        rippleUserManager.createUser(testUser);

        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        "testuser", "oldpassword"));

        assertThrows(IllegalArgumentException.class, () -> {
            rippleUserManager.changePassword("wrongpassword", "newpassword");
        });

        UserDetails unchangedUser = rippleUserManager.loadUserByUsername("testuser");
        assertEquals("oldpassword", unchangedUser.getPassword());
    }

    @Test
    void testCreateUserWithDisabledStatus() {
        User testUser = new User();
        testUser.setUsername("disableduser");
        testUser.setPassword("password123");
        testUser.setEnabled(false);
        testUser.setAuthorities(new ArrayList<>());

        rippleUserManager.createUser(testUser);

        UserDetails loadedUser = rippleUserManager.loadUserByUsername("disableduser");
        assertFalse(loadedUser.isEnabled());
    }

    @Test
    void testMultipleUsersOperations() {
        User user1 = new User();
        user1.setUsername("user1");
        user1.setPassword("password1");
        user1.setEnabled(true);
        user1.setAuthorities(new ArrayList<>());

        User user2 = new User();
        user2.setUsername("user2");
        user2.setPassword("password2");
        user2.setEnabled(true);
        user2.setAuthorities(new ArrayList<>());

        rippleUserManager.createUser(user1);
        rippleUserManager.createUser(user2);

        assertTrue(rippleUserManager.userExists("user1"));
        assertTrue(rippleUserManager.userExists("user2"));

        rippleUserManager.deleteUser("user1");
        assertFalse(rippleUserManager.userExists("user1"));
        assertTrue(rippleUserManager.userExists("user2"));
    }
}