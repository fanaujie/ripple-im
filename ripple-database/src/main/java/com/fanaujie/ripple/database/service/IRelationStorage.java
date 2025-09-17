package com.fanaujie.ripple.database.service;

import com.fanaujie.ripple.database.exception.NotFoundRelationException;
import com.fanaujie.ripple.database.model.RelationWithProfile;

import java.util.List;

public interface IRelationStorage {

    void updateFriendDisplayName(long sourceUserId, long targetUserId, String displayName)
            throws NotFoundRelationException;

    int getRelationStatus(long sourceUserId, long targetUserId) throws NotFoundRelationException;

    void insertRelationStatus(
            long sourceUserId, long targetUserId, String targetUserDisplayName, int relationFlags);

    void updateRelationStatus(long sourceUserId, long targetUserId, int relationFlags);

    List<RelationWithProfile> getFriendsWithBlockedUsers(long sourceUserId);
}
