package com.fanaujie.ripple.storage.service;

import com.fanaujie.ripple.storage.model.UserProfile;

public interface UserProfileStorage {
    UserProfile get(long userId) throws Exception;
}
