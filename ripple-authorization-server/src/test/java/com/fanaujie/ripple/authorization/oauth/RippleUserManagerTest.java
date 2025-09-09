package com.fanaujie.ripple.authorization.oauth;

import com.fanaujie.ripple.database.model.User;
import com.fanaujie.ripple.database.model.UserProfile;
import com.fanaujie.ripple.database.service.IUserStorage;
import com.fanaujie.ripple.database.service.IUserProfileStorage;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest()
@Testcontainers
class RippleUserManagerTest {

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

    @Autowired private RippleUserManager rippleUserManager;

    @Autowired private IUserStorage userStorage;

    @Autowired private IUserProfileStorage userProfileStorage;

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
    void testCreateUser() {
        User testUser = new User();
        testUser.setAccount("testuser");
        testUser.setPassword("password123");
        testUser.setUserProfileStatus(0);
        testUser.setRole(User.DEFAULT_ROLE_USER);

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
        testUser.setUserId(1);
        testUser.setAccount("testuser");
        testUser.setPassword("password123");
        testUser.setUserProfileStatus(0);
        testUser.setRole(User.DEFAULT_ROLE_USER);

        rippleUserManager.createUser(testUser);

        User createdUser = userStorage.findByAccount("testuser");
        assertNotNull(createdUser);
        assertNotEquals(0, createdUser.getUserId());
        assertEquals(User.DEFAULT_ROLE_USER, createdUser.getRole());
    }

    @Test
    void testUpdateUser() {
        User testUser = new User();
        testUser.setAccount("testuser");
        testUser.setPassword("password123");
        testUser.setRole(User.DEFAULT_ROLE_USER);
        userProfileStorage.insertUserProfile(
                testUser.getUserId(), 0, UserProfile.STATUS_NORMAL, "Test User", null);
        rippleUserManager.createUser(testUser);

        User updatedUser = new User();
        updatedUser.setAccount("testuser");
        updatedUser.setPassword("newpassword");
        updatedUser.setRole(User.DEFAULT_ROLE_USER);
        assertDoesNotThrow(
                () ->
                        userProfileStorage.updateStatusByUserId(
                                updatedUser.getUserId(), UserProfile.STATUS_FORBIDDEN));
        rippleUserManager.updateUser(updatedUser);

        UserDetails loadedUser = rippleUserManager.loadUserByUsername("testuser");
        assertEquals("testuser", loadedUser.getUsername());
        assertEquals("newpassword", loadedUser.getPassword());
        assertFalse(loadedUser.isEnabled());
    }

    @Test
    void testUserExists() {
        assertFalse(rippleUserManager.userExists("testuser"));

        User testUser = new User();
        testUser.setAccount("testuser");
        testUser.setPassword("password123");
        testUser.setUserProfileStatus(0);
        testUser.setRole(User.DEFAULT_ROLE_USER);

        rippleUserManager.createUser(testUser);
        assertTrue(rippleUserManager.userExists("testuser"));
    }

    @Test
    void testLoadUserByUsername() {
        User testUser = new User();
        testUser.setAccount("testuser");
        testUser.setPassword("password123");
        testUser.setUserProfileStatus(0);
        testUser.setRole(User.DEFAULT_ROLE_USER);

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
        assertThrows(
                UsernameNotFoundException.class,
                () -> {
                    rippleUserManager.loadUserByUsername("nonexistent");
                });
    }

    @Test
    void testCreateUserWithDisabledStatus() {
        String account = "disableduser";
        User testUser = new User();
        testUser.setAccount(account);
        testUser.setPassword("password123");
        testUser.setRole(User.DEFAULT_ROLE_USER);
        rippleUserManager.createUser(testUser);
        userProfileStorage.insertUserProfile(
                testUser.getUserId(), 0, UserProfile.STATUS_FORBIDDEN, "Disabled User", null);
        UserDetails loadedUser = rippleUserManager.loadUserByUsername(account);
        assertFalse(loadedUser.isEnabled());
    }

    @Test
    void testMultipleUsersOperations() {
        User user1 = new User();
        user1.setAccount("user1");
        user1.setUserId(1);
        user1.setPassword("password1");
        user1.setUserProfileStatus(0);
        user1.setRole(User.DEFAULT_ROLE_USER);

        User user2 = new User();
        user2.setAccount("user2");
        user1.setUserId(2);
        user2.setPassword("password2");
        user2.setUserProfileStatus(0);
        user2.setRole(User.DEFAULT_ROLE_USER);

        rippleUserManager.createUser(user1);
        rippleUserManager.createUser(user2);

        assertTrue(rippleUserManager.userExists("user1"));
        assertTrue(rippleUserManager.userExists("user2"));
    }

    @Test
    void testUserGetAuthoritiesWithUserRole() {
        User user = new User();
        user.setAccount("testuser");
        user.setPassword("password123");
        user.setUserProfileStatus(0);
        user.setRole("ROLE_USER");

        rippleUserManager.createUser(user);

        UserDetails loadedUser = rippleUserManager.loadUserByUsername("testuser");
        assertNotNull(loadedUser.getAuthorities());
        assertEquals(1, loadedUser.getAuthorities().size());

        GrantedAuthority authority = loadedUser.getAuthorities().iterator().next();
        assertEquals("ROLE_USER", authority.getAuthority());
    }

    @Test
    void testUserGetAuthoritiesWithAdminRole() {
        User user = new User();
        user.setAccount("adminuser");
        user.setPassword("password123");
        user.setUserProfileStatus(0);
        user.setRole("ROLE_ADMIN");

        rippleUserManager.createUser(user);

        UserDetails loadedUser = rippleUserManager.loadUserByUsername("adminuser");
        assertNotNull(loadedUser.getAuthorities());
        assertEquals(1, loadedUser.getAuthorities().size());

        GrantedAuthority authority = loadedUser.getAuthorities().iterator().next();
        assertEquals("ROLE_ADMIN", authority.getAuthority());
    }

    @Test
    void testUserGetAuthoritiesWithNullRole() {
        User user = new User();
        user.setAccount("nullroleuser");
        user.setPassword("password123");
        user.setUserProfileStatus(0);
        user.setRole(null);

        rippleUserManager.createUser(user);

        UserDetails loadedUser = rippleUserManager.loadUserByUsername("nullroleuser");
        assertNotNull(loadedUser.getAuthorities());
        assertEquals(1, loadedUser.getAuthorities().size());

        GrantedAuthority authority = loadedUser.getAuthorities().iterator().next();
        assertEquals(User.DEFAULT_ROLE_USER, authority.getAuthority());
    }

    @Test
    void testUserGetAuthoritiesDirectly() {
        User user = new User();
        user.setRole("ROLE_MANAGER");

        assertNotNull(user.getAuthorities());
        assertEquals(1, user.getAuthorities().size());

        GrantedAuthority authority = user.getAuthorities().iterator().next();
        assertEquals("ROLE_MANAGER", authority.getAuthority());
    }

    @Test
    void testUserGetAuthoritiesWithDefaultRole() {
        User user = new User();
        user.setRole(null);

        assertNotNull(user.getAuthorities());
        assertEquals(1, user.getAuthorities().size());

        GrantedAuthority authority = user.getAuthorities().iterator().next();
        assertEquals(User.DEFAULT_ROLE_USER, authority.getAuthority());
    }
}
