package com.fanaujie.ripple.storage.repository;

import com.fanaujie.ripple.storage.exception.BlockAlreadyExistsException;
import com.fanaujie.ripple.storage.exception.InvalidVersionException;
import com.fanaujie.ripple.storage.exception.NotFoundBlockException;
import com.fanaujie.ripple.storage.exception.NotFoundRelationException;
import com.fanaujie.ripple.storage.exception.RelationAlreadyExistsException;
import com.fanaujie.ripple.storage.model.PagedRelationResult;
import com.fanaujie.ripple.storage.model.RelationVersionRecord;
import com.fanaujie.ripple.storage.model.UserProfile;

import java.util.List;
import java.util.UUID;

public interface RelationRepository {

    PagedRelationResult getRelations(long sourceUserId, String nextPageToken, int pageSize);

    void addFriend(long initiatorId, UserProfile friendProfile)
            throws RelationAlreadyExistsException;

    void removeFriend(long initiatorId, long friendId) throws NotFoundRelationException;

    void updateRelationRemarkName(long sourceUserId, long targetUserId, String remarkName)
            throws NotFoundRelationException;

    boolean isFriends(long userId1, long userId2);

    void addBlock(long userId, long blockedUserId, boolean isFriend, UserProfile blockedUserProfile)
            throws BlockAlreadyExistsException;

    void removeBlock(long userId, long blockedUserId) throws NotFoundBlockException;

    void hideBlock(long userId, long blockedUserId) throws NotFoundBlockException;

    List<Long> getFriendIds(long userId);

    List<RelationVersionRecord> getRelationChanges(long userId, String afterVersion, int limit)
            throws InvalidVersionException;
}
