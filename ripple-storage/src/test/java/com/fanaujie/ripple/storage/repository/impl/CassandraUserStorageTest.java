package com.fanaujie.ripple.storage.repository.impl;

import com.datastax.oss.driver.api.core.CqlSession;
import com.fanaujie.ripple.storage.exception.NotFoundUserProfileException;
import com.fanaujie.ripple.storage.model.User;
import com.fanaujie.ripple.storage.model.UserProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class CassandraUserStorageTest {

    @Container
    CassandraContainer cassandraContainer =
            new CassandraContainer("cassandra:5.0.5").withInitScript("ripple.cql");

    private CqlSession session;
    private CassandraUserRepository userStorage;

    @BeforeEach
    void setUp() throws IOException {
        this.session =
                CqlSession.builder()
                        .addContactPoint(cassandraContainer.getContactPoint())
                        .withLocalDatacenter(cassandraContainer.getLocalDatacenter())
                        .build();

        userStorage = new CassandraUserRepository(session);
    }

    @AfterEach
    void tearDown() {
        if (session != null) {
            session.execute("TRUNCATE ripple.user");
            session.execute("TRUNCATE ripple.user_profile");
            session.close();
        }
    }

    @Test
    void testInsertAndFindByAccount() {
        User user = new User(1000L, "testuser", "password123", User.DEFAULT_ROLE_USER, (byte) 0);

        userStorage.insertUser(user, "Test User", "avatar.jpg");

        User foundUser = userStorage.findByAccount("testuser");
        assertNotNull(foundUser);
        assertEquals(1000L, foundUser.getUserId());
        assertEquals("testuser", foundUser.getAccount());
        assertEquals("password123", foundUser.getPassword());
        assertEquals(User.DEFAULT_ROLE_USER, foundUser.getRole());
        assertEquals((byte) 0, foundUser.getStatus());
        assertTrue(foundUser.isEnabled());
    }

    @Test
    void testFindByAccountNotFound() {
        User user = userStorage.findByAccount("nonexistent");
        assertNull(user);
    }

    @Test
    void testUserExists() {
        User user = new User(1001L, "existstest", "password", User.DEFAULT_ROLE_USER, (byte) 0);
        userStorage.insertUser(user, "Exists Test", "avatar.jpg");

        assertTrue(userStorage.userExists("existstest"));
        assertFalse(userStorage.userExists("nonexistent"));
    }

    @Test
    void testInsertUserWithDifferentStatus() {
        User user = new User(1002L, "forbiddenuser", "password", "ROLE_ADMIN", (byte) 1);
        userStorage.insertUser(user, "Forbidden User", "avatar.jpg");

        User foundUser = userStorage.findByAccount("forbiddenuser");
        assertNotNull(foundUser);
        assertEquals(1002L, foundUser.getUserId());
        assertEquals("forbiddenuser", foundUser.getAccount());
        assertEquals("ROLE_ADMIN", foundUser.getRole());
        assertEquals((byte) 1, foundUser.getStatus());
        assertFalse(foundUser.isEnabled());
    }

    @Test
    void testMultipleUsers() {
        User user1 = new User(1003L, "user1", "pass1", User.DEFAULT_ROLE_USER, (byte) 0);
        User user2 = new User(1004L, "user2", "pass2", "ROLE_ADMIN", (byte) 1);

        userStorage.insertUser(user1, "User 1", "avatar1.jpg");
        userStorage.insertUser(user2, "User 2", "avatar2.jpg");

        User found1 = userStorage.findByAccount("user1");
        User found2 = userStorage.findByAccount("user2");

        assertNotNull(found1);
        assertNotNull(found2);
        assertEquals(1003L, found1.getUserId());
        assertEquals(1004L, found2.getUserId());
        assertEquals("user1", found1.getAccount());
        assertEquals("user2", found2.getAccount());
        assertTrue(found1.isEnabled());
        assertFalse(found2.isEnabled());
        assertEquals(User.DEFAULT_ROLE_USER, found1.getRole());
        assertEquals("ROLE_ADMIN", found2.getRole());
    }

    @Test
    void testInsertUserWithNullValues() {
        User user = new User(1005L, "nulltest", "password", null, (byte) 0);
        userStorage.insertUser(user, null, null);

        User foundUser = userStorage.findByAccount("nulltest");
        assertNotNull(foundUser);
        assertEquals("nulltest", foundUser.getAccount());
        assertEquals("password", foundUser.getPassword());
        assertNull(foundUser.getRole());
        assertEquals((byte) 0, foundUser.getStatus());
    }

    // ==================== UserProfile Tests ====================

    @Test
    void testInsertUserCreatesUserProfile() throws NotFoundUserProfileException {
        // Test that insertUser creates both user and user_profile atomically
        User user = new User(2000L, "profiletest", "password", User.DEFAULT_ROLE_USER, (byte) 0);
        userStorage.insertUser(user, "Profile Test User", "test-avatar.jpg");

        // Verify user_profile was created
        UserProfile profile = userStorage.getUserProfile(2000L);
        assertNotNull(profile);
        assertEquals(2000L, profile.getUserId());
        assertEquals("profiletest", profile.getAccount());
        assertEquals("Profile Test User", profile.getNickName());
        assertEquals("test-avatar.jpg", profile.getAvatar());
    }

    @Test
    void testGetUserProfile() throws NotFoundUserProfileException {
        User user = new User(2001L, "getprofile", "password", User.DEFAULT_ROLE_USER, (byte) 0);
        userStorage.insertUser(user, "Get Profile User", "avatar.png");

        UserProfile profile = userStorage.getUserProfile(2001L);
        assertNotNull(profile);
        assertEquals(2001L, profile.getUserId());
        assertEquals("getprofile", profile.getAccount());
        assertEquals("Get Profile User", profile.getNickName());
        assertEquals("avatar.png", profile.getAvatar());
    }

    @Test
    void testGetUserProfileNotFound() {
        assertThrows(NotFoundUserProfileException.class, () -> {
            userStorage.getUserProfile(9999L);
        });
    }

    @Test
    void testUpdateAvatarByUserId() throws NotFoundUserProfileException {
        User user = new User(2002L, "updateavatar", "password", User.DEFAULT_ROLE_USER, (byte) 0);
        userStorage.insertUser(user, "Update Avatar User", "original-avatar.jpg");

        // Update avatar
        userStorage.updateAvatarByUserId(2002L, "new-avatar.png");

        // Verify update
        UserProfile profile = userStorage.getUserProfile(2002L);
        assertEquals("new-avatar.png", profile.getAvatar());
        assertEquals("Update Avatar User", profile.getNickName()); // nickName should remain unchanged
    }

    @Test
    void testUpdateAvatarNotFound() {
        assertThrows(NotFoundUserProfileException.class, () -> {
            userStorage.updateAvatarByUserId(9999L, "new-avatar.jpg");
        });
    }

    @Test
    void testUpdateNickNameByUserId() throws NotFoundUserProfileException {
        User user = new User(2003L, "updatenick", "password", User.DEFAULT_ROLE_USER, (byte) 0);
        userStorage.insertUser(user, "Original Nickname", "avatar.jpg");

        // Update nickname
        userStorage.updateNickNameByUserId(2003L, "New Nickname");

        // Verify update
        UserProfile profile = userStorage.getUserProfile(2003L);
        assertEquals("New Nickname", profile.getNickName());
        assertEquals("avatar.jpg", profile.getAvatar()); // avatar should remain unchanged
    }

    @Test
    void testUpdateNickNameNotFound() {
        assertThrows(NotFoundUserProfileException.class, () -> {
            userStorage.updateNickNameByUserId(9999L, "New Name");
        });
    }

    @Test
    void testUpdateAvatarToNull() throws NotFoundUserProfileException {
        // Test deleting avatar by setting it to null
        User user = new User(2004L, "deleteavatar", "password", User.DEFAULT_ROLE_USER, (byte) 0);
        userStorage.insertUser(user, "Delete Avatar User", "to-be-deleted.jpg");

        // Set avatar to null
        userStorage.updateAvatarByUserId(2004L, null);

        // Verify avatar is null
        UserProfile profile = userStorage.getUserProfile(2004L);
        assertNull(profile.getAvatar());
        assertEquals("Delete Avatar User", profile.getNickName());
    }

    @Test
    void testInsertUserWithNullProfileFields() throws NotFoundUserProfileException {
        // Test inserting user with null nickname and avatar
        User user = new User(2005L, "nullprofile", "password", User.DEFAULT_ROLE_USER, (byte) 0);
        userStorage.insertUser(user, null, null);

        // Verify profile was created with null fields
        UserProfile profile = userStorage.getUserProfile(2005L);
        assertNotNull(profile);
        assertEquals(2005L, profile.getUserId());
        assertEquals("nullprofile", profile.getAccount());
        assertNull(profile.getNickName());
        assertNull(profile.getAvatar());
    }

    @Test
    void testBatchAtomicity() throws NotFoundUserProfileException {
        // Test that insertUser creates both user and user_profile atomically
        // This is already tested in testInsertUserCreatesUserProfile, but let's verify with multiple checks
        User user = new User(2006L, "batchtest", "password", User.DEFAULT_ROLE_USER, (byte) 0);
        userStorage.insertUser(user, "Batch Test", "batch-avatar.jpg");

        // Both user and user_profile should exist
        assertTrue(userStorage.userExists("batchtest"));

        User foundUser = userStorage.findByAccount("batchtest");
        assertNotNull(foundUser);
        assertEquals(2006L, foundUser.getUserId());

        UserProfile profile = userStorage.getUserProfile(2006L);
        assertNotNull(profile);
        assertEquals(2006L, profile.getUserId());
        assertEquals("batchtest", profile.getAccount());
        assertEquals("Batch Test", profile.getNickName());
        assertEquals("batch-avatar.jpg", profile.getAvatar());
    }

    @Test
    void testMultipleUpdatesOnSameProfile() throws NotFoundUserProfileException {
        User user = new User(2007L, "multiupdate", "password", User.DEFAULT_ROLE_USER, (byte) 0);
        userStorage.insertUser(user, "Original Name", "original.jpg");

        // Multiple updates
        userStorage.updateNickNameByUserId(2007L, "First Update");
        userStorage.updateAvatarByUserId(2007L, "first.jpg");
        userStorage.updateNickNameByUserId(2007L, "Second Update");
        userStorage.updateAvatarByUserId(2007L, "second.jpg");

        // Verify final state
        UserProfile profile = userStorage.getUserProfile(2007L);
        assertEquals("Second Update", profile.getNickName());
        assertEquals("second.jpg", profile.getAvatar());
    }
}
