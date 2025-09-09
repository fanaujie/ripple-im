package com.fanaujie.ripple.database.service;

import com.fanaujie.ripple.database.exception.NotFoundRelationException;
import com.fanaujie.ripple.database.model.RelationWithProfile;

import java.util.List;

public interface IRelationStorage {

    void updateFriendDisplayName(long sourceUserId, long targetUserId, String displayName)
            throws NotFoundRelationException;

    byte getRelationStatus(long sourceUserId, long targetUserId) throws NotFoundRelationException;

    void upsertRelationStatus(long sourceUserId, long targetUserId, byte relationFlags);

    List<RelationWithProfile> getFriendsWithBlockedUsers(long sourceUserId);
}
