package com.fanaujie.ripple.database.service;

import com.fanaujie.ripple.database.exception.NotFoundUserProfileException;
import com.fanaujie.ripple.database.model.UserProfile;

import java.util.Optional;

public interface IUserProfileStorage {

    boolean userProfileExists(long userId);

    UserProfile getUserProfile(long userId) throws NotFoundUserProfileException;

    void updateAvatarByUserId(long userId, String avatar) throws NotFoundUserProfileException;

    void updateNickNameByUserId(long userId, String nickName) throws NotFoundUserProfileException;

    void updateStatusByUserId(long userId, byte status) throws NotFoundUserProfileException;

    void insertUserProfile(long userId, int userType, byte status, String nickName, String avatar);
}
