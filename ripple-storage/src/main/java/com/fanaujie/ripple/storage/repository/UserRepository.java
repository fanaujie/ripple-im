package com.fanaujie.ripple.storage.repository;

import com.fanaujie.ripple.storage.exception.NotFoundUserProfileException;
import com.fanaujie.ripple.storage.model.User;
import com.fanaujie.ripple.storage.model.UserProfile;

public interface UserRepository {

    User findByAccount(String account);

    void insertUser(User user, String displayName, String avatar);

    boolean userExists(String account);

    UserProfile getUserProfile(long userId) throws NotFoundUserProfileException;

    boolean userIdExists(long userId);

    void updateAvatarByUserId(long userId, String avatar) throws NotFoundUserProfileException;

    void updateNickNameByUserId(long userId, String nickName) throws NotFoundUserProfileException;
}
