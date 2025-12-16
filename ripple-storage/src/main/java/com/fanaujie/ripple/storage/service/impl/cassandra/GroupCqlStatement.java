package com.fanaujie.ripple.storage.service.impl.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import lombok.Getter;

@Getter
public class GroupCqlStatement {
    private final PreparedStatement insertGroupMemberStmt;
    private final PreparedStatement deleteGroupMemberStmt;
    private final PreparedStatement updateGroupMemberNameStmt;
    private final PreparedStatement updateGroupMemberAvatarStmt;
    private final PreparedStatement selectGroupMembersStmt;
    private final PreparedStatement selectGroupMembersFirstPageStmt;
    private final PreparedStatement selectGroupMembersNextPageStmt;
    private final PreparedStatement selectGroupMemberIdsStmt;

    private final PreparedStatement insertGroupMemberVersionStmt;
    private final PreparedStatement selectGroupChangesStmt;
    private final PreparedStatement selectLatestGroupVersionStmt;

    private final PreparedStatement insertUserGroupStmt;
    private final PreparedStatement updateUserGroupNameStmt;
    private final PreparedStatement updateUserGroupAvatarStmt;
    private final PreparedStatement deleteUserGroupStmt;
    private final PreparedStatement selectUserGroupIdsStmt;
    private final PreparedStatement selectUserGroupsStmt;
    private final PreparedStatement selectUserGroupsFirstPageStmt;
    private final PreparedStatement selectUserGroupsNextPageStmt;
    private final PreparedStatement selectLatestUserGroupVersionStmt;

    private final PreparedStatement insertUserGroupVersionStmt;
    private final PreparedStatement selectUserGroupChangesStmt;

    public GroupCqlStatement(CqlSession session) {
        // group_members operations
        this.insertGroupMemberStmt =
                session.prepare(
                        "INSERT INTO ripple.group_members (group_id, user_id, name, avatar) VALUES (?, ?, ?, ?)");
        this.deleteGroupMemberStmt =
                session.prepare(
                        "DELETE FROM ripple.group_members WHERE group_id = ? AND user_id = ?");
        this.updateGroupMemberNameStmt =
                session.prepare(
                        "UPDATE ripple.group_members SET name = ? WHERE group_id = ? AND user_id = ?");
        this.updateGroupMemberAvatarStmt =
                session.prepare(
                        "UPDATE ripple.group_members SET avatar = ? WHERE group_id = ? AND user_id = ?");
        this.selectGroupMembersStmt =
                session.prepare(
                        "SELECT user_id, name, avatar FROM ripple.group_members WHERE group_id = ?");
        this.selectGroupMembersFirstPageStmt =
                session.prepare(
                        "SELECT user_id, name, avatar FROM ripple.group_members WHERE group_id = ? LIMIT ?");
        this.selectGroupMembersNextPageStmt =
                session.prepare(
                        "SELECT user_id, name, avatar FROM ripple.group_members WHERE group_id = ? AND user_id > ? LIMIT ?");
        this.selectGroupMemberIdsStmt =
                session.prepare("SELECT user_id FROM ripple.group_members WHERE group_id = ?");

        // group_members_version operations
        this.insertGroupMemberVersionStmt =
                session.prepare(
                        "INSERT INTO ripple.group_members_version (group_id, version, changes) "
                                + "VALUES (?, ?, ?)");
        this.selectGroupChangesStmt =
                session.prepare(
                        "SELECT version, changes "
                                + "FROM ripple.group_members_version WHERE group_id = ? AND version > ? LIMIT ?");
        this.selectLatestGroupVersionStmt =
                session.prepare(
                        "SELECT version FROM ripple.group_members_version WHERE group_id = ? ORDER BY version DESC LIMIT 1");

        // user_group operations
        this.insertUserGroupStmt =
                session.prepare(
                        "INSERT INTO ripple.user_group (user_id, group_id, group_name, group_avatar) VALUES (?, ?, ?, ?)");
        this.updateUserGroupNameStmt =
                session.prepare(
                        "UPDATE ripple.user_group SET group_name = ? WHERE user_id = ? AND group_id = ?");
        this.updateUserGroupAvatarStmt =
                session.prepare(
                        "UPDATE ripple.user_group SET group_avatar = ? WHERE user_id = ? AND group_id = ?");
        this.deleteUserGroupStmt =
                session.prepare("DELETE FROM ripple.user_group WHERE user_id = ? AND group_id = ?");
        this.selectUserGroupIdsStmt =
                session.prepare("SELECT group_id FROM ripple.user_group WHERE user_id = ?");
        this.selectUserGroupsStmt =
                session.prepare(
                        "SELECT group_id, group_name, group_avatar FROM ripple.user_group WHERE user_id = ?");
        this.selectUserGroupsFirstPageStmt =
                session.prepare(
                        "SELECT group_id, group_name, group_avatar FROM ripple.user_group WHERE user_id = ? LIMIT ?");
        this.selectUserGroupsNextPageStmt =
                session.prepare(
                        "SELECT group_id, group_name, group_avatar FROM ripple.user_group WHERE user_id = ? AND group_id > ? LIMIT ?");
        this.selectLatestUserGroupVersionStmt =
                session.prepare(
                        "SELECT version FROM ripple.user_group_version WHERE user_id = ? ORDER BY version DESC LIMIT 1");

        this.insertUserGroupVersionStmt =
                session.prepare(
                        "INSERT INTO ripple.user_group_version (user_id, version,group_id, operation, group_name, group_avatar) VALUES (?, ?, ?, ?, ?, ?)");
        this.selectUserGroupChangesStmt =
                session.prepare(
                        "SELECT version, group_id ,operation, group_name, group_avatar "
                                + "FROM ripple.user_group_version WHERE user_id = ? AND version > ? LIMIT ?");
    }
}
