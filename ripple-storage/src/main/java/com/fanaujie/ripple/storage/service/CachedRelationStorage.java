package com.fanaujie.ripple.storage.service;

import com.fanaujie.ripple.protobuf.storage.UserIds;

import java.util.Optional;

public interface CachedRelationStorage {
    Optional<UserIds> getFriendIds(long userId);

    boolean isSenderBlocked(long senderId, long receiverId);
}
