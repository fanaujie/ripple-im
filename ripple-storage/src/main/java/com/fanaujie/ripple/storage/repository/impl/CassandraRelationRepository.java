package com.fanaujie.ripple.storage.repository.impl;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.*;
import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.fanaujie.ripple.storage.exception.*;
import com.fanaujie.ripple.storage.model.*;
import com.fanaujie.ripple.storage.repository.RelationRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CassandraRelationRepository implements RelationRepository {

    private final CqlSession session;
    private final PreparedStatement getRelationStmt;
    private final PreparedStatement getFullRelationStmt;
    private final PreparedStatement getAllRelationsStmt;
    private final PreparedStatement getRelationsFirstPageStmt;
    private final PreparedStatement getRelationsNextPageStmt;
    private final PreparedStatement insertRelationStmt;
    private final PreparedStatement insertRelationVersionStmt;
    private final PreparedStatement deleteRelationStmt;
    private final PreparedStatement updateRelationRemarkNameStmt;
    private final PreparedStatement updateRelationFlagsStmt;
    private final PreparedStatement updateFriendNickNameStmt;
    private final PreparedStatement updateFriendAvatarStmt;
    private final PreparedStatement updateFriendInfoStmt;
    private final PreparedStatement getRelationChangesStmt;
    private final PreparedStatement getLatestVersionStmt;
    private final PreparedStatement getRelationBetweenUsersStmt;

    public CassandraRelationRepository(CqlSession session) {
        this.session = session;
        this.getRelationStmt =
                session.prepare(
                        "SELECT relation_flags FROM ripple.user_relations WHERE user_id = ? AND relation_user_id = ?");
        this.getFullRelationStmt =
                session.prepare(
                        "SELECT nick_name, avatar, remark_name, relation_flags FROM ripple.user_relations WHERE user_id = ? AND relation_user_id = ?");
        this.getAllRelationsStmt =
                session.prepare(
                        "SELECT relation_user_id, relation_flags FROM ripple.user_relations WHERE user_id = ?");
        this.getRelationsFirstPageStmt =
                session.prepare(
                        "SELECT relation_user_id, nick_name, avatar, remark_name, relation_flags FROM ripple.user_relations WHERE user_id = ? LIMIT ?");
        this.getRelationsNextPageStmt =
                session.prepare(
                        "SELECT relation_user_id, nick_name, avatar, remark_name, relation_flags FROM ripple.user_relations WHERE user_id = ? AND relation_user_id > ? LIMIT ?");
        this.insertRelationStmt =
                session.prepare(
                        "INSERT INTO ripple.user_relations (user_id, relation_user_id, nick_name, avatar, remark_name, relation_flags) "
                                + "VALUES (?, ?, ?, ?, ?, ?)");
        this.insertRelationVersionStmt =
                session.prepare(
                        "INSERT INTO ripple.user_relation_version (user_id, version, relation_user_id, operation, nick_name, avatar, remark_name, relation_flags) "
                                + "VALUES (?, now(), ?, ?, ?, ?, ?, ?)");
        this.deleteRelationStmt =
                session.prepare(
                        "DELETE FROM ripple.user_relations WHERE user_id = ? AND relation_user_id = ?");
        this.updateRelationRemarkNameStmt =
                session.prepare(
                        "UPDATE ripple.user_relations SET remark_name = ? WHERE user_id = ? AND relation_user_id = ?");
        this.updateRelationFlagsStmt =
                session.prepare(
                        "UPDATE ripple.user_relations SET relation_flags = ? WHERE user_id = ? AND relation_user_id = ?");
        this.updateFriendNickNameStmt =
                session.prepare(
                        "UPDATE ripple.user_relations SET nick_name = ? WHERE user_id = ? AND relation_user_id = ?");
        this.updateFriendAvatarStmt =
                session.prepare(
                        "UPDATE ripple.user_relations SET avatar = ? WHERE user_id = ? AND relation_user_id = ?");
        this.updateFriendInfoStmt =
                session.prepare(
                        "UPDATE ripple.user_relations SET nick_name = ?, avatar = ? WHERE user_id = ? AND relation_user_id = ?");
        this.getRelationChangesStmt =
                session.prepare(
                        "SELECT version,relation_user_id, operation, nick_name, avatar, remark_name, relation_flags, version FROM ripple.user_relation_version WHERE user_id = ? AND version > ? LIMIT ?");
        this.getLatestVersionStmt =
                session.prepare(
                        "SELECT version FROM ripple.user_relation_version WHERE user_id = ? ORDER BY version DESC LIMIT 1");
        this.getRelationBetweenUsersStmt =
                session.prepare(
                        "SELECT user_id,relation_user_id,nick_name,avatar,remark_name, relation_flags FROM ripple.user_relations WHERE user_id = ? and relation_user_id = ? ");
    }

    @Override
    public PagedRelationResult getRelations(long sourceUserId, String nextPageToken, int pageSize) {
        // Query pageSize + 1 to check if there are more records
        int limit = pageSize + 1;
        ResultSet resultSet;

        if (nextPageToken == null || nextPageToken.isEmpty()) {
            // First page
            resultSet = session.execute(getRelationsFirstPageStmt.bind(sourceUserId, limit));
        } else {
            // Next page, parse token as last relation_user_id
            long lastRelationUserId = Long.parseLong(nextPageToken);
            resultSet =
                    session.execute(
                            getRelationsNextPageStmt.bind(sourceUserId, lastRelationUserId, limit));
        }

        // Collect relations
        List<Relation> relations = new ArrayList<>();
        for (Row row : resultSet) {
            Relation relation = new Relation();
            relation.setSourceUserId(sourceUserId);
            relation.setRelationUserId(row.getLong("relation_user_id"));
            relation.setRelationNickName(row.getString("nick_name"));
            relation.setRelationAvatar(row.getString("avatar"));
            relation.setRelationRemarkName(row.getString("remark_name"));
            relation.setRelationFlags(row.getByte("relation_flags"));
            relations.add(relation);
        }

        // Check if there are more records
        boolean hasMore = relations.size() > pageSize;
        String nextToken = null;

        if (hasMore) {
            // Remove the extra record and set next token
            relations.remove(relations.size() - 1);
            nextToken = String.valueOf(relations.get(relations.size() - 1).getRelationUserId());
        }

        return new PagedRelationResult(relations, nextToken, hasMore);
    }

    @Override
    public void addFriend(long initiatorId, UserProfile friendProfile)
            throws RelationAlreadyExistsException {
        // Check if relation already exists
        Row initiatorRelation =
                session.execute(getRelationStmt.bind(initiatorId, friendProfile.getUserId())).one();

        if (initiatorRelation != null) {
            byte currentFlags = initiatorRelation.getByte("relation_flags");

            // If already a friend, throw exception
            if (RelationFlags.FRIEND.isSet(currentFlags)) {
                throw new RelationAlreadyExistsException(
                        "Friend relation already exists between "
                                + initiatorId
                                + " and "
                                + friendProfile.getUserId());
            }

            // If blocked and hidden, reset flags to FRIEND (unblock and unhide)
            if (RelationFlags.BLOCKED.isSet(currentFlags)
                    && RelationFlags.HIDDEN.isSet(currentFlags)) {
                BatchStatement batch =
                        new BatchStatementBuilder(DefaultBatchType.LOGGED)
                                .addStatement(
                                        updateRelationFlagsStmt.bind(
                                                RelationFlags.FRIEND.getValue(),
                                                initiatorId,
                                                friendProfile.getUserId()))
                                .addStatement(
                                        insertRelationVersionStmt.bind(
                                                initiatorId,
                                                friendProfile.getUserId(),
                                                RelationOperation.ADD_FRIEND.getValue(),
                                                friendProfile.getNickName(),
                                                friendProfile.getAvatar(),
                                                friendProfile.getNickName(),
                                                RelationFlags.FRIEND.getValue()))
                                .build();
                session.execute(batch);
                return;
            }
        }

        // Insert new relation
        BatchStatement batch =
                new BatchStatementBuilder(DefaultBatchType.LOGGED)
                        .addStatement(
                                insertRelationStmt.bind(
                                        initiatorId,
                                        friendProfile.getUserId(),
                                        friendProfile.getNickName(),
                                        friendProfile.getAvatar(),
                                        friendProfile.getNickName(),
                                        RelationFlags.FRIEND.getValue()))
                        .addStatement(
                                insertRelationVersionStmt.bind(
                                        initiatorId,
                                        friendProfile.getUserId(),
                                        RelationOperation.ADD_FRIEND.getValue(),
                                        friendProfile.getNickName(),
                                        friendProfile.getAvatar(),
                                        friendProfile.getNickName(),
                                        RelationFlags.FRIEND.getValue()))
                        .build();
        session.execute(batch);
    }

    @Override
    public void removeFriend(long initiatorId, long friendId) throws NotFoundRelationException {
        // Check if friend relation exists
        Row relation = session.execute(getFullRelationStmt.bind(initiatorId, friendId)).one();

        if (relation == null || !RelationFlags.FRIEND.isSet(relation.getByte("relation_flags"))) {
            throw new NotFoundRelationException(
                    "Friend relation not found between " + initiatorId + " and " + friendId);
        }

        // Delete relation and record version using batch
        BatchStatement batch =
                new BatchStatementBuilder(DefaultBatchType.LOGGED)
                        .addStatement(deleteRelationStmt.bind(initiatorId, friendId))
                        .addStatement(
                                insertRelationVersionStmt.bind(
                                        initiatorId,
                                        friendId,
                                        RelationOperation.DELETE_FRIEND.getValue()))
                        .build();
        session.execute(batch);
    }

    @Override
    public void updateFriendNickName(long sourceUserId, long targetUserId, String nickName)
            throws NotFoundRelationException {
        Row relation = session.execute(getFullRelationStmt.bind(sourceUserId, targetUserId)).one();

        if (relation == null) {
            throw new NotFoundRelationException(
                    "Relation not found between " + sourceUserId + " and " + targetUserId);
        }
        BatchStatement batch =
                new BatchStatementBuilder(DefaultBatchType.LOGGED)
                        .addStatement(
                                updateFriendNickNameStmt.bind(nickName, sourceUserId, targetUserId))
                        .addStatement(
                                insertRelationVersionStmt.bind(
                                        sourceUserId,
                                        targetUserId,
                                        RelationOperation.UPDATE_FRIEND_NICK_NAME.getValue(),
                                        nickName,
                                        null,
                                        null,
                                        null))
                        .build();
        session.execute(batch);
    }

    @Override
    public void updateFriendAvatar(long sourceUserId, long targetUserId, String avatar)
            throws NotFoundRelationException {
        Row relation = session.execute(getFullRelationStmt.bind(sourceUserId, targetUserId)).one();
        if (relation == null) {
            throw new NotFoundRelationException(
                    "Relation not found between " + sourceUserId + " and " + targetUserId);
        }
        BatchStatement batch =
                new BatchStatementBuilder(DefaultBatchType.LOGGED)
                        .addStatement(
                                updateFriendAvatarStmt.bind(avatar, sourceUserId, targetUserId))
                        .addStatement(
                                insertRelationVersionStmt.bind(
                                        sourceUserId,
                                        targetUserId,
                                        RelationOperation.UPDATE_FRIEND_AVATAR.getValue(),
                                        null,
                                        avatar,
                                        null,
                                        null))
                        .build();
        session.execute(batch);
    }

    @Override
    public void updateFriendInfo(long sourceUserId, long targetUserId, String nickName, String avatar)
            throws NotFoundRelationException {
        Row relation = session.execute(getFullRelationStmt.bind(sourceUserId, targetUserId)).one();
        if (relation == null) {
            throw new NotFoundRelationException(
                    "Relation not found between " + sourceUserId + " and " + targetUserId);
        }
        BatchStatement batch =
                new BatchStatementBuilder(DefaultBatchType.LOGGED)
                        .addStatement(
                                updateFriendInfoStmt.bind(nickName, avatar, sourceUserId, targetUserId))
                        .addStatement(
                                insertRelationVersionStmt.bind(
                                        sourceUserId,
                                        targetUserId,
                                        RelationOperation.UPDATE_FRIEND_INFO.getValue(),
                                        nickName,
                                        avatar,
                                        null,
                                        null))
                        .build();
        session.execute(batch);
    }

    @Override
    public void updateFriendRemarkName(long sourceUserId, long targetUserId, String remarkName)
            throws NotFoundRelationException {
        Row relation = session.execute(getFullRelationStmt.bind(sourceUserId, targetUserId)).one();
        if (relation == null) {
            throw new NotFoundRelationException(
                    "Relation not found between " + sourceUserId + " and " + targetUserId);
        }
        BatchStatement batch =
                new BatchStatementBuilder(DefaultBatchType.LOGGED)
                        .addStatement(
                                updateRelationRemarkNameStmt.bind(
                                        remarkName, sourceUserId, targetUserId))
                        .addStatement(
                                insertRelationVersionStmt.bind(
                                        sourceUserId,
                                        targetUserId,
                                        RelationOperation.UPDATE_FRIEND_REMARK_NAME.getValue(),
                                        null,
                                        null,
                                        remarkName,
                                        null))
                        .build();
        session.execute(batch);
    }

    @Override
    public void addBlock(long userId, long blockedUserId) throws BlockAlreadyExistsException {
        Relation betweenUserRelation = this.getRelationBetweenUser(userId, blockedUserId);
        if (betweenUserRelation != null) {
            byte currentFlags = betweenUserRelation.getRelationFlags();
            if (RelationFlags.BLOCKED.isSet(currentFlags)) {
                throw new BlockAlreadyExistsException(
                        "Block already exists between " + userId + " and " + blockedUserId);
            }
            // Relation exists (is friend), update relation_flags to set BLOCKED bit
            byte newFlags = RelationFlags.setFlag(currentFlags, RelationFlags.BLOCKED);
            if (RelationFlags.FRIEND.isSet(newFlags)) {
                BatchStatement batch =
                        new BatchStatementBuilder(DefaultBatchType.LOGGED)
                                .addStatement(
                                        updateRelationFlagsStmt.bind(
                                                newFlags, userId, blockedUserId))
                                .addStatement(
                                        insertRelationVersionStmt.bind(
                                                userId,
                                                blockedUserId,
                                                RelationOperation.ADD_BLOCK.getValue(),
                                                null,
                                                null,
                                                null,
                                                newFlags))
                                .build();
                session.execute(batch);
                return;
            }
            // unexpected case, but handle it anyway
            throw new IllegalArgumentException(
                    "Unexpected relation flags state when adding block between "
                            + userId
                            + " and "
                            + blockedUserId);
        } else {
            // Relation does not exist, insert new relation with BLOCKED flag
            BatchStatement batch =
                    new BatchStatementBuilder(DefaultBatchType.LOGGED)
                            .addStatement(
                                    insertRelationStmt.bind(
                                            userId,
                                            blockedUserId,
                                            null,
                                            null,
                                            null,
                                            RelationFlags.BLOCKED.getValue()))
                            .addStatement(
                                    insertRelationVersionStmt.bind(
                                            userId,
                                            blockedUserId,
                                            RelationOperation.ADD_BLOCK.getValue(),
                                            null,
                                            null,
                                            null,
                                            RelationFlags.BLOCKED.getValue()))
                            .build();
            session.execute(batch);
        }
    }

    @Override
    public void addBlockStranger(long userId, UserProfile stranger)
            throws StrangerHasRelationshipException {
        Relation betweenUserRelation = this.getRelationBetweenUser(userId, stranger.getUserId());
        if (betweenUserRelation != null) {
            throw new StrangerHasRelationshipException(
                    "Stranger "
                            + stranger.getUserId()
                            + " already has a relationship with user "
                            + userId);
        } else {
            // Relation does not exist, insert new relation with BLOCKED flag
            BatchStatement batch =
                    new BatchStatementBuilder(DefaultBatchType.LOGGED)
                            .addStatement(
                                    insertRelationStmt.bind(
                                            userId,
                                            stranger.getUserId(),
                                            stranger.getNickName(),
                                            stranger.getAvatar(),
                                            stranger.getNickName(),
                                            RelationFlags.BLOCKED.getValue()))
                            .addStatement(
                                    insertRelationVersionStmt.bind(
                                            userId,
                                            stranger.getNickName(),
                                            RelationOperation.ADD_STRANGER.getValue(),
                                            stranger.getNickName(),
                                            stranger.getAvatar(),
                                            stranger.getNickName(),
                                            RelationFlags.BLOCKED.getValue()))
                            .build();
            session.execute(batch);
        }
    }

    @Override
    public void removeBlock(long userId, long blockedUserId) throws NotFoundBlockException {
        // Check if block exists
        Relation relation = this.getRelationBetweenUser(userId, blockedUserId);
        if (relation == null || !RelationFlags.BLOCKED.isSet(relation.getRelationFlags())) {
            throw new NotFoundBlockException(
                    "Block not found between " + userId + " and " + blockedUserId);
        }
        byte newFlags = RelationFlags.clearFlag(relation.getRelationFlags(), RelationFlags.BLOCKED);
        BatchStatementBuilder batchBuilder = new BatchStatementBuilder(DefaultBatchType.LOGGED);
        if (newFlags == 0) {
            // stranger, delete relation
            batchBuilder.addStatement(deleteRelationStmt.bind(userId, blockedUserId));
            batchBuilder.addStatement(
                    insertRelationVersionStmt.bind(
                            userId, blockedUserId, RelationOperation.DELETE_BLOCK.getValue()));
        } else if (RelationFlags.FRIEND.isSet(newFlags)) {
            // still a friend, update flags
            batchBuilder.addStatement(
                    updateRelationFlagsStmt.bind(newFlags, userId, blockedUserId));
            batchBuilder.addStatement(
                    insertRelationVersionStmt.bind(
                            userId,
                            blockedUserId,
                            RelationOperation.UNBLOCK_RESTORE_FRIEND.getValue(),
                            null,
                            null,
                            null,
                            newFlags));
        }
        session.execute(batchBuilder.build());
    }

    @Override
    public void hideBlock(long userId, long blockedUserId) throws NotFoundBlockException {
        // Check if block exists
        Relation relation = this.getRelationBetweenUser(userId, blockedUserId);
        if (relation == null || !RelationFlags.BLOCKED.isSet(relation.getRelationFlags())) {
            throw new NotFoundBlockException(
                    "Block not found between " + userId + " and " + blockedUserId);
        }

        byte currentFlags = relation.getRelationFlags();
        byte newFlags = RelationFlags.setFlag(currentFlags, RelationFlags.HIDDEN);
        // If user is also a friend, clear FRIEND flag when hiding block
        if (RelationFlags.FRIEND.isSet(newFlags)) {
            newFlags = RelationFlags.clearFlag(newFlags, RelationFlags.FRIEND);
        }
        // Update relation_flags to set HIDDEN bit and record version
        BatchStatement batch =
                new BatchStatementBuilder(DefaultBatchType.LOGGED)
                        .addStatement(updateRelationFlagsStmt.bind(newFlags, userId, blockedUserId))
                        .addStatement(
                                insertRelationVersionStmt.bind(
                                        userId,
                                        blockedUserId,
                                        RelationOperation.HIDE_BLOCK.getValue(),
                                        null,
                                        null,
                                        null,
                                        newFlags))
                        .build();
        session.execute(batch);
    }

    @Override
    public List<Long> getFriendIds(long userId) {
        ResultSet resultSet = session.execute(getAllRelationsStmt.bind(userId));

        List<Long> friendIdList = new ArrayList<>();
        for (Row row : resultSet) {
            byte relationFlags = row.getByte("relation_flags");
            if (RelationFlags.FRIEND.isSet(relationFlags)) {
                friendIdList.add(row.getLong("relation_user_id"));
            }
        }
        return friendIdList;
    }

    @Override
    public List<RelationVersionChange> getRelationChanges(
            long userId, String afterVersion, int limit) throws InvalidVersionException {
        // Validate afterVersion parameter
        if (afterVersion == null || afterVersion.isEmpty()) {
            throw new InvalidVersionException(
                    "afterVersion cannot be null or empty. Use version from previous sync or call with fullSync.");
        }

        UUID afterVersionUuid = Uuids.endOf(Long.parseLong(afterVersion));
        if (afterVersionUuid.version() != 1) {
            throw new InvalidVersionException(
                    "Invalid version format: "
                            + afterVersion
                            + ". Version must be a valid UUIDv1.");
        }
        ResultSet resultSet =
                session.execute(getRelationChangesStmt.bind(userId, afterVersionUuid, limit));

        List<RelationVersionChange> changes = new ArrayList<>();
        for (Row row : resultSet) {
            RelationVersionChange record = new RelationVersionChange();
            record.setVersion(String.valueOf(Uuids.unixTimestamp(row.getUuid("version"))));
            record.setRelationUserId(row.getLong("relation_user_id"));
            record.setOperation(row.getByte("operation"));
            record.setNickName(row.getString("nick_name"));
            record.setAvatar(row.getString("avatar"));
            record.setRemarkName(row.getString("remark_name"));
            record.setRelationFlags(row.getByte("relation_flags"));
            changes.add(record);
        }
        return changes;
    }

    @Override
    public String getLatestRelationVersion(long userId) {
        Row row = session.execute(getLatestVersionStmt.bind(userId)).one();
        if (row == null) {
            return null;
        }
        return String.valueOf(Uuids.unixTimestamp(row.getUuid("version")));
    }

    @Override
    public Relation getRelationBetweenUser(long userId, long targetUserId) {
        Row row = session.execute(getRelationBetweenUsersStmt.bind(userId, targetUserId)).one();
        if (row == null) {
            return null;
        }
        Relation relation = new Relation();
        relation.setSourceUserId(row.getLong("user_id"));
        relation.setRelationUserId(row.getLong("relation_user_id"));
        relation.setRelationNickName(row.getString("nick_name"));
        relation.setRelationAvatar(row.getString("avatar"));
        relation.setRelationRemarkName(row.getString("remark_name"));
        relation.setRelationFlags(row.getByte("relation_flags"));
        return relation;
    }
}
