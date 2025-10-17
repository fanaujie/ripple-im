package com.fanaujie.ripple.storage.service;

import com.fanaujie.ripple.protobuf.storage.UserIds;

import java.util.Optional;

public interface RelationStorage {
    Optional<UserIds> getFriendIds(long userId);
}
