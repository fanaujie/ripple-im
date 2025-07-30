package com.fanaujie.ripple.authorization.oauth;

import com.fanaujie.ripple.authorization.mapper.UserMapper;
import com.fanaujie.ripple.authorization.model.mapper.User;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
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
        testUser.setAccount("testuser");
        testUser.setPassword("password123");
        testUser.setEnabled(true);
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
        testUser.setAccount("testuser");
        testUser.setPassword("password123");
        testUser.setEnabled(true);
        testUser.setRole(User.DEFAULT_ROLE_USER);

        rippleUserManager.createUser(testUser);

        User createdUser = userMapper.findByAccount("testuser");
        assertNotNull(createdUser);
        assertNotEquals(0, createdUser.getId());
        assertEquals(User.DEFAULT_ROLE_USER, createdUser.getRole());
    }

    @Test
    void testUpdateUser() {
        User testUser = new User();
        testUser.setAccount("testuser");
        testUser.setPassword("password123");
        testUser.setEnabled(true);
        testUser.setRole(User.DEFAULT_ROLE_USER);

        rippleUserManager.createUser(testUser);

        User updatedUser = new User();
        updatedUser.setAccount("testuser");
        updatedUser.setPassword("newpassword");
        updatedUser.setEnabled(false);
        updatedUser.setRole(User.DEFAULT_ROLE_USER);

        rippleUserManager.updateUser(updatedUser);

        UserDetails loadedUser = rippleUserManager.loadUserByUsername("testuser");
        assertEquals("testuser", loadedUser.getUsername());
        assertEquals("newpassword", loadedUser.getPassword());
        assertFalse(loadedUser.isEnabled());
    }

    @Test
    void testDeleteUser() {
        User testUser = new User();
        testUser.setAccount("testuser");
        testUser.setPassword("password123");
        testUser.setEnabled(true);
        testUser.setRole(User.DEFAULT_ROLE_USER);

        rippleUserManager.createUser(testUser);
        assertTrue(rippleUserManager.userExists("testuser"));

        rippleUserManager.deleteUser("testuser");
        assertFalse(rippleUserManager.userExists("testuser"));
    }

    @Test
    void testDeleteUserWithRoles() {
        User testUser = new User();
        testUser.setAccount("testuser");
        testUser.setPassword("password123");
        testUser.setEnabled(true);
        testUser.setRole(User.DEFAULT_ROLE_USER);

        rippleUserManager.createUser(testUser);
        assertTrue(rippleUserManager.userExists("testuser"));

        rippleUserManager.deleteUser("testuser");
        assertFalse(rippleUserManager.userExists("testuser"));

        User deletedUser = userMapper.findByAccount("testuser");
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
        testUser.setAccount("testuser");
        testUser.setPassword("password123");
        testUser.setEnabled(true);
        testUser.setRole(User.DEFAULT_ROLE_USER);

        rippleUserManager.createUser(testUser);
        assertTrue(rippleUserManager.userExists("testuser"));
    }

    @Test
    void testLoadUserByUsername() {
        User testUser = new User();
        testUser.setAccount("testuser");
        testUser.setPassword("password123");
        testUser.setEnabled(true);
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
        assertThrows(UsernameNotFoundException.class, () -> {
            rippleUserManager.loadUserByUsername("nonexistent");
        });
    }


    @Test
    void testCreateUserWithDisabledStatus() {
        User testUser = new User();
        testUser.setAccount("disableduser");
        testUser.setPassword("password123");
        testUser.setEnabled(false);
        testUser.setRole(User.DEFAULT_ROLE_USER);

        rippleUserManager.createUser(testUser);

        UserDetails loadedUser = rippleUserManager.loadUserByUsername("disableduser");
        assertFalse(loadedUser.isEnabled());
    }

    @Test
    void testMultipleUsersOperations() {
        User user1 = new User();
        user1.setAccount("user1");
        user1.setPassword("password1");
        user1.setEnabled(true);
        user1.setRole(User.DEFAULT_ROLE_USER);

        User user2 = new User();
        user2.setAccount("user2");
        user2.setPassword("password2");
        user2.setEnabled(true);
        user2.setRole(User.DEFAULT_ROLE_USER);

        rippleUserManager.createUser(user1);
        rippleUserManager.createUser(user2);

        assertTrue(rippleUserManager.userExists("user1"));
        assertTrue(rippleUserManager.userExists("user2"));

        rippleUserManager.deleteUser("user1");
        assertFalse(rippleUserManager.userExists("user1"));
        assertTrue(rippleUserManager.userExists("user2"));
    }

    @Test
    void testUserGetAuthoritiesWithUserRole() {
        User user = new User();
        user.setAccount("testuser");
        user.setPassword("password123");
        user.setEnabled(true);
        user.setRole("USER");

        rippleUserManager.createUser(user);

        UserDetails loadedUser = rippleUserManager.loadUserByUsername("testuser");
        assertNotNull(loadedUser.getAuthorities());
        assertEquals(1, loadedUser.getAuthorities().size());

        GrantedAuthority authority = loadedUser.getAuthorities().iterator().next();
        assertEquals("USER", authority.getAuthority());
    }

    @Test
    void testUserGetAuthoritiesWithAdminRole() {
        User user = new User();
        user.setAccount("adminuser");
        user.setPassword("password123");
        user.setEnabled(true);
        user.setRole("ADMIN");

        rippleUserManager.createUser(user);

        UserDetails loadedUser = rippleUserManager.loadUserByUsername("adminuser");
        assertNotNull(loadedUser.getAuthorities());
        assertEquals(1, loadedUser.getAuthorities().size());

        GrantedAuthority authority = loadedUser.getAuthorities().iterator().next();
        assertEquals("ADMIN", authority.getAuthority());
    }

    @Test
    void testUserGetAuthoritiesWithNullRole() {
        User user = new User();
        user.setAccount("nullroleuser");
        user.setPassword("password123");
        user.setEnabled(true);
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
        user.setRole("MANAGER");

        assertNotNull(user.getAuthorities());
        assertEquals(1, user.getAuthorities().size());

        GrantedAuthority authority = user.getAuthorities().iterator().next();
        assertEquals("MANAGER".toLowerCase(), authority.getAuthority());
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