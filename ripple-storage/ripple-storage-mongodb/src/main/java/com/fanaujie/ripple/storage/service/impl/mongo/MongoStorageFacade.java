package com.fanaujie.ripple.storage.service.impl.mongo;

import com.fanaujie.ripple.protobuf.msgapiserver.RelationEvent;
import com.fanaujie.ripple.protobuf.storage.UserIds;
import com.fanaujie.ripple.storage.exception.*;
import com.fanaujie.ripple.storage.model.*;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import com.fanaujie.ripple.storage.service.utils.ConversationUtils;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class MongoStorageFacade implements RippleStorageFacade {

    private final Logger logger = LoggerFactory.getLogger(MongoStorageFacade.class);
    private final MongoClient mongoClient;
    private final MongoDatabase database;

    // Collections
    private final MongoCollection<Document> usersCollection;
    private final MongoCollection<Document> userProfilesCollection;
    private final MongoCollection<Document> userRelationsCollection;
    private final MongoCollection<Document> userRelationVersionsCollection;
    private final MongoCollection<Document> userConversationsCollection;
    private final MongoCollection<Document> userConversationsVersionsCollection;
    private final MongoCollection<Document> userMessagesCollection;
    private final MongoCollection<Document> groupMembersCollection;
    private final MongoCollection<Document> groupMembersVersionsCollection;
    private final MongoCollection<Document> userGroupsCollection;
    private final MongoCollection<Document> userGroupVersionsCollection;
    private final MongoCollection<Document> userBlockedByCollection;


    public MongoStorageFacade(MongoStorageFacadeBuilder builder) {
        this.mongoClient = builder.getMongoClient();
        this.database = builder.getDatabase();

        this.usersCollection = database.getCollection("users");
        this.userProfilesCollection = database.getCollection("user_profiles");
        this.userRelationsCollection = database.getCollection("user_relations");
        this.userRelationVersionsCollection = database.getCollection("user_relation_versions");
        this.userConversationsCollection = database.getCollection("user_conversations");
        this.userConversationsVersionsCollection = database.getCollection("user_conversations_versions");
        this.userMessagesCollection = database.getCollection("user_messages");
        this.groupMembersCollection = database.getCollection("group_members");
        this.groupMembersVersionsCollection = database.getCollection("group_members_versions");
        this.userGroupsCollection = database.getCollection("user_groups");
        this.userGroupVersionsCollection = database.getCollection("user_group_versions");
        this.userBlockedByCollection = database.getCollection("user_blocked_by");
    }

    @Override
    public User findByAccount(String account) {
        Document doc = usersCollection.find(Filters.eq("_id", account)).first();
        if (doc == null) {
            return null;
        }
        return new User(
                doc.getLong("user_id"),
                doc.getString("_id"), // account is _id
                doc.getString("password"),
                doc.getString("role"),
                doc.getInteger("status").byteValue()
        );
    }

    @Override
    public boolean userExists(String account) {
        return usersCollection.countDocuments(Filters.eq("_id", account)) > 0;
    }

    @Override
    public void insertUser(User user, String displayName, String avatar) {
        ClientSession session = mongoClient.startSession();
        try {
            session.startTransaction();

            Document userDoc = new Document("_id", user.getAccount())
                    .append("user_id", user.getUserId())
                    .append("password", user.getPassword())
                    .append("role", user.getRole())
                    .append("status", (int) user.getStatus());

            usersCollection.insertOne(session, userDoc);

            Document profileDoc = new Document("_id", user.getUserId())
                    .append("account", user.getAccount())
                    .append("nick_name", displayName)
                    .append("avatar", avatar);

            userProfilesCollection.insertOne(session, profileDoc);

            session.commitTransaction();
        } catch (Exception e) {
            session.abortTransaction();
            throw e;
        } finally {
            session.close();
        }
    }

    @Override
    public UserProfile getUserProfile(long userId) throws NotFoundUserProfileException {
        Document doc = userProfilesCollection.find(Filters.eq("_id", userId)).first();
        if (doc == null) {
            throw new NotFoundUserProfileException("User profile not found for userId: " + userId);
        }
        return new UserProfile(
                doc.getLong("_id"),
                doc.getString("account"),
                doc.getString("nick_name"),
                doc.getString("avatar")
        );
    }

    @Override
    public void updateProfileNickNameByUserId(long userId, String nickName) throws NotFoundUserProfileException {
        Document doc = userProfilesCollection.findOneAndUpdate(
                Filters.eq("_id", userId),
                Updates.set("nick_name", nickName)
        );
        if (doc == null) {
            throw new NotFoundUserProfileException("User profile not found for userId: " + userId);
        }
    }

    @Override
    public void updateProfileAvatarByUserId(long userId, String avatar) throws NotFoundUserProfileException {
        Document doc = userProfilesCollection.findOneAndUpdate(
                Filters.eq("_id", userId),
                Updates.set("avatar", avatar)
        );
        if (doc == null) {
            throw new NotFoundUserProfileException("User profile not found for userId: " + userId);
        }
    }

    // --- Placeholder implementations for other methods to ensure compilation ---

    @Override
    public PagedRelationResult getRelations(long sourceUserId, String nextPageToken, int pageSize) {
        int limit = pageSize + 1;
        List<Document> docs = new ArrayList<>();

        Bson filter;
        if (nextPageToken == null || nextPageToken.isEmpty()) {
            filter = Filters.eq("user_id", sourceUserId);
        } else {
            long lastRelationUserId = Long.parseLong(nextPageToken);
            filter = Filters.and(
                    Filters.eq("user_id", sourceUserId),
                    Filters.gt("relation_user_id", lastRelationUserId)
            );
        }

        userRelationsCollection.find(filter)
                .sort(Sorts.ascending("relation_user_id"))
                .limit(limit)
                .into(docs);

        List<Relation> relations = new ArrayList<>();
        for (Document doc : docs) {
            Relation relation = new Relation();
            relation.setSourceUserId(sourceUserId);
            relation.setRelationUserId(doc.getLong("relation_user_id"));
            relation.setRelationNickName(doc.getString("nick_name"));
            relation.setRelationAvatar(doc.getString("avatar"));
            relation.setRelationRemarkName(doc.getString("remark_name"));
            relation.setRelationFlags(doc.getInteger("relation_flags").byteValue());
            relations.add(relation);
        }

        boolean hasMore = relations.size() > pageSize;
        String nextToken = null;

        if (hasMore) {
            relations.remove(relations.size() - 1);
            nextToken = String.valueOf(relations.get(relations.size() - 1).getRelationUserId());
        }

        return new PagedRelationResult(relations, nextToken, hasMore);
    }

    @Override
    public Relation getRelationBetweenUser(long userId, long targetUserId) {
        Document doc = userRelationsCollection.find(
                Filters.and(
                        Filters.eq("user_id", userId),
                        Filters.eq("relation_user_id", targetUserId)
                )
        ).first();

        if (doc == null) {
            return null;
        }

        Relation relation = new Relation();
        relation.setSourceUserId(doc.getLong("user_id"));
        relation.setRelationUserId(doc.getLong("relation_user_id"));
        relation.setRelationNickName(doc.getString("nick_name"));
        relation.setRelationAvatar(doc.getString("avatar"));
        relation.setRelationRemarkName(doc.getString("remark_name"));
        relation.setRelationFlags(doc.getInteger("relation_flags").byteValue());
        return relation;
    }

    @Override
    public List<RelationVersionChange> getRelationChanges(long userId, String afterVersion, int limit) throws InvalidVersionException {
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

        List<Document> docs = new ArrayList<>();
        userRelationVersionsCollection.find(
                Filters.and(
                        Filters.eq("user_id", userId),
                        Filters.gt("version", afterVersionLong)
                )
        )
        .sort(Sorts.ascending("version"))
        .limit(limit)
        .into(docs);

        List<RelationVersionChange> changes = new ArrayList<>();
        for (Document doc : docs) {
            RelationVersionChange record = new RelationVersionChange();
            record.setVersion(String.valueOf(doc.getLong("version")));
            record.setRelationUserId(doc.getLong("relation_user_id"));
            record.setOperation(doc.getInteger("operation").byteValue());
            record.setNickName(doc.getString("nick_name"));
            record.setAvatar(doc.getString("avatar"));
            record.setRemarkName(doc.getString("remark_name"));

            Integer flags = doc.getInteger("relation_flags");
            if (flags != null) {
                record.setRelationFlags(flags.byteValue());
            }

            changes.add(record);
        }
        return changes;
    }

    @Override
    public String getLatestRelationVersion(long userId) {
        Document doc = userRelationVersionsCollection.find(Filters.eq("user_id", userId))
                .sort(Sorts.descending("version"))
                .limit(1)
                .first();

        if (doc == null) {
            return null;
        }
        return String.valueOf(doc.getLong("version"));
    }

    @Override
    public Messages getMessages(String conversationId, long beforeMessageId, int pageSize) {
        long effectiveBeforeMessageId = (beforeMessageId == 0) ? Long.MAX_VALUE : beforeMessageId;

        List<Document> docs = new ArrayList<>();
        userMessagesCollection.find(
                Filters.and(
                        Filters.eq("conversation_id", conversationId),
                        Filters.lt("message_id", effectiveBeforeMessageId)
                )
        )
        .sort(Sorts.descending("message_id"))
        .limit(pageSize)
        .into(docs);

        List<Message> messages = new ArrayList<>();
        for (Document doc : docs) {
            Message message = new Message();
            message.setConversationId(doc.getString("conversation_id"));
            message.setMessageId(doc.getLong("message_id"));
            message.setSenderId(doc.getLong("sender_id"));

            Long receiverId = doc.getLong("receiver_id");
            if (receiverId != null) message.setReceiverId(receiverId);

            Long groupId = doc.getLong("group_id");
            if (groupId != null) message.setGroupId(groupId);

            message.setSendTimestamp(doc.getLong("send_timestamp"));
            message.setMessageType(doc.getInteger("message_type").byteValue());
            message.setText(doc.getString("text"));
            message.setFileUrl(doc.getString("file_url"));
            message.setFileName(doc.getString("file_name"));

            Integer commandType = doc.getInteger("command_type");
            if (commandType != null) message.setCommandType(commandType.byteValue());

            message.setCommandData(doc.getString("command_data"));
            messages.add(message);
        }
        return new Messages(messages);
    }

    @Override
    public Message getMessages(String conversationId, long messageId) throws NotMessageException {
        Document doc = userMessagesCollection.find(
                Filters.and(
                        Filters.eq("conversation_id", conversationId),
                        Filters.eq("message_id", messageId)
                )
        ).first();

        if (doc == null) {
            throw new NotMessageException(
                    "Message not found for conversationId: "
                            + conversationId
                            + ", messageId: "
                            + messageId);
        }
        
        Message message = new Message();
        message.setConversationId(doc.getString("conversation_id"));
        message.setMessageId(doc.getLong("message_id"));
        message.setSenderId(doc.getLong("sender_id"));

        Long receiverId = doc.getLong("receiver_id");
        if (receiverId != null) message.setReceiverId(receiverId);

        Long groupId = doc.getLong("group_id");
        if (groupId != null) message.setGroupId(groupId);

        message.setSendTimestamp(doc.getLong("send_timestamp"));
        message.setMessageType(doc.getInteger("message_type").byteValue());
        message.setText(doc.getString("text"));
        message.setFileUrl(doc.getString("file_url"));
        message.setFileName(doc.getString("file_name"));

        Integer commandType = doc.getInteger("command_type");
        if (commandType != null) message.setCommandType(commandType.byteValue());

        message.setCommandData(doc.getString("command_data"));
        return message;
    }

    @Override
    public void markLastReadMessageId(String conversationId, long ownerId, long readMessageId, long version) {
        ClientSession session = mongoClient.startSession();
        try {
            session.startTransaction();

            userConversationsCollection.updateOne(session,
                    Filters.and(
                            Filters.eq("user_id", ownerId),
                            Filters.eq("conversation_id", conversationId)
                    ),
                    Updates.set("last_read_message_id", readMessageId)
            );

            Document versionDoc = new Document("user_id", ownerId)
                    .append("version", version)
                    .append("conversation_id", conversationId)
                    .append("operation", (int) ConversationOperation.READ_MESSAGES.getValue())
                    .append("last_read_message_id", readMessageId);

            userConversationsVersionsCollection.insertOne(session, versionDoc);

            session.commitTransaction();
        } catch (Exception e) {
            session.abortTransaction();
            throw e;
        } finally {
            session.close();
        }
    }

    @Override
    public boolean existsByConversationId(String conversationId, long ownerId) {
        return userConversationsCollection.countDocuments(
                Filters.and(
                        Filters.eq("user_id", ownerId),
                        Filters.eq("conversation_id", conversationId)
                )
        ) > 0;
    }

    @Override
    public void createSingeMessageConversation(String conversationId, long ownerId, long peerId, long version) throws NotFoundUserProfileException {
        String name = null;
        String avatar = null;
        Relation relation = getRelationBetweenUser(ownerId, peerId);
        if (relation == null) {
            UserProfile peerProfile = getUserProfile(peerId);
            name = peerProfile.getNickName();
            avatar = peerProfile.getAvatar();
        } else {
            name = relation.getRelationRemarkName() == null
                    ? relation.getRelationNickName()
                    : relation.getRelationRemarkName();
            avatar = relation.getRelationAvatar();
        }

        ClientSession session = mongoClient.startSession();
        try {
            session.startTransaction();

            Document convDoc = new Document("user_id", ownerId)
                    .append("conversation_id", conversationId)
                    .append("peer_id", peerId)
                    .append("name", name)
                    .append("avatar", avatar)
                    .append("unread_count", 0)
                    .append("last_read_message_id", 0L);

            userConversationsCollection.insertOne(session, convDoc);

            Document versionDoc = new Document("user_id", ownerId)
                    .append("version", version)
                    .append("conversation_id", conversationId)
                    .append("peer_id", peerId)
                    .append("operation", (int) ConversationOperation.CREATE_CONVERSATION.getValue())
                    .append("name", name)
                    .append("avatar", avatar);

            userConversationsVersionsCollection.insertOne(session, versionDoc);

            session.commitTransaction();
        } catch (Exception e) {
            session.abortTransaction();
            throw e;
        } finally {
            session.close();
        }
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

        Document doc = new Document("conversation_id", conversationId)
                .append("message_id", messageId)
                .append("sender_id", senderId)
                .append("receiver_id", receiverId)
                .append("send_timestamp", timestamp)
                .append("message_type", (int) MessageType.MESSAGE_TYPE_TEXT.getValue())
                .append("text", messageText)
                .append("file_url", fileUrl)
                .append("file_name", fileName);

        userMessagesCollection.insertOne(doc);
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

        Document doc = new Document("conversation_id", conversationId)
                .append("message_id", messageId)
                .append("sender_id", senderId)
                .append("group_id", groupId)
                .append("send_timestamp", timestamp)
                .append("message_type", (int) MessageType.MESSAGE_TYPE_TEXT.getValue())
                .append("text", messageText)
                .append("file_url", fileUrl)
                .append("file_name", fileName);

        userMessagesCollection.insertOne(doc);
    }

    @Override
    public PagedConversationResult getConversations(long userId, String nextPageToken, int pageSize) {
        int limit = pageSize + 1;
        List<Document> docs = new ArrayList<>();

        Bson filter;
        if (nextPageToken == null || nextPageToken.isEmpty()) {
            filter = Filters.eq("user_id", userId);
        } else {
            filter = Filters.and(
                    Filters.eq("user_id", userId),
                    Filters.gt("conversation_id", nextPageToken)
            );
        }

        userConversationsCollection.find(filter)
                .sort(Sorts.ascending("conversation_id"))
                .limit(limit)
                .into(docs);

        List<Conversation> conversations = new ArrayList<>();
        for (Document doc : docs) {
            Conversation conversation = new Conversation();
            conversation.setOwnerId(userId);
            conversation.setConversationId(doc.getString("conversation_id"));

            Long peerId = doc.getLong("peer_id");
            if (peerId != null) conversation.setPeerId(peerId);

            Long groupId = doc.getLong("group_id");
            if (groupId != null) conversation.setGroupId(groupId);

            conversation.setLastReadMessageId(doc.getLong("last_read_message_id"));
            conversation.setUnreadCount(0);
            conversation.setName(doc.getString("name"));
            conversation.setAvatar(doc.getString("avatar"));
            conversations.add(conversation);
        }

        boolean hasMore = conversations.size() > pageSize;
        String nextToken = null;

        if (hasMore) {
            conversations.remove(conversations.size() - 1);
            nextToken = conversations.get(conversations.size() - 1).getConversationId();
        }

        return new PagedConversationResult(conversations, nextToken, hasMore);
    }

    @Override
    public List<ConversationVersionChange> getConversationChanges(long userId, String afterVersion, int limit) throws InvalidVersionException {
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

        List<Document> docs = new ArrayList<>();
        userConversationsVersionsCollection.find(
                Filters.and(
                        Filters.eq("user_id", userId),
                        Filters.gt("version", afterVersionLong)
                )
        )
        .sort(Sorts.ascending("version"))
        .limit(limit)
        .into(docs);

        List<ConversationVersionChange> changes = new ArrayList<>();
        for (Document doc : docs) {
            ConversationVersionChange record = new ConversationVersionChange();
            record.setVersion(String.valueOf(doc.getLong("version")));
            record.setConversationId(doc.getString("conversation_id"));

            Long peerId = doc.getLong("peer_id");
            if (peerId != null) record.setPeerId(peerId);

            Long groupId = doc.getLong("group_id");
            if (groupId != null) record.setGroupId(groupId);

            record.setOperation(doc.getInteger("operation").byteValue());

            Long lastReadMsgId = doc.getLong("last_read_message_id");
            if (lastReadMsgId != null) record.setLastReadMessageId(lastReadMsgId);

            record.setName(doc.getString("name"));
            record.setAvatar(doc.getString("avatar"));
            changes.add(record);
        }
        return changes;
    }

    @Override
    public String getLatestConversationVersion(long userId) {
        Document doc = userConversationsVersionsCollection.find(Filters.eq("user_id", userId))
                .sort(Sorts.descending("version"))
                .limit(1)
                .first();

        if (doc == null) {
            return null;
        }
        return String.valueOf(doc.getLong("version"));
    }

    @Override
    public UserIds getFriendIds(long userId) {
        List<Long> friendIds = new ArrayList<>();
        userRelationsCollection.find(Filters.eq("user_id", userId))
                .forEach(doc -> {
                    int flags = doc.getInteger("relation_flags");
                    if (RelationFlags.FRIEND.isSet((byte) flags)) {
                        friendIds.add(doc.getLong("relation_user_id"));
                    }
                });

        return UserIds.newBuilder().addAllUserIds(friendIds).build();
    }

    @Override
    public void addFriend(RelationEvent event, long version) throws NotFoundUserProfileException, RelationAlreadyExistsException {
        // Check if relation already exists
        UserProfile friendProfile = this.getUserProfile(event.getTargetUserId());
        long initiatorId = event.getUserId();
        long targetUserId = friendProfile.getUserId();

        ClientSession session = mongoClient.startSession();
        try {
            session.startTransaction();

            Document initiatorRelation = userRelationsCollection.find(session,
                    Filters.and(
                            Filters.eq("user_id", initiatorId),
                            Filters.eq("relation_user_id", targetUserId)
                    )).first();

            if (initiatorRelation != null) {
                int currentFlagsInt = initiatorRelation.getInteger("relation_flags");
                byte currentFlags = (byte) currentFlagsInt;

                // If already a friend, throw exception
                if (RelationFlags.FRIEND.isSet(currentFlags)) {
                    throw new RelationAlreadyExistsException(
                            "Friend relation already exists between "
                                    + initiatorId
                                    + " and "
                                    + targetUserId);
                }

                // If blocked and hidden, reset flags to FRIEND (unblock and unhide)
                if (RelationFlags.BLOCKED.isSet(currentFlags)
                        && RelationFlags.HIDDEN.isSet(currentFlags)) {

                    userRelationsCollection.updateOne(session,
                            Filters.and(
                                    Filters.eq("user_id", initiatorId),
                                    Filters.eq("relation_user_id", targetUserId)
                            ),
                            Updates.set("relation_flags", (int) RelationFlags.FRIEND.getValue())
                    );

                    Document versionDoc = new Document("user_id", initiatorId)
                            .append("version", version)
                            .append("relation_user_id", targetUserId)
                            .append("operation", (int) RelationOperation.ADD_FRIEND.getValue())
                            .append("nick_name", friendProfile.getNickName())
                            .append("avatar", friendProfile.getAvatar())
                            .append("remark_name", null)
                            .append("relation_flags", (int) RelationFlags.FRIEND.getValue());

                    userRelationVersionsCollection.insertOne(session, versionDoc);

                    session.commitTransaction();
                    return;
                }
            }

            // Insert new relation
            Document newRelationDoc = new Document("user_id", initiatorId)
                    .append("relation_user_id", targetUserId)
                    .append("nick_name", friendProfile.getNickName())
                    .append("avatar", friendProfile.getAvatar())
                    .append("remark_name", null)
                    .append("relation_flags", (int) RelationFlags.FRIEND.getValue());

            userRelationsCollection.insertOne(session, newRelationDoc);

            Document versionDoc = new Document("user_id", initiatorId)
                    .append("version", version)
                    .append("relation_user_id", targetUserId)
                    .append("operation", (int) RelationOperation.ADD_FRIEND.getValue())
                    .append("nick_name", friendProfile.getNickName())
                    .append("avatar", friendProfile.getAvatar())
                    .append("remark_name", null)
                    .append("relation_flags", (int) RelationFlags.FRIEND.getValue());

            userRelationVersionsCollection.insertOne(session, versionDoc);

            session.commitTransaction();

        } catch (RelationAlreadyExistsException e) {
            session.abortTransaction();
            throw e;
        } catch (Exception e) {
            session.abortTransaction();
            throw new RuntimeException("Failed to add friend", e);
        } finally {
            session.close();
        }
    }

    @Override
    public void removeFriend(RelationEvent event, long version) throws NotFoundRelationException {
        long initiatorId = event.getUserId();
        long friendId = event.getTargetUserId();

        ClientSession session = mongoClient.startSession();
        try {
            session.startTransaction();

            Document relation = userRelationsCollection.find(session,
                    Filters.and(
                            Filters.eq("user_id", initiatorId),
                            Filters.eq("relation_user_id", friendId)
                    )).first();

            if (relation == null) {
                throw new NotFoundRelationException(
                        "Friend relation not found between " + initiatorId + " and " + friendId);
            }

            int currentFlagsInt = relation.getInteger("relation_flags");
            byte currentFlags = (byte) currentFlagsInt;

            if (!RelationFlags.FRIEND.isSet(currentFlags)) {
                throw new NotFoundRelationException(
                        "Friend relation not found between " + initiatorId + " and " + friendId);
            }

            // Delete relation
            userRelationsCollection.deleteOne(session,
                    Filters.and(
                            Filters.eq("user_id", initiatorId),
                            Filters.eq("relation_user_id", friendId)
                    ));

            // Record version
            Document versionDoc = new Document("user_id", initiatorId)
                    .append("version", version)
                    .append("relation_user_id", friendId)
                    .append("operation", (int) RelationOperation.DELETE_FRIEND.getValue())
                    .append("nick_name", null)
                    .append("avatar", null)
                    .append("remark_name", null)
                    .append("relation_flags", null);

            userRelationVersionsCollection.insertOne(session, versionDoc);

            session.commitTransaction();
        } catch (Exception e) {
            session.abortTransaction();
            throw e;
        } finally {
            session.close();
        }
    }

    @Override
    public UpdateFriendRemarkNameResult updateFriendRemarkName(RelationEvent event, long version) throws NotFoundRelationException {
        long sourceUserId = event.getUserId();
        long targetUserId = event.getTargetUserId();
        String remarkName = event.getTargetUserRemarkName();

        UpdateFriendRemarkNameResult result = new UpdateFriendRemarkNameResult();

        ClientSession session = mongoClient.startSession();
        try {
            session.startTransaction();

            // Check relation exists
            Document relation = userRelationsCollection.find(session,
                    Filters.and(
                            Filters.eq("user_id", sourceUserId),
                            Filters.eq("relation_user_id", targetUserId)
                    )).first();

            if (relation == null) {
                throw new NotFoundRelationException(
                        "Relation not found between " + sourceUserId + " and " + targetUserId);
            }

            // Update relation remark name
            userRelationsCollection.updateOne(session,
                    Filters.and(
                            Filters.eq("user_id", sourceUserId),
                            Filters.eq("relation_user_id", targetUserId)
                    ),
                    Updates.set("remark_name", remarkName)
            );

            // Insert version
            Document versionDoc = new Document("user_id", sourceUserId)
                    .append("version", version)
                    .append("relation_user_id", targetUserId)
                    .append("operation", (int) RelationOperation.UPDATE_FRIEND_REMARK_NAME.getValue())
                    .append("remark_name", remarkName);
            userRelationVersionsCollection.insertOne(session, versionDoc);

            // Synchronize with conversation
            String conversationId = ConversationUtils.generateConversationId(sourceUserId, targetUserId);
            Document convDoc = userConversationsCollection.find(session,
                    Filters.and(
                            Filters.eq("user_id", sourceUserId),
                            Filters.eq("conversation_id", conversationId)
                    )).first();

            if (convDoc != null) {
                userConversationsCollection.updateOne(session,
                        Filters.and(
                                Filters.eq("user_id", sourceUserId),
                                Filters.eq("conversation_id", conversationId)
                        ),
                        Updates.set("name", remarkName)
                );

                Document convVersionDoc = new Document("user_id", sourceUserId)
                        .append("version", version)
                        .append("conversation_id", conversationId)
                        .append("peer_id", targetUserId)
                        .append("operation", (int) ConversationOperation.UPDATE_CONVERSATION_NAME.getValue())
                        .append("name", remarkName);
                userConversationsVersionsCollection.insertOne(session, convVersionDoc);

                result.setConversationUpdated(true);
                result.setConversationId(conversationId);
            }

            session.commitTransaction();
        } catch (Exception e) {
            session.abortTransaction();
            throw e;
        } finally {
            session.close();
        }
        return result;
    }

    @Override
    public void updateFriendNickName(long sourceUserId, long targetUserId, String nickName, long version) throws NotFoundRelationException {
        ClientSession session = mongoClient.startSession();
        try {
            session.startTransaction();

            // Check relation exists
            Document relation = userRelationsCollection.find(session,
                    Filters.and(
                            Filters.eq("user_id", sourceUserId),
                            Filters.eq("relation_user_id", targetUserId)
                    )).first();

            if (relation == null) {
                throw new NotFoundRelationException(
                        "Friend relation not found between " + sourceUserId + " and " + targetUserId);
            }

            // Update nick name
            userRelationsCollection.updateOne(session,
                    Filters.and(
                            Filters.eq("user_id", sourceUserId),
                            Filters.eq("relation_user_id", targetUserId)
                    ),
                    Updates.set("nick_name", nickName)
            );

            // Insert version
            Document versionDoc = new Document("user_id", sourceUserId)
                    .append("version", version)
                    .append("relation_user_id", targetUserId)
                    .append("operation", (int) RelationOperation.UPDATE_FRIEND_NICK_NAME.getValue())
                    .append("nick_name", nickName);
            userRelationVersionsCollection.insertOne(session, versionDoc);

            // Synchronize with conversation if remark name is not set
            String remarkName = relation.getString("remark_name");
            String conversationId = ConversationUtils.generateConversationId(sourceUserId, targetUserId);

            if (remarkName == null) {
                 Document convDoc = userConversationsCollection.find(session,
                        Filters.and(
                                Filters.eq("user_id", sourceUserId),
                                Filters.eq("conversation_id", conversationId)
                        )).first();
                 
                 if (convDoc != null) {
                     userConversationsCollection.updateOne(session,
                             Filters.and(
                                     Filters.eq("user_id", sourceUserId),
                                     Filters.eq("conversation_id", conversationId)
                             ),
                             Updates.set("name", nickName)
                     );

                     Document convVersionDoc = new Document("user_id", sourceUserId)
                             .append("version", version)
                             .append("conversation_id", conversationId)
                             .append("peer_id", targetUserId)
                             .append("operation", (int) ConversationOperation.UPDATE_CONVERSATION_NAME.getValue())
                             .append("name", nickName);
                     userConversationsVersionsCollection.insertOne(session, convVersionDoc);
                 }
            }

            session.commitTransaction();
        } catch (Exception e) {
            session.abortTransaction();
            throw e;
        } finally {
            session.close();
        }
    }

    @Override
    public void updateFriendAvatar(long sourceUserId, long targetUserId, String avatar, long version) throws NotFoundRelationException {
        ClientSession session = mongoClient.startSession();
        try {
            session.startTransaction();

            // Check relation exists
            Document relation = userRelationsCollection.find(session,
                    Filters.and(
                            Filters.eq("user_id", sourceUserId),
                            Filters.eq("relation_user_id", targetUserId)
                    )).first();

            if (relation == null) {
                throw new NotFoundRelationException(
                        "Relation not found between " + sourceUserId + " and " + targetUserId);
            }

            // Update avatar
            userRelationsCollection.updateOne(session,
                    Filters.and(
                            Filters.eq("user_id", sourceUserId),
                            Filters.eq("relation_user_id", targetUserId)
                    ),
                    Updates.set("avatar", avatar)
            );

            // Insert version
            Document versionDoc = new Document("user_id", sourceUserId)
                    .append("version", version)
                    .append("relation_user_id", targetUserId)
                    .append("operation", (int) RelationOperation.UPDATE_FRIEND_AVATAR.getValue())
                    .append("avatar", avatar);
            userRelationVersionsCollection.insertOne(session, versionDoc);

            session.commitTransaction();
        } catch (Exception e) {
            session.abortTransaction();
            throw e;
        } finally {
            session.close();
        }
    }

    @Override
    public void blockFriend(RelationEvent event, long version) throws BlockAlreadyExistsException {
        long userId = event.getUserId();
        long blockedUserId = event.getTargetUserId();

        ClientSession session = mongoClient.startSession();
        try {
            session.startTransaction();

            Document relation = userRelationsCollection.find(session,
                    Filters.and(
                            Filters.eq("user_id", userId),
                            Filters.eq("relation_user_id", blockedUserId)
                    )).first();

            if (relation != null) {
                int currentFlagsInt = relation.getInteger("relation_flags");
                byte currentFlags = (byte) currentFlagsInt;

                if (RelationFlags.BLOCKED.isSet(currentFlags)) {
                    throw new BlockAlreadyExistsException(
                            "Block already exists between " + userId + " and " + blockedUserId);
                }

                byte newFlags = RelationFlags.setFlag(currentFlags, RelationFlags.BLOCKED);

                userRelationsCollection.updateOne(session,
                        Filters.and(
                                Filters.eq("user_id", userId),
                                Filters.eq("relation_user_id", blockedUserId)
                        ),
                        Updates.set("relation_flags", (int) newFlags)
                );

                Document versionDoc = new Document("user_id", userId)
                        .append("version", version)
                        .append("relation_user_id", blockedUserId)
                        .append("operation", (int) RelationOperation.ADD_BLOCK.getValue())
                        .append("relation_flags", (int) newFlags);
                userRelationVersionsCollection.insertOne(session, versionDoc);

            } else {
                Document newRelationDoc = new Document("user_id", userId)
                        .append("relation_user_id", blockedUserId)
                        .append("relation_flags", (int) RelationFlags.BLOCKED.getValue());

                userRelationsCollection.insertOne(session, newRelationDoc);

                Document versionDoc = new Document("user_id", userId)
                        .append("version", version)
                        .append("relation_user_id", blockedUserId)
                        .append("operation", (int) RelationOperation.ADD_BLOCK.getValue())
                        .append("relation_flags", (int) RelationFlags.BLOCKED.getValue());
                userRelationVersionsCollection.insertOne(session, versionDoc);
            }

            session.commitTransaction();
        } catch (BlockAlreadyExistsException e) {
            session.abortTransaction();
            throw e;
        } catch (Exception e) {
            session.abortTransaction();
            throw e;
        } finally {
            session.close();
        }
    }

    @Override
    public void blockStranger(RelationEvent event, long version) throws StrangerHasRelationshipException, NotFoundUserProfileException {
        UserProfile stranger = this.getUserProfile(event.getTargetUserId());
        long userId = event.getUserId();
        long blockedUserId = stranger.getUserId();

        ClientSession session = mongoClient.startSession();
        try {
            session.startTransaction();

            Document relation = userRelationsCollection.find(session,
                    Filters.and(
                            Filters.eq("user_id", userId),
                            Filters.eq("relation_user_id", blockedUserId)
                    )).first();

            if (relation != null) {
                throw new StrangerHasRelationshipException(
                        "Stranger "
                                + stranger.getUserId()
                                + " already has a relationship with user "
                                + userId);
            }

            Document newRelationDoc = new Document("user_id", userId)
                    .append("relation_user_id", stranger.getUserId())
                    .append("nick_name", stranger.getNickName())
                    .append("avatar", stranger.getAvatar())
                    .append("relation_flags", (int) RelationFlags.BLOCKED.getValue());

            userRelationsCollection.insertOne(session, newRelationDoc);

            Document versionDoc = new Document("user_id", userId)
                    .append("version", version)
                    .append("relation_user_id", stranger.getUserId())
                    .append("operation", (int) RelationOperation.BLOCK_STRANGER.getValue())
                    .append("nick_name", stranger.getNickName())
                    .append("avatar", stranger.getAvatar())
                    .append("relation_flags", (int) RelationFlags.BLOCKED.getValue());
            userRelationVersionsCollection.insertOne(session, versionDoc);

            session.commitTransaction();
        } catch (StrangerHasRelationshipException e) {
            session.abortTransaction();
            throw e;
        } catch (Exception e) {
            session.abortTransaction();
            throw e;
        } finally {
            session.close();
        }
    }

    @Override
    public void unblockUser(RelationEvent event, long version) throws NotFoundBlockException {
        long userId = event.getUserId();
        long blockedUserId = event.getTargetUserId();

        ClientSession session = mongoClient.startSession();
        try {
            session.startTransaction();

            Document relation = userRelationsCollection.find(session,
                    Filters.and(
                            Filters.eq("user_id", userId),
                            Filters.eq("relation_user_id", blockedUserId)
                    )).first();

            if (relation == null) {
                throw new NotFoundBlockException(
                        "Block not found between " + userId + " and " + blockedUserId);
            }

            int currentFlagsInt = relation.getInteger("relation_flags");
            byte currentFlags = (byte) currentFlagsInt;

            if (!RelationFlags.BLOCKED.isSet(currentFlags)) {
                throw new NotFoundBlockException(
                        "Block not found between " + userId + " and " + blockedUserId);
            }

            byte newFlags = RelationFlags.clearFlag(currentFlags, RelationFlags.BLOCKED);

            if (newFlags == 0) {
                // stranger, delete relation
                userRelationsCollection.deleteOne(session,
                        Filters.and(
                                Filters.eq("user_id", userId),
                                Filters.eq("relation_user_id", blockedUserId)
                        ));

                Document versionDoc = new Document("user_id", userId)
                        .append("version", version)
                        .append("relation_user_id", blockedUserId)
                        .append("operation", (int) RelationOperation.DELETE_BLOCK.getValue());
                userRelationVersionsCollection.insertOne(session, versionDoc);

            } else if (RelationFlags.FRIEND.isSet(newFlags)) {
                // still a friend, update flags
                userRelationsCollection.updateOne(session,
                        Filters.and(
                                Filters.eq("user_id", userId),
                                Filters.eq("relation_user_id", blockedUserId)
                        ),
                        Updates.set("relation_flags", (int) newFlags)
                );

                Document versionDoc = new Document("user_id", userId)
                        .append("version", version)
                        .append("relation_user_id", blockedUserId)
                        .append("operation", (int) RelationOperation.UNBLOCK_RESTORE_FRIEND.getValue())
                        .append("relation_flags", (int) newFlags);
                userRelationVersionsCollection.insertOne(session, versionDoc);
            }

            session.commitTransaction();
        } catch (NotFoundBlockException e) {
            session.abortTransaction();
            throw e;
        } catch (Exception e) {
            session.abortTransaction();
            throw e;
        } finally {
            session.close();
        }
    }

    @Override
    public void hideBlockedUser(RelationEvent event, long version) throws NotFoundBlockException {
        long userId = event.getUserId();
        long blockedUserId = event.getTargetUserId();

        ClientSession session = mongoClient.startSession();
        try {
            session.startTransaction();

            Document relation = userRelationsCollection.find(session,
                    Filters.and(
                            Filters.eq("user_id", userId),
                            Filters.eq("relation_user_id", blockedUserId)
                    )).first();

            if (relation == null) {
                throw new NotFoundBlockException(
                        "Block not found between " + userId + " and " + blockedUserId);
            }

            int currentFlagsInt = relation.getInteger("relation_flags");
            byte currentFlags = (byte) currentFlagsInt;

            if (!RelationFlags.BLOCKED.isSet(currentFlags)) {
                throw new NotFoundBlockException(
                        "Block not found between " + userId + " and " + blockedUserId);
            }

            byte newFlags = RelationFlags.setFlag(currentFlags, RelationFlags.HIDDEN);
            if (RelationFlags.FRIEND.isSet(newFlags)) {
                newFlags = RelationFlags.clearFlag(newFlags, RelationFlags.FRIEND);
            }

            userRelationsCollection.updateOne(session,
                    Filters.and(
                            Filters.eq("user_id", userId),
                            Filters.eq("relation_user_id", blockedUserId)
                    ),
                    Updates.set("relation_flags", (int) newFlags)
            );

            Document versionDoc = new Document("user_id", userId)
                    .append("version", version)
                    .append("relation_user_id", blockedUserId)
                    .append("operation", (int) RelationOperation.HIDE_BLOCK.getValue())
                    .append("relation_flags", (int) newFlags);
            userRelationVersionsCollection.insertOne(session, versionDoc);

            session.commitTransaction();
        } catch (NotFoundBlockException e) {
            session.abortTransaction();
            throw e;
        } catch (Exception e) {
            session.abortTransaction();
            throw e;
        } finally {
            session.close();
        }
    }

    @Override
    public boolean isBlocked(long userId, long targetUserId) {
        Document relation = userRelationsCollection.find(
                Filters.and(
                        Filters.eq("user_id", userId),
                        Filters.eq("relation_user_id", targetUserId)
                )).first();
        if (relation == null) {
            return false;
        }
        int currentFlagsInt = relation.getInteger("relation_flags");
        byte currentFlags = (byte) currentFlagsInt;
        return RelationFlags.BLOCKED.isSet(currentFlags);
    }

    @Override
    public void syncFriendInfo(long sourceUserId, long targetUserId, String nickName, String avatar, long version) throws NotFoundRelationException {
        ClientSession session = mongoClient.startSession();
        try {
            session.startTransaction();

            Document relation = userRelationsCollection.find(session,
                    Filters.and(
                            Filters.eq("user_id", sourceUserId),
                            Filters.eq("relation_user_id", targetUserId)
                    )).first();

            if (relation == null) {
                throw new NotFoundRelationException(
                        "Relation not found between " + sourceUserId + " and " + targetUserId);
            }

            userRelationsCollection.updateOne(session,
                    Filters.and(
                            Filters.eq("user_id", sourceUserId),
                            Filters.eq("relation_user_id", targetUserId)
                    ),
                    Updates.combine(
                            Updates.set("nick_name", nickName),
                            Updates.set("avatar", avatar)
                    )
            );

            Document versionDoc = new Document("user_id", sourceUserId)
                    .append("version", version)
                    .append("relation_user_id", targetUserId)
                    .append("operation", (int) RelationOperation.UPDATE_FRIEND_INFO.getValue())
                    .append("nick_name", nickName)
                    .append("avatar", avatar);
            userRelationVersionsCollection.insertOne(session, versionDoc);

            String conversationId = ConversationUtils.generateConversationId(sourceUserId, targetUserId);
            Document convDoc = userConversationsCollection.find(session,
                    Filters.and(
                            Filters.eq("user_id", sourceUserId),
                            Filters.eq("conversation_id", conversationId)
                    )).first();

            if (convDoc != null) {
                String remarkName = relation.getString("remark_name");
                String displayName = (remarkName == null) ? nickName : remarkName;

                userConversationsCollection.updateOne(session,
                        Filters.and(
                                Filters.eq("user_id", sourceUserId),
                                Filters.eq("conversation_id", conversationId)
                        ),
                        Updates.set("avatar", avatar)
                );

                Document convVersionDoc = new Document("user_id", sourceUserId)
                        .append("version", version)
                        .append("conversation_id", conversationId)
                        .append("peer_id", targetUserId)
                        .append("operation", (int) ConversationOperation.UPDATE_CONVERSATION_NAME_AVATAR.getValue())
                        .append("name", displayName)
                        .append("avatar", avatar);
                userConversationsVersionsCollection.insertOne(session, convVersionDoc);
            }

            session.commitTransaction();
        } catch (Exception e) {
            session.abortTransaction();
            throw e;
        } finally {
            session.close();
        }
    }

    @Override
    public List<Long> getGroupMemberIds(long groupId) throws NotFoundGroupException {
        List<Long> memberIds = new ArrayList<>();
        groupMembersCollection.find(Filters.eq("group_id", groupId))
                .forEach(doc -> memberIds.add(doc.getLong("user_id")));

        if (memberIds.isEmpty()) {
            throw new NotFoundGroupException("Group not found for groupId: " + groupId);
        }
        return memberIds;
    }

    @Override
    public List<GroupMemberInfo> getGroupMembersInfo(long groupId) throws NotFoundGroupException {
        List<GroupMemberInfo> members = new ArrayList<>();
        groupMembersCollection.find(Filters.eq("group_id", groupId))
                .forEach(doc -> {
                    GroupMemberInfo info = new GroupMemberInfo();
                    info.setGroupId(groupId);
                    info.setUserId(doc.getLong("user_id"));
                    info.setName(doc.getString("name"));
                    info.setAvatar(doc.getString("avatar"));
                    members.add(info);
                });

        if (members.isEmpty()) {
            throw new NotFoundGroupException(
                    "Group not found or has no members for groupId: " + groupId);
        }
        return members;
    }

    @Override
    public PagedGroupMemberResult getGroupMembersPaged(long groupId, String nextPageToken, int pageSize) throws NotFoundGroupException {
        int limit = pageSize + 1;
        List<Document> docs = new ArrayList<>();

        Bson filter;
        if (nextPageToken == null || nextPageToken.isEmpty()) {
            filter = Filters.eq("group_id", groupId);
        } else {
            long lastUserId = Long.parseLong(nextPageToken);
            filter = Filters.and(
                    Filters.eq("group_id", groupId),
                    Filters.gt("user_id", lastUserId)
            );
        }

        groupMembersCollection.find(filter)
                .sort(Sorts.ascending("user_id"))
                .limit(limit)
                .into(docs);

        List<GroupMemberInfo> members = new ArrayList<>();
        for (Document doc : docs) {
            GroupMemberInfo info = new GroupMemberInfo();
            info.setGroupId(groupId);
            info.setUserId(doc.getLong("user_id"));
            info.setName(doc.getString("name"));
            info.setAvatar(doc.getString("avatar"));
            members.add(info);
        }

        if (members.isEmpty() && (nextPageToken == null || nextPageToken.isEmpty())) {
            throw new NotFoundGroupException(
                    "Group not found or has no members for groupId: " + groupId);
        }

        boolean hasMore = members.size() > pageSize;
        String nextToken = null;

        if (hasMore) {
            members.remove(members.size() - 1);
            nextToken = String.valueOf(members.get(members.size() - 1).getUserId());
        }

        return new PagedGroupMemberResult(members, nextToken, hasMore);
    }

    @Override
    public List<Long> getUserGroupIds(long userId) {
        List<Long> groupIds = new ArrayList<>();
        userGroupsCollection.find(Filters.eq("user_id", userId))
                .forEach(doc -> groupIds.add(doc.getLong("group_id")));
        return groupIds;
    }

    @Override
    public List<UserGroup> getUserGroups(long userId) {
        List<UserGroup> groups = new ArrayList<>();
        userGroupsCollection.find(Filters.eq("user_id", userId))
                .forEach(doc -> groups.add(
                        new UserGroup(
                                doc.getLong("group_id"),
                                doc.getString("group_name"),
                                doc.getString("group_avatar"))
                ));
        return groups;
    }

    @Override
    public PagedUserGroupResult getUserGroupsPaged(long userId, String nextPageToken, int pageSize) {
        int limit = pageSize + 1;
        List<Document> docs = new ArrayList<>();

        Bson filter;
        if (nextPageToken == null || nextPageToken.isEmpty()) {
            filter = Filters.eq("user_id", userId);
        } else {
            long lastGroupId = Long.parseLong(nextPageToken);
            filter = Filters.and(
                    Filters.eq("user_id", userId),
                    Filters.gt("group_id", lastGroupId)
            );
        }

        userGroupsCollection.find(filter)
                .sort(Sorts.ascending("group_id"))
                .limit(limit)
                .into(docs);

        List<UserGroup> groups = new ArrayList<>();
        for (Document doc : docs) {
            groups.add(new UserGroup(
                    doc.getLong("group_id"),
                    doc.getString("group_name"),
                    doc.getString("group_avatar")));
        }

        boolean hasMore = groups.size() > pageSize;
        String nextToken = null;

        if (hasMore) {
            groups.remove(groups.size() - 1);
            nextToken = String.valueOf(groups.get(groups.size() - 1).getGroupId());
        }

        return new PagedUserGroupResult(groups, nextToken, hasMore);
    }

    @Override
    public String getLatestUserGroupVersion(long userId) {
        Document doc = userGroupVersionsCollection.find(Filters.eq("user_id", userId))
                .sort(Sorts.descending("version"))
                .limit(1)
                .first();

        if (doc == null) {
            return null;
        }
        return String.valueOf(doc.getLong("version"));
    }

    @Override
    public List<GroupVersionChange> getGroupChanges(long groupId, String afterVersion, int limit) throws InvalidVersionException {
        long afterVersionLong;
        try {
            afterVersionLong = afterVersion == null || afterVersion.isEmpty() ? 0L : Long.parseLong(afterVersion);
        } catch (NumberFormatException e) {
            throw new InvalidVersionException(
                    "Invalid version format: "
                            + afterVersion
                            + ". Version must be a valid long.");
        }

        List<Document> docs = new ArrayList<>();
        groupMembersVersionsCollection.find(
                Filters.and(
                        Filters.eq("group_id", groupId),
                        Filters.gt("version", afterVersionLong)
                )
        )
        .sort(Sorts.ascending("version"))
        .limit(limit)
        .into(docs);

        List<GroupVersionChange> changes = new ArrayList<>();
        for (Document doc : docs) {
            GroupVersionChange change = new GroupVersionChange();
            change.setGroupId(groupId);
            change.setVersion(String.valueOf(doc.getLong("version")));

            List<Document> changesFromDoc = (List<Document>) doc.get("changes");
            List<ChangeDetail> changeDetailsList = new ArrayList<>();
            if (changesFromDoc != null) {
                for (Document changeDoc : changesFromDoc) {
                    ChangeDetail detail = new ChangeDetail();
                    detail.setOperation(changeDoc.getInteger("operation").byteValue());

                    Long userId = changeDoc.getLong("user_id");
                    if (userId != null) detail.setUserId(userId);

                    detail.setName(changeDoc.getString("name"));
                    detail.setAvatar(changeDoc.getString("avatar"));
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
        Document doc = groupMembersVersionsCollection.find(Filters.eq("group_id", groupId))
                .sort(Sorts.descending("version"))
                .limit(1)
                .first();

        if (doc == null) {
            return null;
        }
        return String.valueOf(doc.getLong("version"));
    }

    @Override
    public void createGroup(long groupId, List<UserProfile> members, long version) {
        ClientSession session = mongoClient.startSession();
        try {
            session.startTransaction();

            List<Document> memberDocs = new ArrayList<>();
            for (UserProfile member : members) {
                memberDocs.add(new Document("group_id", groupId)
                        .append("user_id", member.getUserId())
                        .append("name", member.getNickName())
                        .append("avatar", member.getAvatar()));
            }
            groupMembersCollection.insertMany(session, memberDocs);

            List<Document> changesList = new ArrayList<>();

            // Create group op
            changesList.add(new Document("operation", (int) GroupChangeOperation.CREATE_GROUP.getValue()));

            for (UserProfile member : members) {
                changesList.add(new Document("operation", (int) GroupChangeOperation.MEMBER_JOIN.getValue())
                        .append("user_id", member.getUserId())
                        .append("name", member.getNickName())
                        .append("avatar", member.getAvatar()));
            }

            Document versionDoc = new Document("group_id", groupId)
                    .append("version", version)
                    .append("changes", changesList);

            groupMembersVersionsCollection.insertOne(session, versionDoc);

            session.commitTransaction();
        } catch (Exception e) {
            session.abortTransaction();
            throw e;
        } finally {
            session.close();
        }
    }

    @Override
    public void createUserGroupAndConversation(long userId, long groupId, String groupName, String groupAvatar, long version) {
        String conversationId = ConversationUtils.generateGroupConversationId(groupId);
        if (groupAvatar != null && groupAvatar.isEmpty()) {
            groupAvatar = null;
        }

        ClientSession session = mongoClient.startSession();
        try {
            session.startTransaction();

            userGroupsCollection.insertOne(session, new Document("user_id", userId)
                    .append("group_id", groupId)
                    .append("group_name", groupName)
                    .append("group_avatar", groupAvatar));

            userGroupVersionsCollection.insertOne(session, new Document("user_id", userId)
                    .append("version", version)
                    .append("group_id", groupId)
                    .append("operation", (int) UserGroupOperation.JOIN.getValue())
                    .append("group_name", groupName)
                    .append("group_avatar", groupAvatar));

            userConversationsCollection.insertOne(session, new Document("user_id", userId)
                    .append("conversation_id", conversationId)
                    .append("group_id", groupId)
                    .append("name", groupName)
                    .append("avatar", groupAvatar)
                    .append("unread_count", 0)
                    .append("last_read_message_id", 0L));

            userConversationsVersionsCollection.insertOne(session, new Document("user_id", userId)
                    .append("version", version)
                    .append("conversation_id", conversationId)
                    .append("group_id", groupId)
                    .append("operation", (int) ConversationOperation.CREATE_CONVERSATION.getValue())
                    .append("name", groupName)
                    .append("avatar", groupAvatar));

            session.commitTransaction();
        } catch (Exception e) {
            session.abortTransaction();
            throw e;
        } finally {
            session.close();
        }
    }

    @Override
    public void createGroupMembersProfile(long groupId, List<UserProfile> members, long version) {
        ClientSession session = mongoClient.startSession();
        try {
            session.startTransaction();

            List<Document> memberDocs = new ArrayList<>();
            for (UserProfile member : members) {
                memberDocs.add(new Document("group_id", groupId)
                        .append("user_id", member.getUserId())
                        .append("name", member.getNickName())
                        .append("avatar", member.getAvatar()));
            }
            groupMembersCollection.insertMany(session, memberDocs);

            List<Document> changesList = new ArrayList<>();

            for (UserProfile member : members) {
                changesList.add(new Document("operation", (int) GroupChangeOperation.MEMBER_JOIN.getValue())
                        .append("user_id", member.getUserId())
                        .append("name", member.getNickName())
                        .append("avatar", member.getAvatar()));
            }

            Document versionDoc = new Document("group_id", groupId)
                    .append("version", version)
                    .append("changes", changesList);

            groupMembersVersionsCollection.insertOne(session, versionDoc);

            session.commitTransaction();
        } catch (Exception e) {
            session.abortTransaction();
            throw e;
        } finally {
            session.close();
        }
    }

    @Override
    public void updateGroupMemberName(long groupId, long userId, String name, long version) {
        ClientSession session = mongoClient.startSession();
        try {
            session.startTransaction();

            groupMembersCollection.updateOne(session,
                    Filters.and(
                            Filters.eq("group_id", groupId),
                            Filters.eq("user_id", userId)
                    ),
                    Updates.set("name", name)
            );

            List<Document> changesList = new ArrayList<>();
            changesList.add(new Document("operation", (int) GroupChangeOperation.MEMBER_UPDATE_NAME.getValue())
                    .append("user_id", userId)
                    .append("name", name));

            groupMembersVersionsCollection.insertOne(session, new Document("group_id", groupId)
                    .append("version", version)
                    .append("changes", changesList));

            session.commitTransaction();
        } catch (Exception e) {
            session.abortTransaction();
            throw e;
        } finally {
            session.close();
        }
    }

    @Override
    public void updateGroupMemberAvatar(long groupId, long userId, String avatar, long version) {
        ClientSession session = mongoClient.startSession();
        try {
            session.startTransaction();

            groupMembersCollection.updateOne(session,
                    Filters.and(
                            Filters.eq("group_id", groupId),
                            Filters.eq("user_id", userId)
                    ),
                    Updates.set("avatar", avatar)
            );

            List<Document> changesList = new ArrayList<>();
            changesList.add(new Document("operation", (int) GroupChangeOperation.MEMBER_UPDATE_AVATAR.getValue())
                    .append("user_id", userId)
                    .append("avatar", avatar));

            groupMembersVersionsCollection.insertOne(session, new Document("group_id", groupId)
                    .append("version", version)
                    .append("changes", changesList));

            session.commitTransaction();
        } catch (Exception e) {
            session.abortTransaction();
            throw e;
        } finally {
            session.close();
        }
    }

    @Override
    public void removeUserGroup(long userId, long groupId, long version) {
        ClientSession session = mongoClient.startSession();
        try {
            session.startTransaction();

            userGroupsCollection.deleteOne(session,
                    Filters.and(
                            Filters.eq("user_id", userId),
                            Filters.eq("group_id", groupId)
                    ));

            userGroupVersionsCollection.insertOne(session, new Document("user_id", userId)
                    .append("version", version)
                    .append("group_id", groupId)
                    .append("operation", (int) UserGroupOperation.QUIT.getValue()));

            session.commitTransaction();
        } catch (Exception e) {
            session.abortTransaction();
            throw e;
        } finally {
            session.close();
        }
    }

    @Override
    public void updateUserGroupName(long userId, long groupId, String groupName, long version) {
        String conversationId = ConversationUtils.generateGroupConversationId(groupId);
        ClientSession session = mongoClient.startSession();
        try {
            session.startTransaction();

            userGroupsCollection.updateOne(session,
                    Filters.and(
                            Filters.eq("user_id", userId),
                            Filters.eq("group_id", groupId)
                    ),
                    Updates.set("group_name", groupName)
            );

            userGroupVersionsCollection.insertOne(session, new Document("user_id", userId)
                    .append("version", version)
                    .append("group_id", groupId)
                    .append("operation", (int) UserGroupOperation.UPDATE_GROUP_NAME.getValue())
                    .append("group_name", groupName));

            userConversationsCollection.updateOne(session,
                    Filters.and(
                            Filters.eq("user_id", userId),
                            Filters.eq("conversation_id", conversationId)
                    ),
                    Updates.set("name", groupName)
            );

            userConversationsVersionsCollection.insertOne(session, new Document("user_id", userId)
                    .append("version", version)
                    .append("conversation_id", conversationId)
                    .append("group_id", groupId)
                    .append("operation", (int) ConversationOperation.UPDATE_CONVERSATION_NAME.getValue())
                    .append("name", groupName));

            session.commitTransaction();
        } catch (Exception e) {
            session.abortTransaction();
            throw e;
        } finally {
            session.close();
        }
    }

    @Override
    public void updateUserGroupAvatar(long userId, long groupId, String groupAvatar, long version) {
        String conversationId = ConversationUtils.generateGroupConversationId(groupId);
        ClientSession session = mongoClient.startSession();
        try {
            session.startTransaction();

            userGroupsCollection.updateOne(session,
                    Filters.and(
                            Filters.eq("user_id", userId),
                            Filters.eq("group_id", groupId)
                    ),
                    Updates.set("group_avatar", groupAvatar)
            );

            userGroupVersionsCollection.insertOne(session, new Document("user_id", userId)
                    .append("version", version)
                    .append("group_id", groupId)
                    .append("operation", (int) UserGroupOperation.UPDATE_GROUP_AVATAR.getValue())
                    .append("group_avatar", groupAvatar));

            userConversationsCollection.updateOne(session,
                    Filters.and(
                            Filters.eq("user_id", userId),
                            Filters.eq("conversation_id", conversationId)
                    ),
                    Updates.set("avatar", groupAvatar)
            );

            userConversationsVersionsCollection.insertOne(session, new Document("user_id", userId)
                    .append("version", version)
                    .append("conversation_id", conversationId)
                    .append("group_id", groupId)
                    .append("operation", (int) ConversationOperation.UPDATE_CONVERSATION_AVATAR.getValue())
                    .append("avatar", groupAvatar));

            session.commitTransaction();
        } catch (Exception e) {
            session.abortTransaction();
            throw e;
        } finally {
            session.close();
        }
    }

    @Override
    public List<UserGroupVersionChange> getUserGroupChanges(long userId, String afterVersion, int limit) throws InvalidVersionException {
        long afterVersionLong;
        try {
            afterVersionLong = afterVersion == null ? 0L : Long.parseLong(afterVersion);
        } catch (NumberFormatException e) {
            throw new InvalidVersionException("Invalid version format: " + afterVersion);
        }

        List<Document> docs = new ArrayList<>();
        userGroupVersionsCollection.find(
                Filters.and(
                        Filters.eq("user_id", userId),
                        Filters.gt("version", afterVersionLong)
                )
        )
        .sort(Sorts.ascending("version"))
        .limit(limit)
        .into(docs);

        List<UserGroupVersionChange> changes = new ArrayList<>();
        for (Document doc : docs) {
            UserGroupVersionChange change = new UserGroupVersionChange();
            change.setGroupId(doc.getLong("group_id"));
            change.setOperation(doc.getInteger("operation").byteValue());
            change.setGroupName(doc.getString("group_name"));
            change.setGroupAvatar(doc.getString("group_avatar"));
            change.setVersion(String.valueOf(doc.getLong("version")));
            changes.add(change);
        }
        return changes;
    }

    @Override
    public void removeGroupMember(long groupId, long userId, long version) {
        ClientSession session = mongoClient.startSession();
        try {
            session.startTransaction();

            groupMembersCollection.deleteOne(session,
                    Filters.and(
                            Filters.eq("group_id", groupId),
                            Filters.eq("user_id", userId)
                    ));

            List<Document> changesList = new ArrayList<>();
            changesList.add(new Document("operation", (int) GroupChangeOperation.MEMBER_QUIT.getValue())
                    .append("user_id", userId));

            groupMembersVersionsCollection.insertOne(session, new Document("group_id", groupId)
                    .append("version", version)
                    .append("changes", changesList));

            session.commitTransaction();
        } catch (Exception e) {
            session.abortTransaction();
            throw e;
        } finally {
            session.close();
        }
    }

    @Override
    public void removeGroupConversation(long userId, long groupId, long version) {
        String conversationId = ConversationUtils.generateGroupConversationId(groupId);
        ClientSession session = mongoClient.startSession();
        try {
            session.startTransaction();

            userConversationsCollection.deleteOne(session,
                    Filters.and(
                            Filters.eq("user_id", userId),
                            Filters.eq("conversation_id", conversationId)
                    ));

            userConversationsVersionsCollection.insertOne(session, new Document("user_id", userId)
                    .append("version", version)
                    .append("conversation_id", conversationId)
                    .append("group_id", groupId)
                    .append("operation", (int) ConversationOperation.REMOVE_CONVERSATION.getValue()));

            session.commitTransaction();
        } catch (Exception e) {
            session.abortTransaction();
            throw e;
        } finally {
            session.close();
        }
    }

    @Override
    public void saveGroupCommandMessage(String conversationId, long messageId, long senderId, long groupId, long timestamp, byte commandType, String commandData) {
        Document doc = new Document("conversation_id", conversationId)
                .append("message_id", messageId)
                .append("sender_id", senderId)
                .append("group_id", groupId)
                .append("send_timestamp", timestamp)
                .append("message_type", (int) MessageType.MESSAGE_TYPE_GROUP_COMMAND.getValue())
                .append("command_type", (int) commandType)
                .append("command_data", commandData);

        userMessagesCollection.insertOne(doc);
    }

    @Override
    public int calculateUnreadCount(long userId, String conversationId) {
        Document convDoc = userConversationsCollection.find(
                Filters.and(
                        Filters.eq("user_id", userId),
                        Filters.eq("conversation_id", conversationId)
                )
        ).first();

        if (convDoc == null) {
            return 0;
        }

        Long lastReadMessageId = convDoc.getLong("last_read_message_id");
        if (lastReadMessageId == null) {
            lastReadMessageId = 0L;
        }

        long count = userMessagesCollection.countDocuments(
                Filters.and(
                        Filters.eq("conversation_id", conversationId),
                        Filters.gt("message_id", lastReadMessageId),
                        Filters.ne("sender_id", userId)
                )
        );

        return (int) count;
    }
}
