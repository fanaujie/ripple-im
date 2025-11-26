package com.fanaujie.ripple.storage.service;

import com.fanaujie.ripple.protobuf.storage.UserIds;
import com.fanaujie.ripple.storage.exception.NotFoundRelationException;

import java.util.Optional;

public interface CachedRelationStorage {
    Optional<UserIds> getFriendIds(long userId);

    Optional<Byte> getRelationFlags(long senderId, long receiverId);
}
