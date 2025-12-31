package com.fanaujie.ripple.storage.service.impl.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import lombok.Getter;

@Getter
public class BotCqlStatement {
    // Bot config statements (bot_config table - no name/avatar, uses user_profile for identity)
    private final PreparedStatement insertBotConfigStmt;
    private final PreparedStatement selectBotConfigStmt;
    private final PreparedStatement selectAllBotConfigsStmt;

    // User installed bots statements
    private final PreparedStatement insertUserBotStmt;
    private final PreparedStatement deleteUserBotStmt;
    private final PreparedStatement selectUserBotsStmt;

    // Bot user tokens statements
    private final PreparedStatement insertBotTokenStmt;
    private final PreparedStatement selectBotTokenStmt;

    public BotCqlStatement(CqlSession session) {
        // bot_config table: configuration only (identity stored in user_profile)
        this.insertBotConfigStmt = session.prepare(
                "INSERT INTO ripple.bot_config (bot_id, endpoint, secret, require_auth, auth_config, enabled, category, description, developer_name, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        this.selectBotConfigStmt = session.prepare("SELECT * FROM ripple.bot_config WHERE bot_id = ?");
        this.selectAllBotConfigsStmt = session.prepare("SELECT * FROM ripple.bot_config");

        this.insertUserBotStmt = session.prepare("INSERT INTO ripple.user_installed_bots (user_id, bot_id, installed_at, settings) VALUES (?, ?, ?, ?)");
        this.deleteUserBotStmt = session.prepare("DELETE FROM ripple.user_installed_bots WHERE user_id = ? AND bot_id = ?");
        this.selectUserBotsStmt = session.prepare("SELECT * FROM ripple.user_installed_bots WHERE user_id = ?");

        this.insertBotTokenStmt = session.prepare("INSERT INTO ripple.bot_user_tokens (bot_id, user_id, access_token, refresh_token, expires_at) VALUES (?, ?, ?, ?, ?)");
        this.selectBotTokenStmt = session.prepare("SELECT * FROM ripple.bot_user_tokens WHERE bot_id = ? AND user_id = ?");
    }
}
