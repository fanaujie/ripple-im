package com.fanaujie.ripple.database.service;

import com.fanaujie.ripple.database.exception.NotFoundUserProfileException;
import com.fanaujie.ripple.database.mapper.UserProfileMapper;
import com.fanaujie.ripple.database.model.UserProfile;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserProfileStorageImpl implements IUserProfileStorage {

    private final UserProfileMapper userProfileMapper;

    public UserProfileStorageImpl(UserProfileMapper userProfileMapper) {
        this.userProfileMapper = userProfileMapper;
    }

    @Override
    public boolean userProfileExists(long userId) {
        return userProfileMapper.countByUserId(userId) == 1;
    }

    @Override
    public UserProfile getUserProfile(long userId) throws NotFoundUserProfileException {
        UserProfile profile = userProfileMapper.findByUserId(userId);
        if (profile == null) {
            throw new NotFoundUserProfileException("User profile not found for userId: " + userId);
        } else {
            return profile;
        }
    }

    @Override
    public void updateAvatarByUserId(long userId, String avatar)
            throws NotFoundUserProfileException {
        if (0 == userProfileMapper.updateAvatar(userId, avatar)) {
            throw new NotFoundUserProfileException("User profile not found for userId: " + userId);
        }
    }

    @Override
    public void updateNickNameByUserId(long userId, String nickName)
            throws NotFoundUserProfileException {
        if (0 == userProfileMapper.updateNickName(userId, nickName)) {
            throw new NotFoundUserProfileException("User profile not found for userId: " + userId);
        }
    }

    @Override
    public void updateStatusByUserId(long userId, byte status) throws NotFoundUserProfileException {
        if (0 == userProfileMapper.updateStatus(userId, status)) {
            throw new NotFoundUserProfileException("User profile not found for userId: " + userId);
        }
    }

    @Override
    public void insertUserProfile(
            long userId, int userType, byte status, String nickName, String avatar) {
        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        profile.setUserType(userType);
        profile.setStatus(status);
        profile.setNickName(nickName);
        profile.setAvatar(avatar);
        userProfileMapper.insertUserProfile(profile);
    }
}
