package com.fanaujie.ripple.storage.service.impl.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import lombok.Getter;

@Getter
public class UserCqlStatement {
    private final PreparedStatement selectUserStmt;
    private final PreparedStatement selectAccountStmt;
    private final PreparedStatement insertUserStmt;
    private final PreparedStatement insertUserProfileStmt;
    private final PreparedStatement selectUserIdStmt;
    private final PreparedStatement selectUserProfileStmt;
    private final PreparedStatement updateAvatarStmt;
    private final PreparedStatement updateNickNameStmt;

    public UserCqlStatement(CqlSession session) {
        this.selectUserStmt =
                session.prepare(
                        "SELECT account,user_id, password, role, status FROM ripple.user WHERE account = ?");
        this.selectAccountStmt =
                session.prepare("SELECT account FROM ripple.user WHERE account = ?");
        this.insertUserStmt =
                session.prepare(
                        "INSERT INTO ripple.user (user_id, account, password, role, status) VALUES (?, ?, ?, ?, ?)");
        this.insertUserProfileStmt =
                session.prepare(
                        "INSERT INTO ripple.user_profile (user_id, account, nick_name, avatar) VALUES (?, ?, ?, ?)");
        this.selectUserIdStmt =
                session.prepare("SELECT user_id FROM ripple.user_profile WHERE user_id = ?");
        this.selectUserProfileStmt =
                session.prepare(
                        "SELECT user_id, account, nick_name, avatar FROM ripple.user_profile WHERE user_id = ?");
        this.updateAvatarStmt =
                session.prepare("UPDATE ripple.user_profile SET avatar = ? WHERE user_id = ?");
        this.updateNickNameStmt =
                session.prepare("UPDATE ripple.user_profile SET nick_name = ? WHERE user_id = ?");
    }
}
