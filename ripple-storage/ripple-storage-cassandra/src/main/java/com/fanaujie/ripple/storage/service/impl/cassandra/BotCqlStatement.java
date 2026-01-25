package com.fanaujie.ripple.storage.service.impl.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import lombok.Getter;

@Getter
public class BotCqlStatement {
    private final PreparedStatement selectBotConfigStmt;
    private final PreparedStatement insertBotConfigStmt;
    private final PreparedStatement deleteBotConfigStmt;
    private final PreparedStatement selectUserRoleStmt;
    private final PreparedStatement selectAllBotsStmt;

    public BotCqlStatement(CqlSession session) {
        this.selectBotConfigStmt = session.prepare(
                "SELECT user_id, webhook_url, api_key, description, created_at, updated_at " +
                "FROM ripple.bot_config WHERE user_id = ?");

        this.insertBotConfigStmt = session.prepare(
                "INSERT INTO ripple.bot_config (user_id, webhook_url, api_key, description, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)");

        this.deleteBotConfigStmt = session.prepare(
                "DELETE FROM ripple.bot_config WHERE user_id = ?");

        this.selectUserRoleStmt = session.prepare(
                "SELECT role FROM ripple.user WHERE account = ?");

        this.selectAllBotsStmt = session.prepare(
                "SELECT user_id, webhook_url, api_key, description, created_at, updated_at " +
                "FROM ripple.bot_config");
    }
}
