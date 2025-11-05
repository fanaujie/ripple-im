package com.fanaujie.ripple.storage.repository;

import com.fanaujie.ripple.storage.exception.BlockAlreadyExistsException;
import com.fanaujie.ripple.storage.exception.InvalidVersionException;
import com.fanaujie.ripple.storage.exception.NotFoundBlockException;
import com.fanaujie.ripple.storage.exception.NotFoundRelationException;
import com.fanaujie.ripple.storage.exception.RelationAlreadyExistsException;
import com.fanaujie.ripple.storage.model.PagedRelationResult;
import com.fanaujie.ripple.storage.model.Relation;
import com.fanaujie.ripple.storage.model.RelationVersionChange;
import com.fanaujie.ripple.storage.model.UserProfile;

import java.util.List;

public interface RelationRepository {

    PagedRelationResult getRelations(long sourceUserId, String nextPageToken, int pageSize);

    void addFriend(long initiatorId, UserProfile friendProfile)
            throws RelationAlreadyExistsException;

    void removeFriend(long initiatorId, long friendId) throws NotFoundRelationException;

    void updateFriendNickName(long sourceUserId, long targetUserId, String nickName)
            throws NotFoundRelationException;

    void updateFriendAvatar(long sourceUserId, long targetUserId, String avatar)
            throws NotFoundRelationException;

    void updateFriendInfo(long sourceUserId, long targetUserId, String nickName, String avatar)
            throws NotFoundRelationException;

    void updateFriendRemarkName(long sourceUserId, long targetUserId, String remarkName)
            throws NotFoundRelationException;

    void addBlock(long userId, long blockedUserId) throws BlockAlreadyExistsException;

    void addBlockStranger(long userId, UserProfile stranger) throws BlockAlreadyExistsException;

    void removeBlock(long userId, long blockedUserId) throws NotFoundBlockException;

    void hideBlock(long userId, long blockedUserId) throws NotFoundBlockException;

    List<Long> getFriendIds(long userId);

    List<RelationVersionChange> getRelationChanges(long userId, String afterVersion, int limit)
            throws InvalidVersionException;

    String getLatestRelationVersion(long userId);

    Relation getRelationBetweenUser(long userId, long blockedUserId);
}
