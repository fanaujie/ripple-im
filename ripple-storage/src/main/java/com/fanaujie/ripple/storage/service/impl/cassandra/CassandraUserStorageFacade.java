package com.fanaujie.ripple.storage.service.impl.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.*;
import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.fanaujie.ripple.protobuf.msgapiserver.RelationEvent;
import com.fanaujie.ripple.protobuf.msgapiserver.SingleMessageContent;
import com.fanaujie.ripple.protobuf.storage.UserIds;
import com.fanaujie.ripple.storage.cache.KvCache;
import com.fanaujie.ripple.storage.cache.RelationCachePrefixKey;
import com.fanaujie.ripple.storage.cache.impl.RedissonKvCache;
import com.fanaujie.ripple.storage.exception.*;
import com.fanaujie.ripple.storage.model.*;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import com.fanaujie.ripple.storage.service.utils.ConversationUtils;
import com.google.protobuf.InvalidProtocolBufferException;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class CassandraUserStorageFacade implements RippleStorageFacade {

    private final Logger logger = LoggerFactory.getLogger(CassandraUserStorageFacade.class);
    private final RedissonClient redissonClient;
    private final KvCache kvCache;
    private final long kvCacheExpireSeconds;
    private final CqlSession session;
    private final UserCqlStatement userCqlStatement;
    private final RelationCqlStatement relationCqlStatement;
    private final ConversationCqlStatement conversationCqlStatement;

    public CassandraUserStorageFacade(CassandraUserStorageFacadeBuilder builder) {
        this.session = builder.getSession();
        this.redissonClient = builder.getRedissonClient();
        this.kvCacheExpireSeconds = builder.getKvCacheExpireSeconds();
        this.kvCache =
                this.redissonClient != null ? new RedissonKvCache(this.redissonClient) : null;
        this.userCqlStatement = new UserCqlStatement(this.session);
        this.relationCqlStatement = new RelationCqlStatement(this.session);
        this.conversationCqlStatement = new ConversationCqlStatement(this.session);
    }

    @Override
    public User findByAccount(String account) {
        BoundStatement bound = userCqlStatement.getSelectUserStmt().bind(account);
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
    public boolean userExists(String account) {
        BoundStatement bound = this.userCqlStatement.getSelectAccountStmt().bind(account);
        ResultSet rs = session.execute(bound);
        return rs.one() != null;
    }

    @Override
    public void insertUser(User user, String displayName, String avatar) {
        BoundStatement userBound =
                this.userCqlStatement
                        .getInsertUserStmt()
                        .bind(
                                user.getUserId(),
                                user.getAccount(),
                                user.getPassword(),
                                user.getRole(),
                                user.getStatus());
        BoundStatement profileBound =
                this.userCqlStatement
                        .getInsertUserProfileStmt()
                        .bind(user.getUserId(), user.getAccount(), displayName, avatar);
        BatchStatement batch =
                new BatchStatementBuilder(DefaultBatchType.LOGGED)
                        .addStatement(userBound)
                        .addStatement(profileBound)
                        .build();
        session.execute(batch);
    }

    @Override
    public UserProfile getUserProfile(long userId) throws NotFoundUserProfileException {
        BoundStatement bound = this.userCqlStatement.getSelectUserProfileStmt().bind(userId);
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
    public void updateProfileNickNameByUserId(long userId, String nickName)
            throws NotFoundUserProfileException {
        Row row = profileExists(userId);
        if (row == null) {
            throw new NotFoundUserProfileException("User profile not found for userId: " + userId);
        }
        BoundStatement bound = userCqlStatement.getUpdateNickNameStmt().bind(nickName, userId);
        session.execute(bound);
    }

    @Override
    public void updateProfileAvatarByUserId(long userId, String avatar)
            throws NotFoundUserProfileException {
        Row row = profileExists(userId);
        if (row == null) {
            throw new NotFoundUserProfileException("User profile not found for userId: " + userId);
        }
        BoundStatement bound = userCqlStatement.getUpdateAvatarStmt().bind(avatar, userId);
        session.execute(bound);
    }

    @Override
    public PagedRelationResult getRelations(long sourceUserId, String nextPageToken, int pageSize) {
        // Query pageSize + 1 to check if there are more records
        int limit = pageSize + 1;
        ResultSet resultSet;

        if (nextPageToken == null || nextPageToken.isEmpty()) {
            // First page
            resultSet =
                    session.execute(
                            this.relationCqlStatement
                                    .getSelectRelationsFirstPageStmt()
                                    .bind(sourceUserId, limit));
        } else {
            // Next page, parse token as last relation_user_id
            long lastRelationUserId = Long.parseLong(nextPageToken);
            resultSet =
                    session.execute(
                            this.relationCqlStatement
                                    .getSelectRelationsNextPageStmt()
                                    .bind(sourceUserId, lastRelationUserId, limit));
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
    public Relation getRelationBetweenUser(long userId, long targetUserId) {
        Row row =
                session.execute(
                                this.relationCqlStatement
                                        .getSelectRelationBetweenUsersStmt()
                                        .bind(userId, targetUserId))
                        .one();
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
                session.execute(
                        relationCqlStatement
                                .getSelectRelationChangesStmt()
                                .bind(userId, afterVersionUuid, limit));

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
        Row row =
                session.execute(relationCqlStatement.getSelectLatestVersionStmt().bind(userId))
                        .one();
        if (row == null) {
            return null;
        }
        return String.valueOf(Uuids.unixTimestamp(row.getUuid("version")));
    }

    @Override
    public Messages getMessages(String conversationId, long beforeMessageId, int pageSize) {
        // Query pageSize + 1 to check if there are more records
        int limit = pageSize + 1;

        // When beforeMessageId is 0, use Long.MAX_VALUE to get the latest messages
        long effectiveBeforeMessageId = (beforeMessageId == 0) ? Long.MAX_VALUE : beforeMessageId;

        ResultSet resultSet =
                session.execute(
                        conversationCqlStatement
                                .getSelectMessagesStmt()
                                .bind(conversationId, effectiveBeforeMessageId, limit));

        List<Message> messages = new ArrayList<>();
        for (Row row : resultSet) {
            Message message = new Message();
            message.setConversationId(row.getString("conversation_id"));
            message.setMessageId(row.getLong("message_id"));
            message.setSenderId(row.getLong("sender_id"));
            message.setReceiverId(row.getLong("receiver_id"));
            message.setGroupId(row.getLong("group_id"));
            message.setSendTimestamp(row.getLong("send_timestamp"));
            message.setText(row.getString("text"));
            message.setFileUrl(row.getString("file_url"));
            message.setFileName(row.getString("file_name"));
            messages.add(message);
        }
        return new Messages(messages);
    }

    @Override
    public void markLastReadMessageId(String conversationId, long ownerId, long readMessageId) {
        BatchStatement batch =
                new BatchStatementBuilder(DefaultBatchType.LOGGED)
                        .addStatement(
                                conversationCqlStatement
                                        .getUpdateLastReadMessageIdStmt()
                                        .bind(readMessageId, ownerId, conversationId))
                        .addStatement(
                                conversationCqlStatement
                                        .getInsertConversationVersionStmt()
                                        .bind(
                                                ownerId,
                                                conversationId,
                                                null, // peer_id
                                                null, // group_id
                                                ConversationOperation.READ_MESSAGES.getValue(),
                                                null, // last_message_id
                                                null, // last_message
                                                null, // last_message_timestamp
                                                readMessageId, // last_read_message_id
                                                null, // name not updated when marking read
                                                null)) // avatar not updated when marking read
                        .build();
        session.execute(batch);
    }

    @Override
    public boolean existsByConversationId(String conversationId, long ownerId) {
        Row ownerRow =
                session.execute(
                                conversationCqlStatement
                                        .getExistsConversationStmt()
                                        .bind(ownerId, conversationId))
                        .one();
        return ownerRow != null;
    }

    @Override
    public void createSingeMessageConversation(String conversationId, long ownerId, long peerId)
            throws NotFoundUserProfileException {
        String name = null;
        String avatar = null;
        Relation relation = getRelationBetweenUser(ownerId, peerId);
        if (relation == null) {
            // Peer is not a friend, fetch profile for basic info
            UserProfile peerProfile = getUserProfile(peerId);
            name = peerProfile.getNickName();
            avatar = peerProfile.getAvatar();
        } else {
            name =
                    relation.getRelationRemarkName() == null
                            ? relation.getRelationNickName()
                            : relation.getRelationRemarkName();
            avatar = relation.getRelationAvatar();
        }
        BatchStatement senderBatch =
                new BatchStatementBuilder(DefaultBatchType.LOGGED)
                        .addStatement(
                                conversationCqlStatement
                                        .getInsertConversationStmt()
                                        .bind(
                                                ownerId,
                                                conversationId,
                                                peerId,
                                                null, // group_id is null for single conversations
                                                null,
                                                null,
                                                null,
                                                null, // last_read_message_id is null initially
                                                name,
                                                avatar))
                        // events
                        .addStatement(
                                conversationCqlStatement
                                        .getInsertConversationVersionStmt()
                                        .bind(
                                                ownerId,
                                                conversationId,
                                                peerId,
                                                null,
                                                ConversationOperation.CREATE_CONVERSATION
                                                        .getValue(),
                                                null, // last_message_id is null for create
                                                // conversation
                                                null,
                                                null,
                                                null, // last_read_message_id is null for create
                                                name,
                                                avatar))
                        // events
                        .build();
        session.execute(senderBatch);
    }

    @Override
    public void updateSingeMessageConversation(
            String conversationId,
            long ownerId,
            long peerId,
            long lastMessageId,
            long lastMessageTimestamp,
            SingleMessageContent singleMessageContent) {
        BatchStatementBuilder batchBuilder = new BatchStatementBuilder(DefaultBatchType.LOGGED);

        // Update conversation with new message
        batchBuilder.addStatement(
                conversationCqlStatement
                        .getUpdateNewMessageStmt()
                        .bind(
                                lastMessageId,
                                singleMessageContent.getText(),
                                lastMessageTimestamp,
                                ownerId,
                                conversationId));
        batchBuilder.addStatement(
                conversationCqlStatement
                        .getInsertConversationVersionStmt()
                        .bind(
                                ownerId,
                                conversationId,
                                peerId,
                                null,
                                ConversationOperation.NEW_MESSAGE.getValue(),
                                lastMessageId,
                                singleMessageContent.getText(),
                                lastMessageTimestamp,
                                null, // last_read_message_id not updated when new message arrives
                                null, // name not updated when new message arrives
                                null)); // avatar not updated when new message arrives
        session.execute(batchBuilder.build());
    }

    @Override
    public void saveMessage(
            String conversationId,
            long messageId,
            long senderId,
            long receiverId,
            long groupId,
            long timestamp,
            SingleMessageContent content) {
        session.execute(
                conversationCqlStatement
                        .getInsertMessageStmt()
                        .bind(
                                conversationId,
                                messageId,
                                senderId,
                                receiverId == 0 ? null : receiverId,
                                groupId == 0 ? null : groupId,
                                timestamp,
                                content.getText(),
                                content.getFileUrl(),
                                content.getFileName()));
    }

    @Override
    public PagedConversationResult getConversations(
            long userId, String nextPageToken, int pageSize) {
        // Query pageSize + 1 to check if there are more records
        int limit = pageSize + 1;
        ResultSet resultSet;

        if (nextPageToken == null || nextPageToken.isEmpty()) {
            // First page
            resultSet =
                    session.execute(
                            conversationCqlStatement
                                    .getSelectConversationsFirstPageStmt()
                                    .bind(userId, limit));
        } else {
            // Next page, parse token as last conversation_id
            resultSet =
                    session.execute(
                            conversationCqlStatement
                                    .getSelectConversationsNextPageStmt()
                                    .bind(userId, nextPageToken, limit));
        }
        List<Conversation> conversations = new ArrayList<>();
        for (Row row : resultSet) {
            String conversationId = row.getString("conversation_id");
            long lastReadMessageId = row.getLong("last_read_message_id");
            Conversation conversation = new Conversation();
            conversation.setOwnerId(userId);
            conversation.setConversationId(conversationId);
            conversation.setPeerId(row.getLong("peer_id"));
            conversation.setGroupId(row.getLong("group_id"));
            conversation.setLastMessageId(row.getLong("last_message_id"));
            conversation.setLastMessage(row.getString("last_message"));
            conversation.setLastMessageTimestamp(row.getLong("last_message_timestamp"));
            conversation.setLastReadMessageId(lastReadMessageId);
            conversation.setUnreadCount(getUnreadCount(conversationId, lastReadMessageId, userId));
            conversation.setName(row.getString("name"));
            conversation.setAvatar(row.getString("avatar"));
            conversations.add(conversation);
        }

        // Check if there are more records
        boolean hasMore = conversations.size() > pageSize;
        String nextToken = null;

        if (hasMore) {
            // Remove the extra record and set next token
            conversations.remove(conversations.size() - 1);
            nextToken = conversations.get(conversations.size() - 1).getConversationId();
        }

        return new PagedConversationResult(conversations, nextToken, hasMore);
    }

    @Override
    public List<ConversationVersionChange> getConversationChanges(
            long userId, String afterVersion, int limit) throws InvalidVersionException {
        // Validate afterVersion parameter
        if (afterVersion == null || afterVersion.isEmpty()) {
            throw new InvalidVersionException(
                    "afterVersion cannot be null or empty. Use version from previous sync or call with fullSync.");
        }

        UUID afterVersionUuid;
        try {
            afterVersionUuid = Uuids.endOf(Long.parseLong(afterVersion));
        } catch (NumberFormatException e) {
            throw new InvalidVersionException(
                    "Invalid version format: "
                            + afterVersion
                            + ". Version must be a valid timestamp.");
        }

        if (afterVersionUuid.version() != 1) {
            throw new InvalidVersionException(
                    "Invalid version format: "
                            + afterVersion
                            + ". Version must be a valid UUIDv1.");
        }

        ResultSet resultSet =
                session.execute(
                        conversationCqlStatement
                                .getSelectConversationChangesStmt()
                                .bind(userId, afterVersionUuid, limit));

        List<ConversationVersionChange> changes = new ArrayList<>();
        for (Row row : resultSet) {
            ConversationVersionChange record = new ConversationVersionChange();
            record.setVersion(String.valueOf(Uuids.unixTimestamp(row.getUuid("version"))));
            record.setConversationId(row.getString("conversation_id"));
            record.setPeerId(row.getLong("peer_id"));
            record.setGroupId(row.getLong("group_id"));
            record.setOperation(row.getByte("operation"));
            record.setLastMessageId(row.getLong("last_message_id"));
            record.setLastMessage(row.getString("last_message"));
            record.setLastMessageTimestamp(row.getLong("last_message_timestamp"));
            record.setLastReadMessageId(row.getLong("last_read_message_id"));
            record.setName(row.getString("name"));
            record.setAvatar(row.getString("avatar"));
            changes.add(record);
        }
        return changes;
    }

    @Override
    public String getLatestConversationVersion(long userId) {
        Row row =
                session.execute(conversationCqlStatement.getSelectLatestVersionStmt().bind(userId))
                        .one();
        if (row == null) {
            return null;
        }
        return String.valueOf(Uuids.unixTimestamp(row.getUuid("version")));
    }

    @Override
    public Optional<UserIds> getFriendIds(long userId) {
        if (this.kvCache == null) {
            throw new IllegalStateException("KvCache is not initialized.");
        }
        String key = RelationCachePrefixKey.FRIEND_IDS.getValue() + userId;
        byte[] value =
                this.kvCache.getIfPresent(
                        key,
                        this.kvCacheExpireSeconds,
                        () -> {
                            ResultSet resultSet =
                                    session.execute(
                                            this.relationCqlStatement
                                                    .getSelectAllRelationsStmt()
                                                    .bind(userId));

                            List<Long> friendIds = new ArrayList<>();
                            for (Row row : resultSet) {
                                byte relationFlags = row.getByte("relation_flags");
                                if (RelationFlags.FRIEND.isSet(relationFlags)) {
                                    friendIds.add(row.getLong("relation_user_id"));
                                }
                            }
                            if (friendIds.isEmpty()) {
                                return null;
                            }
                            UserIds.Builder b = UserIds.newBuilder();
                            b.addAllUserIds(friendIds);
                            return b.build().toByteArray();
                        });
        if (value != null) {
            try {
                return Optional.of(UserIds.parseFrom(value));
            } catch (InvalidProtocolBufferException e) {
                logger.error("Failed to parse UserIds from cache for userId: {}", userId, e);
            }
        }
        return Optional.empty();
    }

    @Override
    public void addFriend(RelationEvent event)
            throws NotFoundUserProfileException, RelationAlreadyExistsException {
        // Check if relation already exists
        UserProfile friendProfile = this.getUserProfile(event.getTargetUserId());
        long initiatorId = event.getUserId();
        Row initiatorRelation =
                session.execute(
                                relationCqlStatement
                                        .getSelectRelationFlagStmt()
                                        .bind(initiatorId, friendProfile.getUserId()))
                        .one();
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
                                        relationCqlStatement
                                                .getUpdateRelationFlagsStmt()
                                                .bind(
                                                        RelationFlags.FRIEND.getValue(),
                                                        initiatorId,
                                                        friendProfile.getUserId()))
                                .addStatement(
                                        relationCqlStatement
                                                .getInsertRelationVersionStmt()
                                                .bind(
                                                        initiatorId,
                                                        friendProfile.getUserId(),
                                                        RelationOperation.ADD_FRIEND.getValue(),
                                                        friendProfile.getNickName(),
                                                        friendProfile.getAvatar(),
                                                        null,
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
                                relationCqlStatement
                                        .getInsertRelationStmt()
                                        .bind(
                                                initiatorId,
                                                friendProfile.getUserId(),
                                                friendProfile.getNickName(),
                                                friendProfile.getAvatar(),
                                                null,
                                                RelationFlags.FRIEND.getValue()))
                        .addStatement(
                                relationCqlStatement
                                        .getInsertRelationVersionStmt()
                                        .bind(
                                                initiatorId,
                                                friendProfile.getUserId(),
                                                RelationOperation.ADD_FRIEND.getValue(),
                                                friendProfile.getNickName(),
                                                friendProfile.getAvatar(),
                                                null,
                                                RelationFlags.FRIEND.getValue()))
                        .build();
        session.execute(batch);
    }

    @Override
    public void removeFriend(RelationEvent event) throws NotFoundRelationException {
        long initiatorId = event.getUserId();
        long friendId = event.getTargetUserId();
        // Check if friend relation exists
        Row relation =
                session.execute(
                                relationCqlStatement
                                        .getSelectRelationFlagStmt()
                                        .bind(initiatorId, friendId))
                        .one();

        if (relation == null || !RelationFlags.FRIEND.isSet(relation.getByte("relation_flags"))) {
            throw new NotFoundRelationException(
                    "Friend relation not found between " + initiatorId + " and " + friendId);
        }

        // Delete relation and record version using batch
        BatchStatement batch =
                new BatchStatementBuilder(DefaultBatchType.LOGGED)
                        .addStatement(
                                relationCqlStatement
                                        .getDeleteRelationStmt()
                                        .bind(initiatorId, friendId))
                        .addStatement(
                                relationCqlStatement
                                        .getInsertRelationVersionStmt()
                                        .bind(
                                                initiatorId,
                                                friendId,
                                                RelationOperation.DELETE_FRIEND.getValue()))
                        .build();
        session.execute(batch);
    }

    @Override
    public UpdateFriendRemarkNameResult updateFriendRemarkName(RelationEvent event)
            throws NotFoundRelationException {
        long sourceUserId = event.getUserId();
        long targetUserId = event.getTargetUserId();
        String remarkName = event.getTargetUserRemarkName();
        UpdateFriendRemarkNameResult result = new UpdateFriendRemarkNameResult();
        relationExits(sourceUserId, targetUserId);
        BatchStatementBuilder batchBuilder =
                new BatchStatementBuilder(DefaultBatchType.LOGGED)
                        .addStatement(
                                relationCqlStatement
                                        .getUpdateRelationRemarkNameStmt()
                                        .bind(remarkName, sourceUserId, targetUserId))
                        .addStatement(
                                relationCqlStatement
                                        .getInsertRelationVersionStmt()
                                        .bind(
                                                sourceUserId,
                                                targetUserId,
                                                RelationOperation.UPDATE_FRIEND_REMARK_NAME
                                                        .getValue(),
                                                null,
                                                null,
                                                remarkName,
                                                null));
        // When a user updates their friend's remark name and a single chat conversation
        // with that friend already exists, we need to synchronously update the conversation
        // name displayed in the conversation list. This ensures the conversation list shows
        // the latest remark name, keeping it consistent with the contact list's remark name.
        String conversationId =
                ConversationUtils.generateConversationId(sourceUserId, targetUserId);
        if (this.existsByConversationId(conversationId, sourceUserId)) {
            batchBuilder.addStatement(
                    conversationCqlStatement
                            .getUpdateNameStmt()
                            .bind(remarkName, sourceUserId, conversationId));
            batchBuilder.addStatement(
                    conversationCqlStatement
                            .getInsertConversationVersionStmt()
                            .bind(
                                    sourceUserId,
                                    conversationId,
                                    targetUserId,
                                    null,
                                    ConversationOperation.UPDATE_CONVERSATION_NAME.getValue(),
                                    null,
                                    null,
                                    null,
                                    null,
                                    remarkName,
                                    null));
            result.setConversationUpdated(true);
            result.setConversationId(conversationId);
        }
        session.execute(batchBuilder.build());
        return result;
    }

    @Override
    public void updateFriendNickName(long sourceUserId, long targetUserId, String nickName)
            throws NotFoundRelationException {
        Relation relation = getRelationBetweenUser(sourceUserId, targetUserId);
        if (relation == null) {
            throw new NotFoundRelationException(
                    "Friend relation not found between " + sourceUserId + " and " + targetUserId);
        }
        BatchStatementBuilder batchBuilder =
                new BatchStatementBuilder(DefaultBatchType.LOGGED)
                        .addStatement(
                                relationCqlStatement
                                        .getUpdateFriendNickNameStmt()
                                        .bind(nickName, sourceUserId, targetUserId))
                        .addStatement(
                                relationCqlStatement
                                        .getInsertRelationVersionStmt()
                                        .bind(
                                                sourceUserId,
                                                targetUserId,
                                                RelationOperation.UPDATE_FRIEND_NICK_NAME
                                                        .getValue(),
                                                nickName,
                                                null,
                                                null,
                                                null));
        // When a user updates their nick_name and a single chat conversation
        // with that friend already exists but not set remark_name, we need to synchronously update
        // the conversation
        // avatar displayed in the conversation list. This ensures the conversation list shows
        // the latest avatar, keeping it consistent with the contact list's avatar.
        String conversationId =
                ConversationUtils.generateConversationId(sourceUserId, targetUserId);
        if (this.existsByConversationId(conversationId, sourceUserId)
                && relation.getRelationRemarkName() == null) {
            batchBuilder.addStatement(
                    conversationCqlStatement
                            .getUpdateNameStmt()
                            .bind(nickName, sourceUserId, conversationId));
            batchBuilder.addStatement(
                    conversationCqlStatement
                            .getInsertConversationVersionStmt()
                            .bind(
                                    sourceUserId,
                                    conversationId,
                                    targetUserId,
                                    null,
                                    ConversationOperation.UPDATE_CONVERSATION_AVATAR.getValue(),
                                    null,
                                    null,
                                    null,
                                    null,
                                    nickName,
                                    null));
        }
        session.execute(batchBuilder.build());
    }

    @Override
    public void updateFriendAvatar(long sourceUserId, long targetUserId, String avatar)
            throws NotFoundRelationException {
        relationExits(sourceUserId, targetUserId);
        BatchStatementBuilder batchBuilder =
                new BatchStatementBuilder(DefaultBatchType.LOGGED)
                        .addStatement(
                                relationCqlStatement
                                        .getUpdateFriendAvatarStmt()
                                        .bind(avatar, sourceUserId, targetUserId))
                        .addStatement(
                                relationCqlStatement
                                        .getInsertRelationVersionStmt()
                                        .bind(
                                                sourceUserId,
                                                targetUserId,
                                                RelationOperation.UPDATE_FRIEND_AVATAR.getValue(),
                                                null,
                                                avatar,
                                                null,
                                                null));
        // When a user updates their avatar and a single chat conversation
        // with that friend already exists, we need to synchronously update the conversation
        // avatar displayed in the conversation list. This ensures the conversation list shows
        // the latest avatar, keeping it consistent with the contact list's avatar.
        String conversationId =
                ConversationUtils.generateConversationId(sourceUserId, targetUserId);
        if (this.existsByConversationId(conversationId, sourceUserId)) {
            batchBuilder.addStatement(
                    conversationCqlStatement
                            .getUpdateAvatarStmt()
                            .bind(avatar, sourceUserId, conversationId));
            batchBuilder.addStatement(
                    conversationCqlStatement
                            .getInsertConversationVersionStmt()
                            .bind(
                                    sourceUserId,
                                    conversationId,
                                    targetUserId,
                                    null,
                                    ConversationOperation.UPDATE_CONVERSATION_AVATAR.getValue(),
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    avatar));
        }
        session.execute(batchBuilder.build());
    }

    @Override
    public void blockFriend(RelationEvent event) throws BlockAlreadyExistsException {
        long userId = event.getUserId();
        long blockedUserId = event.getTargetUserId();
        Row initiatorRelation =
                session.execute(
                                relationCqlStatement
                                        .getSelectRelationFlagStmt()
                                        .bind(userId, blockedUserId))
                        .one();
        if (initiatorRelation != null) {
            byte currentFlags = initiatorRelation.getByte("relation_flags");
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
                                        relationCqlStatement
                                                .getUpdateRelationFlagsStmt()
                                                .bind(newFlags, userId, blockedUserId))
                                .addStatement(
                                        relationCqlStatement
                                                .getInsertRelationVersionStmt()
                                                .bind(
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
                                    relationCqlStatement
                                            .getInsertRelationStmt()
                                            .bind(
                                                    userId,
                                                    blockedUserId,
                                                    null,
                                                    null,
                                                    null,
                                                    RelationFlags.BLOCKED.getValue()))
                            .addStatement(
                                    relationCqlStatement
                                            .getInsertRelationVersionStmt()
                                            .bind(
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
    public void blockStranger(RelationEvent event)
            throws StrangerHasRelationshipException, NotFoundUserProfileException {
        // Check if relation already exists
        UserProfile stranger = this.getUserProfile(event.getTargetUserId());
        long userId = event.getUserId();
        long blockedUserId = stranger.getUserId();
        Row initiatorRelation =
                session.execute(
                                relationCqlStatement
                                        .getSelectRelationFlagStmt()
                                        .bind(userId, blockedUserId))
                        .one();
        if (initiatorRelation != null) {
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
                                    relationCqlStatement
                                            .getInsertRelationStmt()
                                            .bind(
                                                    userId,
                                                    stranger.getUserId(),
                                                    stranger.getNickName(),
                                                    stranger.getAvatar(),
                                                    stranger.getNickName(),
                                                    RelationFlags.BLOCKED.getValue()))
                            .addStatement(
                                    relationCqlStatement
                                            .getInsertRelationVersionStmt()
                                            .bind(
                                                    userId,
                                                    stranger.getUserId(),
                                                    RelationOperation.BLOCK_STRANGER.getValue(),
                                                    stranger.getNickName(),
                                                    stranger.getAvatar(),
                                                    stranger.getNickName(),
                                                    RelationFlags.BLOCKED.getValue()))
                            .build();
            session.execute(batch);
        }
    }

    @Override
    public void unblockUser(RelationEvent event) throws NotFoundBlockException {
        long userId = event.getUserId();
        long blockedUserId = event.getTargetUserId();
        Row initiatorRelation =
                session.execute(
                                relationCqlStatement
                                        .getSelectRelationFlagStmt()
                                        .bind(userId, blockedUserId))
                        .one();
        if (initiatorRelation != null) {
            byte currentFlags = initiatorRelation.getByte("relation_flags");
            if (!RelationFlags.BLOCKED.isSet(currentFlags)) {
                throw new NotFoundBlockException(
                        "Block not found between " + userId + " and " + blockedUserId);
            }
            byte newFlags = RelationFlags.clearFlag(currentFlags, RelationFlags.BLOCKED);
            BatchStatementBuilder batchBuilder = new BatchStatementBuilder(DefaultBatchType.LOGGED);
            if (newFlags == 0) {
                // stranger, delete relation
                batchBuilder.addStatement(
                        relationCqlStatement.getDeleteRelationStmt().bind(userId, blockedUserId));
                batchBuilder.addStatement(
                        relationCqlStatement
                                .getInsertRelationVersionStmt()
                                .bind(
                                        userId,
                                        blockedUserId,
                                        RelationOperation.DELETE_BLOCK.getValue()));
            } else if (RelationFlags.FRIEND.isSet(newFlags)) {
                // still a friend, update flags
                batchBuilder.addStatement(
                        relationCqlStatement
                                .getUpdateRelationFlagsStmt()
                                .bind(newFlags, userId, blockedUserId));
                batchBuilder.addStatement(
                        relationCqlStatement
                                .getInsertRelationVersionStmt()
                                .bind(
                                        userId,
                                        blockedUserId,
                                        RelationOperation.UNBLOCK_RESTORE_FRIEND.getValue(),
                                        null,
                                        null,
                                        null,
                                        newFlags));
            }
            session.execute(batchBuilder.build());
            return;
        }
        throw new NotFoundBlockException(
                "Block not found between " + userId + " and " + blockedUserId);
    }

    @Override
    public void hideBlockedUser(RelationEvent event) throws NotFoundBlockException {
        long userId = event.getUserId();
        long blockedUserId = event.getTargetUserId();
        Row initiatorRelation =
                session.execute(
                                relationCqlStatement
                                        .getSelectRelationFlagStmt()
                                        .bind(userId, blockedUserId))
                        .one();
        if (initiatorRelation != null) {
            byte currentFlags = initiatorRelation.getByte("relation_flags");
            if (!RelationFlags.BLOCKED.isSet(currentFlags)) {
                throw new NotFoundBlockException(
                        "Block not found between " + userId + " and " + blockedUserId);
            }
            byte newFlags = RelationFlags.setFlag(currentFlags, RelationFlags.HIDDEN);
            // If user is also a friend, clear FRIEND flag when hiding block
            if (RelationFlags.FRIEND.isSet(newFlags)) {
                newFlags = RelationFlags.clearFlag(newFlags, RelationFlags.FRIEND);
            }
            // Update relation_flags to set HIDDEN bit and record version
            BatchStatement batch =
                    new BatchStatementBuilder(DefaultBatchType.LOGGED)
                            .addStatement(
                                    relationCqlStatement
                                            .getUpdateRelationFlagsStmt()
                                            .bind(newFlags, userId, blockedUserId))
                            .addStatement(
                                    relationCqlStatement
                                            .getInsertRelationVersionStmt()
                                            .bind(
                                                    userId,
                                                    blockedUserId,
                                                    RelationOperation.HIDE_BLOCK.getValue(),
                                                    null,
                                                    null,
                                                    null,
                                                    newFlags))
                            .build();
            session.execute(batch);
            return;
        }
        throw new NotFoundBlockException(
                "Block not found between " + userId + " and " + blockedUserId);
    }

    @Override
    public boolean isBlocked(long userId, long targetUserId) {

        return false;
    }

    @Override
    public void syncFriendInfo(long sourceUserId, long targetUserId, String nickName, String avatar)
            throws NotFoundRelationException {
        Relation relation = getRelationBetweenUser(sourceUserId, targetUserId);
        if (relation == null) {
            throw new NotFoundRelationException(
                    "Relation not found between " + sourceUserId + " and " + targetUserId);
        }
        BatchStatementBuilder batchBuilder =
                new BatchStatementBuilder(DefaultBatchType.LOGGED)
                        .addStatement(
                                relationCqlStatement
                                        .getUpdateFriendInfoStmt()
                                        .bind(nickName, avatar, sourceUserId, targetUserId))
                        .addStatement(
                                relationCqlStatement
                                        .getInsertRelationVersionStmt()
                                        .bind(
                                                sourceUserId,
                                                targetUserId,
                                                RelationOperation.UPDATE_FRIEND_INFO.getValue(),
                                                nickName,
                                                avatar,
                                                null,
                                                null));
        String conversationId =
                ConversationUtils.generateConversationId(sourceUserId, targetUserId);
        if (this.existsByConversationId(conversationId, sourceUserId)) {
            batchBuilder.addStatement(
                    conversationCqlStatement
                            .getUpdateAvatarStmt()
                            .bind(avatar, sourceUserId, conversationId));
            batchBuilder.addStatement(
                    conversationCqlStatement
                            .getInsertConversationVersionStmt()
                            .bind(
                                    sourceUserId,
                                    conversationId,
                                    targetUserId,
                                    null,
                                    ConversationOperation.UPDATE_CONVERSATION_NAME_AVATAR
                                            .getValue(),
                                    null,
                                    null,
                                    null,
                                    null,
                                    relation.getRelationRemarkName() == null
                                            ? nickName
                                            : relation.getRelationRemarkName(),
                                    avatar));
        }
        session.execute(batchBuilder.build());
    }

    private void relationExits(long sourceUserId, long targetUserId)
            throws NotFoundRelationException {
        Row relation =
                session.execute(
                                relationCqlStatement
                                        .getSelectRelationFlagStmt()
                                        .bind(sourceUserId, targetUserId))
                        .one();
        if (relation == null) {
            throw new NotFoundRelationException(
                    "Relation not found between " + sourceUserId + " and " + targetUserId);
        }
    }

    private int getUnreadCount(String conversationId, long lastReadMessageId, long receiverId) {
        ResultSet resultSet =
                session.execute(
                        conversationCqlStatement
                                .getSelectConversationUnreadCountStmt()
                                .bind(conversationId, lastReadMessageId));
        int count = 0;
        for (Row row : resultSet) {
            long rowReceiverId = row.getLong("receiver_id");
            if (rowReceiverId == receiverId) {
                count++;
            }
        }
        return count;
    }

    private Row profileExists(long userId) {
        BoundStatement bound = userCqlStatement.getSelectUserIdStmt().bind(userId);
        return session.execute(bound).one();
    }
}
