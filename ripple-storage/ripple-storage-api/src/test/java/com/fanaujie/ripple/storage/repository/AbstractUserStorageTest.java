package com.fanaujie.ripple.storage.repository;

import com.fanaujie.ripple.storage.exception.NotFoundUserProfileException;
import com.fanaujie.ripple.storage.model.User;
import com.fanaujie.ripple.storage.model.UserProfile;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public abstract class AbstractUserStorageTest {

    protected abstract RippleStorageFacade getStorageFacade();

    @Test
    void testInsertAndFindByAccount() {
        RippleStorageFacade storageFacade = getStorageFacade();
        User user = new User(1000L, "testuser", "password123", User.DEFAULT_ROLE_USER, (byte) 0);

        storageFacade.insertUser(user, "Test User", "avatar.jpg");

        User foundUser = storageFacade.findByAccount("testuser");
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
        User user = getStorageFacade().findByAccount("nonexistent");
        assertNull(user);
    }

    @Test
    void testUserExists() {
        RippleStorageFacade storageFacade = getStorageFacade();
        User user = new User(1001L, "existstest", "password", User.DEFAULT_ROLE_USER, (byte) 0);
        storageFacade.insertUser(user, "Exists Test", "avatar.jpg");

        assertTrue(storageFacade.userExists("existstest"));
        assertFalse(storageFacade.userExists("nonexistent"));
    }

    @Test
    void testInsertUserWithDifferentStatus() {
        RippleStorageFacade storageFacade = getStorageFacade();
        User user = new User(1002L, "forbiddenuser", "password", "ROLE_ADMIN", (byte) 1);
        storageFacade.insertUser(user, "Forbidden User", "avatar.jpg");

        User foundUser = storageFacade.findByAccount("forbiddenuser");
        assertNotNull(foundUser);
        assertEquals(1002L, foundUser.getUserId());
        assertEquals("forbiddenuser", foundUser.getAccount());
        assertEquals("ROLE_ADMIN", foundUser.getRole());
        assertEquals((byte) 1, foundUser.getStatus());
        assertFalse(foundUser.isEnabled());
    }

    @Test
    void testMultipleUsers() {
        RippleStorageFacade storageFacade = getStorageFacade();
        User user1 = new User(1003L, "user1", "pass1", User.DEFAULT_ROLE_USER, (byte) 0);
        User user2 = new User(1004L, "user2", "pass2", "ROLE_ADMIN", (byte) 1);

        storageFacade.insertUser(user1, "User 1", "avatar1.jpg");
        storageFacade.insertUser(user2, "User 2", "avatar2.jpg");

        User found1 = storageFacade.findByAccount("user1");
        User found2 = storageFacade.findByAccount("user2");

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
        RippleStorageFacade storageFacade = getStorageFacade();
        User user = new User(1005L, "nulltest", "password", null, (byte) 0);
        storageFacade.insertUser(user, null, null);

        User foundUser = storageFacade.findByAccount("nulltest");
        assertNotNull(foundUser);
        assertEquals("nulltest", foundUser.getAccount());
        assertEquals("password", foundUser.getPassword());
        assertNull(foundUser.getRole());
        assertEquals((byte) 0, foundUser.getStatus());
    }

    // ==================== UserProfile Tests ====================

    @Test
    void testInsertUserCreatesUserProfile() throws NotFoundUserProfileException {
        RippleStorageFacade storageFacade = getStorageFacade();
        User user = new User(2000L, "profiletest", "password", User.DEFAULT_ROLE_USER, (byte) 0);
        storageFacade.insertUser(user, "Profile Test User", "test-avatar.jpg");

        UserProfile profile = storageFacade.getUserProfile(2000L);
        assertNotNull(profile);
        assertEquals(2000L, profile.getUserId());
        assertEquals("profiletest", profile.getAccount());
        assertEquals("Profile Test User", profile.getNickName());
        assertEquals("test-avatar.jpg", profile.getAvatar());
    }

    @Test
    void testGetUserProfile() throws NotFoundUserProfileException {
        RippleStorageFacade storageFacade = getStorageFacade();
        User user = new User(2001L, "getprofile", "password", User.DEFAULT_ROLE_USER, (byte) 0);
        storageFacade.insertUser(user, "Get Profile User", "avatar.png");

        UserProfile profile = storageFacade.getUserProfile(2001L);
        assertNotNull(profile);
        assertEquals(2001L, profile.getUserId());
        assertEquals("getprofile", profile.getAccount());
        assertEquals("Get Profile User", profile.getNickName());
        assertEquals("avatar.png", profile.getAvatar());
    }

    @Test
    void testGetUserProfileNotFound() {
        assertThrows(NotFoundUserProfileException.class, () -> getStorageFacade().getUserProfile(9999L));
    }

    @Test
    void testUpdateAvatarByUserId() throws NotFoundUserProfileException {
        RippleStorageFacade storageFacade = getStorageFacade();
        User user = new User(2002L, "updateavatar", "password", User.DEFAULT_ROLE_USER, (byte) 0);
        storageFacade.insertUser(user, "Update Avatar User", "original-avatar.jpg");

        storageFacade.updateProfileAvatarByUserId(2002L, "new-avatar.png");

        UserProfile profile = storageFacade.getUserProfile(2002L);
        assertEquals("new-avatar.png", profile.getAvatar());
        assertEquals("Update Avatar User", profile.getNickName());
    }

    @Test
    void testUpdateAvatarNotFound() {
        assertThrows(
                NotFoundUserProfileException.class,
                () -> getStorageFacade().updateProfileAvatarByUserId(9999L, "new-avatar.jpg"));
    }

    @Test
    void testUpdateNickNameByUserId() throws NotFoundUserProfileException {
        RippleStorageFacade storageFacade = getStorageFacade();
        User user = new User(2003L, "updatenick", "password", User.DEFAULT_ROLE_USER, (byte) 0);
        storageFacade.insertUser(user, "Original Nickname", "avatar.jpg");

        storageFacade.updateProfileNickNameByUserId(2003L, "New Nickname");

        UserProfile profile = storageFacade.getUserProfile(2003L);
        assertEquals("New Nickname", profile.getNickName());
        assertEquals("avatar.jpg", profile.getAvatar());
    }

    @Test
    void testUpdateNickNameNotFound() {
        assertThrows(
                NotFoundUserProfileException.class,
                () -> getStorageFacade().updateProfileNickNameByUserId(9999L, "New Name"));
    }

    @Test
    void testUpdateAvatarToNull() throws NotFoundUserProfileException {
        RippleStorageFacade storageFacade = getStorageFacade();
        User user = new User(2004L, "deleteavatar", "password", User.DEFAULT_ROLE_USER, (byte) 0);
        storageFacade.insertUser(user, "Delete Avatar User", "to-be-deleted.jpg");

        storageFacade.updateProfileAvatarByUserId(2004L, null);

        UserProfile profile = storageFacade.getUserProfile(2004L);
        assertNull(profile.getAvatar());
        assertEquals("Delete Avatar User", profile.getNickName());
    }

    @Test
    void testInsertUserWithNullProfileFields() throws NotFoundUserProfileException {
        RippleStorageFacade storageFacade = getStorageFacade();
        User user = new User(2005L, "nullprofile", "password", User.DEFAULT_ROLE_USER, (byte) 0);
        storageFacade.insertUser(user, null, null);

        UserProfile profile = storageFacade.getUserProfile(2005L);
        assertNotNull(profile);
        assertEquals(2005L, profile.getUserId());
        assertEquals("nullprofile", profile.getAccount());
        assertNull(profile.getNickName());
        assertNull(profile.getAvatar());
    }

    @Test
    void testBatchAtomicity() throws NotFoundUserProfileException {
        RippleStorageFacade storageFacade = getStorageFacade();
        User user = new User(2006L, "batchtest", "password", User.DEFAULT_ROLE_USER, (byte) 0);
        storageFacade.insertUser(user, "Batch Test", "batch-avatar.jpg");

        assertTrue(storageFacade.userExists("batchtest"));

        User foundUser = storageFacade.findByAccount("batchtest");
        assertNotNull(foundUser);
        assertEquals(2006L, foundUser.getUserId());

        UserProfile profile = storageFacade.getUserProfile(2006L);
        assertNotNull(profile);
        assertEquals(2006L, profile.getUserId());
        assertEquals("batchtest", profile.getAccount());
        assertEquals("Batch Test", profile.getNickName());
        assertEquals("batch-avatar.jpg", profile.getAvatar());
    }

    @Test
    void testMultipleUpdatesOnSameProfile() throws NotFoundUserProfileException {
        RippleStorageFacade storageFacade = getStorageFacade();
        User user = new User(2007L, "multiupdate", "password", User.DEFAULT_ROLE_USER, (byte) 0);
        storageFacade.insertUser(user, "Original Name", "original.jpg");

        storageFacade.updateProfileNickNameByUserId(2007L, "First Update");
        storageFacade.updateProfileAvatarByUserId(2007L, "first.jpg");
        storageFacade.updateProfileNickNameByUserId(2007L, "Second Update");
        storageFacade.updateProfileAvatarByUserId(2007L, "second.jpg");

        UserProfile profile = storageFacade.getUserProfile(2007L);
        assertEquals("Second Update", profile.getNickName());
        assertEquals("second.jpg", profile.getAvatar());
    }
}
