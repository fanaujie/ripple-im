package com.fanaujie.ripple.storage.service.impl.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import lombok.Getter;

@Getter
public class RelationCqlStatement {
    private final PreparedStatement selectRelationFlagStmt;
    private final PreparedStatement selectFullRelationStmt;
    private final PreparedStatement selectAllRelationsStmt;
    private final PreparedStatement selectRelationsFirstPageStmt;
    private final PreparedStatement selectRelationsNextPageStmt;
    private final PreparedStatement insertRelationStmt;
    private final PreparedStatement insertRelationVersionStmt;
    private final PreparedStatement deleteRelationStmt;
    private final PreparedStatement updateRelationRemarkNameStmt;
    private final PreparedStatement updateRelationFlagsStmt;
    private final PreparedStatement updateFriendNickNameStmt;
    private final PreparedStatement updateFriendAvatarStmt;
    private final PreparedStatement updateFriendInfoStmt;
    private final PreparedStatement selectRelationChangesStmt;
    private final PreparedStatement selectLatestVersionStmt;
    private final PreparedStatement selectRelationBetweenUsersStmt;
    private final PreparedStatement selectFriendIdsStmt;

    public RelationCqlStatement(CqlSession session) {
        this.selectRelationFlagStmt =
                session.prepare(
                        "SELECT relation_flags FROM ripple.user_relations WHERE user_id = ? AND relation_user_id = ?");
        this.selectFullRelationStmt =
                session.prepare(
                        "SELECT nick_name, avatar, remark_name, relation_flags FROM ripple.user_relations WHERE user_id = ? AND relation_user_id = ?");
        this.selectAllRelationsStmt =
                session.prepare(
                        "SELECT relation_user_id, relation_flags FROM ripple.user_relations WHERE user_id = ?");
        this.selectRelationsFirstPageStmt =
                session.prepare(
                        "SELECT relation_user_id, nick_name, avatar, remark_name, relation_flags FROM ripple.user_relations WHERE user_id = ? LIMIT ?");
        this.selectRelationsNextPageStmt =
                session.prepare(
                        "SELECT relation_user_id, nick_name, avatar, remark_name, relation_flags FROM ripple.user_relations WHERE user_id = ? AND relation_user_id > ? LIMIT ?");
        this.insertRelationStmt =
                session.prepare(
                        "INSERT INTO ripple.user_relations (user_id, relation_user_id, nick_name, avatar, remark_name, relation_flags) "
                                + "VALUES (?, ?, ?, ?, ?, ?)");
        this.insertRelationVersionStmt =
                session.prepare(
                        "INSERT INTO ripple.user_relation_version (user_id, version, relation_user_id, operation, nick_name, avatar, remark_name, relation_flags) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
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
        this.selectRelationChangesStmt =
                session.prepare(
                        "SELECT version,relation_user_id, operation, nick_name, avatar, remark_name, relation_flags, version FROM ripple.user_relation_version WHERE user_id = ? AND version > ? LIMIT ?");
        this.selectLatestVersionStmt =
                session.prepare(
                        "SELECT version FROM ripple.user_relation_version WHERE user_id = ? ORDER BY version DESC LIMIT 1");
        this.selectRelationBetweenUsersStmt =
                session.prepare(
                        "SELECT user_id,relation_user_id,nick_name,avatar,remark_name, relation_flags FROM ripple.user_relations WHERE user_id = ? and relation_user_id = ? ");
        this.selectFriendIdsStmt =
                session.prepare(
                        "SELECT relation_user_id FROM ripple.user_relations WHERE user_id = ?");
    }
}
