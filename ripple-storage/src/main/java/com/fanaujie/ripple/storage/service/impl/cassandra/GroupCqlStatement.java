package com.fanaujie.ripple.storage.service.impl.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import lombok.Data;

@Data
public class GroupCqlStatement {
    private final PreparedStatement insertGroupStmt;
    private final PreparedStatement insertUserGroupStmt;
    private final PreparedStatement selectGroupMembersStmt;
    private final PreparedStatement insertGroupMemberInfoStmt;
    private final PreparedStatement selectUserGroupsStmt;
    private final PreparedStatement updateGroupMemberInfoStmt;
    private final PreparedStatement selectGroupStmt;

    public GroupCqlStatement(CqlSession session) {
        this.insertGroupStmt =
                session.prepare(
                        "INSERT INTO ripple.groups (group_id, group_name, group_avatar, member_ids, version) "
                                + "VALUES (?, ?, ?, ?, ?)");

        this.insertUserGroupStmt =
                session.prepare("INSERT INTO ripple.user_groups (user_id, group_id) VALUES (?, ?)");

        this.selectGroupMembersStmt =
                session.prepare("SELECT user_id FROM ripple.user_groups WHERE user_id = ?");

        this.insertGroupMemberInfoStmt =
                session.prepare(
                        "INSERT INTO ripple.group_members_info (group_id, user_id, name, avatar) "
                                + "VALUES (?, ?, ?, ?)");

        this.selectUserGroupsStmt =
                session.prepare("SELECT group_id FROM ripple.user_groups WHERE user_id = ?");

        this.updateGroupMemberInfoStmt =
                session.prepare(
                        "UPDATE ripple.group_members_info SET name = ?, avatar = ? "
                                + "WHERE group_id = ? AND user_id = ?");

        this.selectGroupStmt =
                session.prepare(
                        "SELECT group_name, group_avatar, member_ids FROM ripple.groups WHERE group_id = ?");
    }
}
