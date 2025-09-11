package com.fanaujie.ripple.authorization.repository;

import com.fanaujie.ripple.authorization.oauth.RippleUserManager;
import com.fanaujie.ripple.database.model.User;
import com.fanaujie.ripple.database.model.UserProfile;
import com.fanaujie.ripple.database.service.IUserProfileStorage;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class UserRepository {

    private final RippleUserManager userManager;
    private final IUserProfileStorage userProfileStorage;

    public UserRepository(RippleUserManager userManager, IUserProfileStorage userProfileStorage) {
        this.userManager = userManager;
        this.userProfileStorage = userProfileStorage;
    }

    public boolean userExists(String account) {
        return userManager.userExists(account);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void createUser(User user, String nickname, String avatar) {
        userManager.createUser(user);
        this.userProfileStorage.insertUserProfile(
                user.getUserId(),
                0, // Default user type
                UserProfile.STATUS_NORMAL,
                nickname,
                avatar);
    }
}
