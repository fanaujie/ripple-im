package com.fanaujie.ripple.storage.service.impl.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.*;
import com.datastax.oss.driver.api.core.data.UdtValue;
import com.datastax.oss.driver.api.core.type.UserDefinedType;
import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.fanaujie.ripple.protobuf.msgapiserver.RelationEvent;
import com.fanaujie.ripple.protobuf.msgapiserver.SingleMessageContent;
import com.fanaujie.ripple.protobuf.storage.UserIds;
import com.fanaujie.ripple.storage.exception.*;
import com.fanaujie.ripple.storage.model.*;
import static com.fanaujie.ripple.storage.model.GroupChangeOperation.*;
import static com.fanaujie.ripple.storage.model.UserGroupOperation.*;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import com.fanaujie.ripple.storage.service.utils.ConversationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

public class CassandraStorageFacade implements RippleStorageFacade {

    private final Logger logger = LoggerFactory.getLogger(CassandraStorageFacade.class);
    private final CqlSession session;
    private final UserCqlStatement userCqlStatement;
    private final RelationCqlStatement relationCqlStatement;
    private final ConversationCqlStatement conversationCqlStatement;
    private final GroupCqlStatement groupCqlStatement;

    public CassandraStorageFacade(CassandraStorageFacadeBuilder builder) {
        this.session = builder.getSession();
        this.userCqlStatement = new UserCqlStatement(this.session);
        this.relationCqlStatement = new RelationCqlStatement(this.session);
        this.conversationCqlStatement = new ConversationCqlStatement(this.session);
        this.groupCqlStatement = new GroupCqlStatement(this.session);
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
        long afterVersionLong;
        try {
            afterVersionLong = Long.parseLong(afterVersion);
        } catch (NumberFormatException e) {
             throw new InvalidVersionException(
                    "Invalid version format: "
                            + afterVersion
                            + ". Version must be a valid long.");
        }

        ResultSet resultSet =
                session.execute(
                        relationCqlStatement
                                .getSelectRelationChangesStmt()
                                .bind(userId, afterVersionLong, limit));

        List<RelationVersionChange> changes = new ArrayList<>();
        for (Row row : resultSet) {
            RelationVersionChange record = new RelationVersionChange();
            record.setVersion(String.valueOf(row.getLong("version")));
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
        return String.valueOf(row.getLong("version"));
    }

    @Override
    public Messages getMessages(String conversationId, long beforeMessageId, int pageSize) {
        // When beforeMessageId is 0, use Long.MAX_VALUE to get the latest messages
        long effectiveBeforeMessageId = (beforeMessageId == 0) ? Long.MAX_VALUE : beforeMessageId;

        ResultSet resultSet =
                session.execute(
                        conversationCqlStatement
                                .getSelectMessagesStmt()
                                .bind(conversationId, effectiveBeforeMessageId, pageSize));

        List<Message> messages = new ArrayList<>();
        for (Row row : resultSet) {
            Message message = new Message();
            message.setConversationId(row.getString("conversation_id"));
            message.setMessageId(row.getLong("message_id"));
            message.setSenderId(row.getLong("sender_id"));
            message.setReceiverId(row.getLong("receiver_id"));
            message.setGroupId(row.getLong("group_id"));
            message.setSendTimestamp(row.getLong("send_timestamp"));
            message.setMessageType(row.getByte("message_type"));
            message.setText(row.getString("text"));
            message.setFileUrl(row.getString("file_url"));
            message.setFileName(row.getString("file_name"));
            message.setCommandType(row.getByte("command_type"));
            message.setCommandData(row.getString("command_data"));
            messages.add(message);
        }
        return new Messages(messages);
    }

    @Override
    public Message getMessages(String conversationId, long messageId) throws NotMessageException {
        ResultSet resultSet =
                session.execute(
                        conversationCqlStatement
                                .getSelectMessageByIdStmt()
                                .bind(conversationId, messageId));

        Row row = resultSet.one();
        if (row == null) {
            throw new NotMessageException(
                    "Message not found for conversationId: "
                            + conversationId
                            + ", messageId: "
                            + messageId);
        }
        Message message = new Message();
        message.setConversationId(row.getString("conversation_id"));
        message.setMessageId(row.getLong("message_id"));
        message.setSenderId(row.getLong("sender_id"));
        message.setReceiverId(row.getLong("receiver_id"));
        message.setGroupId(row.getLong("group_id"));
        message.setSendTimestamp(row.getLong("send_timestamp"));
        message.setMessageType(row.getByte("message_type"));
        message.setText(row.getString("text"));
        message.setFileUrl(row.getString("file_url"));
        message.setFileName(row.getString("file_name"));
        message.setCommandType(row.getByte("command_type"));
        message.setCommandData(row.getString("command_data"));
        return message;
    }

    @Override
    public void markLastReadMessageId(String conversationId, long ownerId, long readMessageId, long version) {
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
                                                version,
                                                conversationId,
                                                null, // peer_id
                                                null, // group_id
                                                ConversationOperation.READ_MESSAGES.getValue(),
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
    public void createSingeMessageConversation(String conversationId, long ownerId, long peerId, long version)
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
                                                null, // last_read_message_id is null initially
                                                name,
                                                avatar))
                        // events
                        .addStatement(
                                conversationCqlStatement
                                        .getInsertConversationVersionStmt()
                                        .bind(
                                                ownerId,
                                                version,
                                                conversationId,
                                                peerId,
                                                null,
                                                ConversationOperation.CREATE_CONVERSATION
                                                        .getValue(),
                                                null, // last_read_message_id is null for create
                                                name,
                                                avatar))
                        // events
                        .build();
        session.execute(senderBatch);
    }

    @Override
    public void saveTextMessage(
            String conversationId,
            long messageId,
            long senderId,
            long receiverId,
            long timestamp,
            String messageText,
            String fileUrl,
            String fileName) {
        session.execute(
                conversationCqlStatement
                        .getInsertMessageStmt()
                        .bind(
                                conversationId,
                                messageId,
                                senderId,
                                receiverId,
                                null,
                                timestamp,
                                MessageType.MESSAGE_TYPE_TEXT.getValue(),
                                messageText,
                                fileUrl,
                                fileName));
    }

    @Override
    public void saveGroupTextMessage(
            String conversationId,
            long messageId,
            long senderId,
            long groupId,
            long timestamp,
            String messageText,
            String fileUrl,
            String fileName) {
        session.execute(
                conversationCqlStatement
                        .getInsertMessageStmt()
                        .bind(
                                conversationId,
                                messageId,
                                senderId,
                                null, // receiverId is null for group messages
                                groupId,
                                timestamp,
                                MessageType.MESSAGE_TYPE_TEXT.getValue(),
                                messageText,
                                fileUrl,
                                fileName));
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
            Conversation conversation = new Conversation();
            conversation.setOwnerId(userId);
            conversation.setConversationId(conversationId);
            conversation.setPeerId(row.getLong("peer_id"));
            conversation.setGroupId(row.getLong("group_id"));
            conversation.setLastReadMessageId(row.getLong("last_read_message_id"));
            conversation.setUnreadCount(0);
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

        long afterVersionLong;
        try {
            afterVersionLong = Long.parseLong(afterVersion);
        } catch (NumberFormatException e) {
            throw new InvalidVersionException(
                    "Invalid version format: "
                            + afterVersion
                            + ". Version must be a valid timestamp.");
        }

        ResultSet resultSet =
                session.execute(
                        conversationCqlStatement
                                .getSelectConversationChangesStmt()
                                .bind(userId, afterVersionLong, limit));

        List<ConversationVersionChange> changes = new ArrayList<>();
        for (Row row : resultSet) {
            ConversationVersionChange record = new ConversationVersionChange();
            record.setVersion(String.valueOf(row.getLong("version")));
            record.setConversationId(row.getString("conversation_id"));
            record.setPeerId(row.getLong("peer_id"));
            record.setGroupId(row.getLong("group_id"));
            record.setOperation(row.getByte("operation"));
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
        return String.valueOf(row.getLong("version"));
    }

    @Override
    public UserIds getFriendIds(long userId) {

        ResultSet resultSet =
                session.execute(this.relationCqlStatement.getSelectAllRelationsStmt().bind(userId));

        List<Long> friendIds = new ArrayList<>();
        for (Row row : resultSet) {
            byte relationFlags = row.getByte("relation_flags");
            if (RelationFlags.FRIEND.isSet(relationFlags)) {
                friendIds.add(row.getLong("relation_user_id"));
            }
        }
        UserIds.Builder b = UserIds.newBuilder();
        b.addAllUserIds(friendIds);
        return b.build();
    }

    @Override
    public void addFriend(RelationEvent event, long version)
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
                                                        version,
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
                                                version,
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
    public void removeFriend(RelationEvent event, long version) throws NotFoundRelationException {
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
                                                version,
                                                friendId,
                                                RelationOperation.DELETE_FRIEND.getValue(),
                                                null,
                                                null,
                                                null,
                                                null))
                        .build();
        session.execute(batch);
    }

    @Override
    public UpdateFriendRemarkNameResult updateFriendRemarkName(RelationEvent event, long version)
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
                                                version,
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
                                    version,
                                    conversationId,
                                    targetUserId,
                                    null,
                                    ConversationOperation.UPDATE_CONVERSATION_NAME.getValue(),
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
    public void updateFriendNickName(long sourceUserId, long targetUserId, String nickName, long version)
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
                                                version,
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
                                    version,
                                    conversationId,
                                    targetUserId,
                                    null,
                                    ConversationOperation.UPDATE_CONVERSATION_NAME.getValue(),
                                    null,
                                    nickName,
                                    null));
        }
        session.execute(batchBuilder.build());
    }

    @Override
    public void updateFriendAvatar(long sourceUserId, long targetUserId, String avatar, long version)
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
                                                version,
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
                                    version,
                                    conversationId,
                                    targetUserId,
                                    null,
                                    ConversationOperation.UPDATE_CONVERSATION_AVATAR.getValue(),
                                    null,
                                    null,
                                    avatar));
        }
        session.execute(batchBuilder.build());
    }

    @Override
    public void blockFriend(RelationEvent event, long version) throws BlockAlreadyExistsException {
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
                                                        version,
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
                                                    version,
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
    public void blockStranger(RelationEvent event, long version)
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
                                                    null,
                                                    RelationFlags.BLOCKED.getValue()))
                            .addStatement(
                                    relationCqlStatement
                                            .getInsertRelationVersionStmt()
                                            .bind(
                                                    userId,
                                                    version,
                                                    stranger.getUserId(),
                                                    RelationOperation.BLOCK_STRANGER.getValue(),
                                                    stranger.getNickName(),
                                                    stranger.getAvatar(),
                                                    null,
                                                    RelationFlags.BLOCKED.getValue()))
                            .build();
            session.execute(batch);
        }
    }

    @Override
    public void unblockUser(RelationEvent event, long version) throws NotFoundBlockException {
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
                                        version,
                                        blockedUserId,
                                        RelationOperation.DELETE_BLOCK.getValue(),
                                        null,
                                        null,
                                        null,
                                        null));
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
                                        version,
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
    public void hideBlockedUser(RelationEvent event, long version) throws NotFoundBlockException {
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
                                                    version,
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
        Relation relation = getRelationBetweenUser(userId, targetUserId);
        if (relation == null) {
            return false;
        }
        return RelationFlags.BLOCKED.isSet(relation.getRelationFlags());
    }

    @Override
    public void syncFriendInfo(long sourceUserId, long targetUserId, String nickName, String avatar, long version)
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
                                                version,
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
                                    version,
                                    conversationId,
                                    targetUserId,
                                    null,
                                    ConversationOperation.UPDATE_CONVERSATION_NAME_AVATAR
                                            .getValue(),
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

    private Row profileExists(long userId) {
        BoundStatement bound = userCqlStatement.getSelectUserIdStmt().bind(userId);
        return session.execute(bound).one();
    }

    @Override
    public void createGroup(long groupId, List<UserProfile> members, long version) {
        BatchStatementBuilder batchBuilder = new BatchStatementBuilder(DefaultBatchType.LOGGED);
        for (UserProfile member : members) {
            BoundStatement memberStmt =
                    groupCqlStatement
                            .getInsertGroupMemberStmt()
                            .bind(
                                    groupId,
                                    member.getUserId(),
                                    member.getNickName(),
                                    member.getAvatar());
            batchBuilder.addStatement(memberStmt);
        }
        List<UdtValue> changesList = new ArrayList<>();
        UserDefinedType changeDetailType = buildGroupChangeDetailType();

        UdtValue createGroupChange =
                changeDetailType
                        .newValue()
                        .setByte("operation", CREATE_GROUP.getValue())
                        .setToNull("user_id")
                        .setToNull("name")
                        .setToNull("avatar");
        changesList.add(createGroupChange);

        for (UserProfile member : members) {
            UdtValue memberChangeDetail =
                    changeDetailType
                            .newValue()
                            .setByte("operation", MEMBER_JOIN.getValue())
                            .setLong("user_id", member.getUserId())
                            .setString("name", member.getNickName())
                            .setString("avatar", member.getAvatar());
            changesList.add(memberChangeDetail);
        }

        BoundStatement versionStmt =
                groupCqlStatement
                        .getInsertGroupMemberVersionStmt()
                        .bind(groupId, version, changesList);
        batchBuilder.addStatement(versionStmt);

        session.execute(batchBuilder.build());
    }

    @Override
    public void createUserGroupAndConversation(
            long userId, long groupId, String groupName, String groupAvatar, long version) {
        String conversationId = ConversationUtils.generateGroupConversationId(groupId);
        if (groupAvatar.isEmpty()) {
            groupAvatar = null;
        }
        BatchStatement batch =
                new BatchStatementBuilder(DefaultBatchType.LOGGED)
                        .addStatement(
                                groupCqlStatement
                                        .getInsertUserGroupStmt()
                                        .bind(userId, groupId, groupName, groupAvatar))
                        .addStatement(
                                groupCqlStatement
                                        .getInsertUserGroupVersionStmt()
                                        .bind(
                                                userId,
                                                version,
                                                groupId,
                                                JOIN.getValue(),
                                                groupName,
                                                groupAvatar))
                        .addStatement(
                                conversationCqlStatement
                                        .getInsertConversationStmt()
                                        .bind(
                                                userId,
                                                conversationId,
                                                null,
                                                groupId,
                                                null,
                                                groupName,
                                                groupAvatar))
                        .addStatement(
                                conversationCqlStatement
                                        .getInsertConversationVersionStmt()
                                        .bind(
                                                userId,
                                                version,
                                                conversationId,
                                                null,
                                                groupId,
                                                ConversationOperation.CREATE_CONVERSATION
                                                        .getValue(),
                                                null,
                                                groupName,
                                                groupAvatar))
                        .build();
        session.execute(batch);
    }

    @Override
    public void createGroupMembersProfile(long groupId, List<UserProfile> members, long version) {
        BatchStatementBuilder batchBuilder = new BatchStatementBuilder(DefaultBatchType.LOGGED);
        for (UserProfile member : members) {
            BoundStatement memberStmt =
                    groupCqlStatement
                            .getInsertGroupMemberStmt()
                            .bind(
                                    groupId,
                                    member.getUserId(),
                                    member.getNickName(),
                                    member.getAvatar());
            batchBuilder.addStatement(memberStmt);
        }
        List<UdtValue> changesList = new ArrayList<>();
        UserDefinedType changeDetailType = buildGroupChangeDetailType();

        for (UserProfile member : members) {
            UdtValue changeDetail =
                    changeDetailType
                            .newValue()
                            .setByte("operation", MEMBER_JOIN.getValue())
                            .setLong("user_id", member.getUserId())
                            .setString("name", member.getNickName())
                            .setString("avatar", member.getAvatar());
            changesList.add(changeDetail);
        }

        BoundStatement versionStmt =
                groupCqlStatement
                        .getInsertGroupMemberVersionStmt()
                        .bind(groupId, version, changesList);
        batchBuilder.addStatement(versionStmt);

        session.execute(batchBuilder.build());
    }

    @Override
    public void updateGroupMemberName(long groupId, long userId, String name, long version) {
        UserDefinedType changeDetailType = buildGroupChangeDetailType();
        UdtValue changeDetail =
                changeDetailType
                        .newValue()
                        .setByte("operation", MEMBER_UPDATE_NAME.getValue())
                        .setLong("user_id", userId)
                        .setString("name", name)
                        .setToNull("avatar");
        List<UdtValue> changesList = Collections.singletonList(changeDetail);

        BatchStatement batch =
                new BatchStatementBuilder(DefaultBatchType.LOGGED)
                        .addStatement(
                                groupCqlStatement
                                        .getUpdateGroupMemberNameStmt()
                                        .bind(name, groupId, userId))
                        .addStatement(
                                groupCqlStatement
                                        .getInsertGroupMemberVersionStmt()
                                        .bind(groupId, version, changesList))
                        .build();
        session.execute(batch);
    }

    @Override
    public void updateGroupMemberAvatar(long groupId, long userId, String avatar, long version) {
        UserDefinedType changeDetailType = buildGroupChangeDetailType();
        UdtValue changeDetail =
                changeDetailType
                        .newValue()
                        .setByte("operation", MEMBER_UPDATE_AVATAR.getValue())
                        .setLong("user_id", userId)
                        .setToNull("name")
                        .setString("avatar", avatar);
        List<UdtValue> changesList = Collections.singletonList(changeDetail);

        BatchStatement batch =
                new BatchStatementBuilder(DefaultBatchType.LOGGED)
                        .addStatement(
                                groupCqlStatement
                                        .getUpdateGroupMemberAvatarStmt()
                                        .bind(avatar, groupId, userId))
                        .addStatement(
                                groupCqlStatement
                                        .getInsertGroupMemberVersionStmt()
                                        .bind(groupId, version, changesList))
                        .build();
        session.execute(batch);
    }

    @Override
    public List<Long> getGroupMemberIds(long groupId) throws NotFoundGroupException {
        BoundStatement bound = groupCqlStatement.getSelectGroupMemberIdsStmt().bind(groupId);
        ResultSet rs = session.execute(bound);
        List<Long> memberIds = new ArrayList<>();
        for (Row row : rs) {
            memberIds.add(row.getLong("user_id"));
        }
        if (memberIds.isEmpty()) {
            throw new NotFoundGroupException("Group not found for groupId: " + groupId);
        }
        return memberIds;
    }

    @Override
    public List<GroupMemberInfo> getGroupMembersInfo(long groupId) throws NotFoundGroupException {
        BoundStatement bound = groupCqlStatement.getSelectGroupMembersStmt().bind(groupId);
        ResultSet rs = session.execute(bound);
        List<GroupMemberInfo> members = new ArrayList<>();
        for (Row row : rs) {
            GroupMemberInfo memberInfo = new GroupMemberInfo();
            memberInfo.setGroupId(groupId);
            memberInfo.setUserId(row.getLong("user_id"));
            memberInfo.setName(row.getString("name"));
            memberInfo.setAvatar(row.getString("avatar"));
            members.add(memberInfo);
        }
        if (members.isEmpty()) {
            throw new NotFoundGroupException(
                    "Group not found or has no members for groupId: " + groupId);
        }
        return members;
    }

    @Override
    public List<Long> getUserGroupIds(long userId) {
        ResultSet resultSet =
                session.execute(groupCqlStatement.getSelectUserGroupIdsStmt().bind(userId));
        List<Long> groupIds = new ArrayList<>();
        for (Row row : resultSet) {
            groupIds.add(row.getLong("group_id"));
        }
        return groupIds;
    }

    @Override
    public List<UserGroup> getUserGroups(long userId) {
        ResultSet resultSet =
                session.execute(groupCqlStatement.getSelectUserGroupsStmt().bind(userId));

        List<UserGroup> groups = new ArrayList<>();
        for (Row row : resultSet) {
            groups.add(
                    new UserGroup(
                            row.getLong("group_id"),
                            row.getString("group_name"),
                            row.getString("group_avatar")));
        }
        return groups;
    }

    @Override
    public PagedGroupMemberResult getGroupMembersPaged(
            long groupId, String nextPageToken, int pageSize) throws NotFoundGroupException {
        // Query pageSize + 1 to check if there are more records
        int limit = pageSize + 1;
        ResultSet resultSet;

        if (nextPageToken == null || nextPageToken.isEmpty()) {
            // First page
            resultSet =
                    session.execute(
                            groupCqlStatement
                                    .getSelectGroupMembersFirstPageStmt()
                                    .bind(groupId, limit));
        } else {
            // Next page, parse token as last user_id
            long lastUserId = Long.parseLong(nextPageToken);
            resultSet =
                    session.execute(
                            groupCqlStatement
                                    .getSelectGroupMembersNextPageStmt()
                                    .bind(groupId, lastUserId, limit));
        }

        // Collect members
        List<GroupMemberInfo> members = new ArrayList<>();
        for (Row row : resultSet) {
            GroupMemberInfo memberInfo = new GroupMemberInfo();
            memberInfo.setGroupId(groupId);
            memberInfo.setUserId(row.getLong("user_id"));
            memberInfo.setName(row.getString("name"));
            memberInfo.setAvatar(row.getString("avatar"));
            members.add(memberInfo);
        }

        // Check if group exists (no members means group not found)
        if (members.isEmpty() && (nextPageToken == null || nextPageToken.isEmpty())) {
            throw new NotFoundGroupException(
                    "Group not found or has no members for groupId: " + groupId);
        }

        // Check if there are more records
        boolean hasMore = members.size() > pageSize;
        String nextToken = null;

        if (hasMore) {
            // Remove the extra record and set next token
            members.remove(members.size() - 1);
            nextToken = String.valueOf(members.get(members.size() - 1).getUserId());
        }

        return new PagedGroupMemberResult(members, nextToken, hasMore);
    }

    @Override
    public PagedUserGroupResult getUserGroupsPaged(
            long userId, String nextPageToken, int pageSize) {
        // Query pageSize + 1 to check if there are more records
        int limit = pageSize + 1;
        ResultSet resultSet;

        if (nextPageToken == null || nextPageToken.isEmpty()) {
            // First page
            resultSet =
                    session.execute(
                            groupCqlStatement
                                    .getSelectUserGroupsFirstPageStmt()
                                    .bind(userId, limit));
        } else {
            // Next page, parse token as last group_id
            long lastGroupId = Long.parseLong(nextPageToken);
            resultSet =
                    session.execute(
                            groupCqlStatement
                                    .getSelectUserGroupsNextPageStmt()
                                    .bind(userId, lastGroupId, limit));
        }

        // Collect groups
        List<UserGroup> groups = new ArrayList<>();
        for (Row row : resultSet) {
            groups.add(
                    new UserGroup(
                            row.getLong("group_id"),
                            row.getString("group_name"),
                            row.getString("group_avatar")));
        }

        // Check if there are more records
        boolean hasMore = groups.size() > pageSize;
        String nextToken = null;

        if (hasMore) {
            // Remove the extra record and set next token
            groups.remove(groups.size() - 1);
            nextToken = String.valueOf(groups.get(groups.size() - 1).getGroupId());
        }

        return new PagedUserGroupResult(groups, nextToken, hasMore);
    }

    @Override
    public String getLatestUserGroupVersion(long userId) {
        Row row =
                session.execute(
                                groupCqlStatement
                                        .getSelectLatestUserGroupVersionStmt()
                                        .bind(userId))
                        .one();
        if (row == null) {
            return null;
        }
        return String.valueOf(row.getLong("version"));
    }

    @Override
    public void removeUserGroup(long userId, long groupId, long version) {
        BatchStatement batch =
                new BatchStatementBuilder(DefaultBatchType.LOGGED)
                        .addStatement(
                                groupCqlStatement
                                        .getInsertUserGroupVersionStmt()
                                        .bind(
                                                userId,
                                                version,
                                                groupId,
                                                QUIT.getValue(),
                                                null,
                                                null))
                        .addStatement(
                                groupCqlStatement.getDeleteUserGroupStmt().bind(userId, groupId))
                        .build();
        session.execute(batch);
    }

    @Override
    public void updateUserGroupName(long userId, long groupId, String groupName, long version) {
        String conversationId = ConversationUtils.generateGroupConversationId(groupId);
        BatchStatement batch =
                new BatchStatementBuilder(DefaultBatchType.LOGGED)
                        // Update user_group table
                        .addStatement(
                                groupCqlStatement
                                        .getUpdateUserGroupNameStmt()
                                        .bind(groupName, userId, groupId))
                        .addStatement(
                                groupCqlStatement
                                        .getInsertUserGroupVersionStmt()
                                        .bind(
                                                userId,
                                                version,
                                                groupId,
                                                UserGroupOperation.UPDATE_GROUP_NAME.getValue(),
                                                groupName,
                                                null))
                        // Update user_conversations table
                        .addStatement(
                                conversationCqlStatement
                                        .getUpdateNameStmt()
                                        .bind(groupName, userId, conversationId))
                        .addStatement(
                                conversationCqlStatement
                                        .getInsertConversationVersionStmt()
                                        .bind(
                                                userId,
                                                version,
                                                conversationId,
                                                null, // peer_id
                                                groupId,
                                                ConversationOperation.UPDATE_CONVERSATION_NAME
                                                        .getValue(),
                                                null, // last_read_message_id
                                                groupName,
                                                null)) // avatar
                        .build();
        session.execute(batch);
    }

    @Override
    public void updateUserGroupAvatar(long userId, long groupId, String groupAvatar, long version) {
        String conversationId = ConversationUtils.generateGroupConversationId(groupId);
        BatchStatement batch =
                new BatchStatementBuilder(DefaultBatchType.LOGGED)
                        // Update user_group table
                        .addStatement(
                                groupCqlStatement
                                        .getUpdateUserGroupAvatarStmt()
                                        .bind(groupAvatar, userId, groupId))
                        .addStatement(
                                groupCqlStatement
                                        .getInsertUserGroupVersionStmt()
                                        .bind(
                                                userId,
                                                version,
                                                groupId,
                                                UserGroupOperation.UPDATE_GROUP_AVATAR.getValue(),
                                                null,
                                                groupAvatar))
                        // Update user_conversations table
                        .addStatement(
                                conversationCqlStatement
                                        .getUpdateAvatarStmt()
                                        .bind(groupAvatar, userId, conversationId))
                        .addStatement(
                                conversationCqlStatement
                                        .getInsertConversationVersionStmt()
                                        .bind(
                                                userId,
                                                version,
                                                conversationId,
                                                null, // peer_id
                                                groupId,
                                                ConversationOperation.UPDATE_CONVERSATION_AVATAR
                                                        .getValue(),
                                                null, // last_read_message_id
                                                null, // name
                                                groupAvatar))
                        .build();
        session.execute(batch);
    }

    @Override
    public List<UserGroupVersionChange> getUserGroupChanges(
            long userId, String afterVersion, int limit) throws InvalidVersionException {
        long afterVersionLong;
        try {
            afterVersionLong = afterVersion == null ? 0L : Long.parseLong(afterVersion);
        } catch (NumberFormatException e) {
            throw new InvalidVersionException("Invalid version format: " + afterVersion);
        }
        BoundStatement bound =
                groupCqlStatement
                        .getSelectUserGroupChangesStmt()
                        .bind(userId, afterVersionLong, limit);
        ResultSet rs = session.execute(bound);

        List<UserGroupVersionChange> changes = new ArrayList<>();
        for (Row row : rs) {
            UserGroupVersionChange change = new UserGroupVersionChange();
            change.setGroupId(row.getLong("group_id"));
            change.setOperation(row.getByte("operation"));
            change.setGroupName(row.getString("group_name"));
            change.setGroupAvatar(row.getString("group_avatar"));
            change.setVersion(String.valueOf(row.getLong("version")));
            changes.add(change);
        }
        return changes;
    }

    @Override
    public void removeGroupMember(long groupId, long userId, long version) {
        UserDefinedType changeDetailType = buildGroupChangeDetailType();

        UdtValue changeDetail =
                changeDetailType
                        .newValue()
                        .setByte("operation", MEMBER_QUIT.getValue())
                        .setLong("user_id", userId)
                        .setToNull("name")
                        .setToNull("avatar");
        List<UdtValue> changesList = Collections.singletonList(changeDetail);

        BoundStatement deleteFromMembers =
                groupCqlStatement.getDeleteGroupMemberStmt().bind(groupId, userId);
        BoundStatement insertVersion =
                groupCqlStatement
                        .getInsertGroupMemberVersionStmt()
                        .bind(groupId, version, changesList);

        BatchStatement batch =
                BatchStatement.newInstance(BatchType.LOGGED)
                        .add(deleteFromMembers)
                        .add(insertVersion);
        session.execute(batch);
    }

    @Override
    public void removeGroupConversation(long userId, long groupId, long version) {
        String conversationId = ConversationUtils.generateGroupConversationId(groupId);
        BatchStatement batch =
                new BatchStatementBuilder(DefaultBatchType.LOGGED)
                        .addStatement(
                                conversationCqlStatement
                                        .getDeleteConversationStmt()
                                        .bind(userId, conversationId))
                        .addStatement(
                                conversationCqlStatement
                                        .getInsertConversationVersionStmt()
                                        .bind(
                                                userId,
                                                version,
                                                conversationId,
                                                null, // peer_id is null for group conversation
                                                groupId,
                                                ConversationOperation.REMOVE_CONVERSATION
                                                        .getValue(),
                                                null, // last_read_message_id
                                                null, // name
                                                null)) // avatar
                        .build();
        session.execute(batch);
    }

    @Override
    public void saveGroupCommandMessage(
            String conversationId,
            long messageId,
            long senderId,
            long groupId,
            long timestamp,
            byte commandType,
            String commandData) {
        session.execute(
                conversationCqlStatement
                        .getInsertGroupCommandMessageStmt()
                        .bind(
                                conversationId,
                                messageId,
                                senderId,
                                groupId,
                                timestamp,
                                MessageType.MESSAGE_TYPE_GROUP_COMMAND.getValue(),
                                commandType,
                                commandData));
    }

    @Override
    public List<GroupVersionChange> getGroupChanges(long groupId, String afterVersion, int limit)
            throws InvalidVersionException {
        long afterVersionLong;
        try {
            afterVersionLong = afterVersion == null ? 0L : Long.parseLong(afterVersion);
        } catch (NumberFormatException e) {
            throw new InvalidVersionException("Invalid version format: " + afterVersion);
        }

        BoundStatement bound =
                groupCqlStatement
                        .getSelectGroupChangesStmt()
                        .bind(groupId, afterVersionLong, limit);
        ResultSet rs = session.execute(bound);

        List<GroupVersionChange> changes = new ArrayList<>();
        for (Row row : rs) {
            GroupVersionChange change = new GroupVersionChange();
            change.setGroupId(groupId);
            change.setVersion(String.valueOf(row.getLong("version")));
            List<UdtValue> changesFromRow = row.getList("changes", UdtValue.class);
            List<ChangeDetail> changeDetailsList = new ArrayList<>();
            if (changesFromRow != null) {
                for (UdtValue udtValue : changesFromRow) {
                    ChangeDetail detail = new ChangeDetail();
                    detail.setOperation(udtValue.getByte("operation"));
                    detail.setUserId(udtValue.getLong("user_id"));
                    detail.setName(udtValue.getString("name"));
                    detail.setAvatar(udtValue.getString("avatar"));
                    changeDetailsList.add(detail);
                }
            }

            change.setChanges(changeDetailsList);
            changes.add(change);
        }

        return changes;
    }

    @Override
    public String getLatestGroupVersion(long groupId) {
        BoundStatement bound = groupCqlStatement.getSelectLatestGroupVersionStmt().bind(groupId);
        Row row = session.execute(bound).one();
        if (row != null) {
            long version = row.getLong("version");
            return String.valueOf(version);
        }
        return null;
    }

    private UserDefinedType buildGroupChangeDetailType() {
        return session.getMetadata()
                .getKeyspace("ripple")
                .flatMap(ks -> ks.getUserDefinedType("group_change_detail"))
                .orElseThrow(() -> new IllegalStateException("UDT group_change_detail not found"));
    }
}
