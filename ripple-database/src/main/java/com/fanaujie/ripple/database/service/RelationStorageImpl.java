package com.fanaujie.ripple.database.service;

import com.fanaujie.ripple.database.exception.NotFoundRelationException;
import com.fanaujie.ripple.database.mapper.RelationMapper;
import com.fanaujie.ripple.database.model.RelationWithProfile;
import com.fanaujie.ripple.database.model.UserRelation;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RelationStorageImpl implements IRelationStorage {

    private final RelationMapper relationMapper;

    public RelationStorageImpl(RelationMapper relationMapper) {
        this.relationMapper = relationMapper;
    }

    @Override
    public void updateFriendDisplayName(long sourceUserId, long targetUserId, String displayName)
            throws NotFoundRelationException {
        if (0 == relationMapper.updateDisplayName(sourceUserId, targetUserId, displayName)) {
            throw new NotFoundRelationException(
                    "Relation not found between users: " + sourceUserId + " and " + targetUserId);
        }
    }

    @Override
    public int getRelationStatus(long sourceUserId, long targetUserId)
            throws NotFoundRelationException {
        UserRelation relation =
                relationMapper.findRelationBySourceAndTarget(sourceUserId, targetUserId);
        if (relation == null) {
            throw new NotFoundRelationException(
                    "Relation not found between users: " + sourceUserId + " and " + targetUserId);
        }
        return relation.getRelationFlags();
    }

    @Override
    public void insertRelationStatus(
            long sourceUserId, long targetUserId, String targetUserDisplayName, int relationFlags) {
        relationMapper.insertRelation(
                sourceUserId, targetUserId, targetUserDisplayName, relationFlags);
    }

    @Override
    public void updateRelationStatus(long sourceUserId, long targetUserId, int relationFlags) {
        relationMapper.updateRelationFlags(sourceUserId, targetUserId, relationFlags);
    }

    @Override
    public List<RelationWithProfile> getFriendsWithBlockedUsers(long sourceUserId) {
        return relationMapper.findAllRelationsBySourceUserId(sourceUserId);
    }
}
