package com.fanaujie.ripple.storage.repository.impl;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.*;
import com.fanaujie.ripple.storage.exception.NotFoundUserProfileException;
import com.fanaujie.ripple.storage.model.User;
import com.fanaujie.ripple.storage.model.UserProfile;
import com.fanaujie.ripple.storage.repository.UserRepository;

import java.util.concurrent.CompletableFuture;

public class CassandraUserRepository implements UserRepository {

    private final CqlSession session;
    private final PreparedStatement selectUserStmt;
    private final PreparedStatement selectAccountStmt;
    private final PreparedStatement insertUserStmt;
    private final PreparedStatement insertUserProfileStmt;
    private final PreparedStatement selectUserIdStmt;
    private final PreparedStatement selectUserProfileStmt;
    private final PreparedStatement updateAvatarStmt;
    private final PreparedStatement updateNickNameStmt;

    public CassandraUserRepository(CqlSession session) {
        this.session = session;
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

    @Override
    public User findByAccount(String account) {
        BoundStatement bound = selectUserStmt.bind(account);
        Row row = session.execute(bound).one();
        if (row == null) {
            return null;
        }
        return new User(
                row.getLong("user_id"),
                row.getString("account"),
                row.getString("password"),
                row.getString("role"),
                row.getByte("status"));
    }

    @Override
    public void insertUser(User user, String displayName, String avatar) {
        BoundStatement userBound =
                insertUserStmt.bind(
                        user.getUserId(),
                        user.getAccount(),
                        user.getPassword(),
                        user.getRole(),
                        user.getStatus());
        BoundStatement profileBound =
                insertUserProfileStmt.bind(
                        user.getUserId(), user.getAccount(), displayName, avatar);
        BatchStatement batch =
                BatchStatement.newInstance(DefaultBatchType.LOGGED, userBound, profileBound);
        session.execute(batch);
    }

    @Override
    public boolean userExists(String account) {
        BoundStatement bound = selectAccountStmt.bind(account);
        ResultSet rs = session.execute(bound);
        return rs.one() != null;
    }

    @Override
    public UserProfile getUserProfile(long userId) throws NotFoundUserProfileException {
        BoundStatement bound = selectUserProfileStmt.bind(userId);
        Row row = session.execute(bound).one();
        if (row == null) {
            throw new NotFoundUserProfileException("User profile not found for userId: " + userId);
        }
        return new UserProfile(
                row.getLong("user_id"),
                row.getString("account"),
                row.getString("nick_name"),
                row.getString("avatar"));
    }

    @Override
    public void updateAvatarByUserId(long userId, String avatar)
            throws NotFoundUserProfileException {
        Row row = profileExists(userId);
        if (row == null) {
            throw new NotFoundUserProfileException("User profile not found for userId: " + userId);
        }
        BoundStatement bound = updateAvatarStmt.bind(avatar, userId);
        session.execute(bound);
    }

    @Override
    public void updateNickNameByUserId(long userId, String nickName)
            throws NotFoundUserProfileException {
        Row row = profileExists(userId);
        if (row == null) {
            throw new NotFoundUserProfileException("User profile not found for userId: " + userId);
        }
        BoundStatement bound = updateNickNameStmt.bind(nickName, userId);
        session.execute(bound);
    }

    @Override
    public boolean userIdExists(long userId) {
        return profileExists(userId) != null;
    }

    private Row profileExists(long userId) {
        BoundStatement bound = selectUserIdStmt.bind(userId);
        return session.execute(bound).one();
    }
}
