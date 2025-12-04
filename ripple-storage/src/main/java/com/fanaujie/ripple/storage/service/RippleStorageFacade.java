package com.fanaujie.ripple.storage.service;

import com.fanaujie.ripple.protobuf.msgapiserver.RelationEvent;
import com.fanaujie.ripple.protobuf.msgapiserver.SingleMessageContent;
import com.fanaujie.ripple.protobuf.storage.UserIds;
import com.fanaujie.ripple.storage.exception.*;
import com.fanaujie.ripple.storage.model.*;

import java.util.List;
import java.util.Optional;

public interface RippleStorageFacade {
    User findByAccount(String account);

    boolean userExists(String account);

    void insertUser(User user, String displayName, String avatar);

    UserProfile getUserProfile(long userId) throws NotFoundUserProfileException;

    void updateProfileNickNameByUserId(long userId, String nickName)
            throws NotFoundUserProfileException;

    void updateProfileAvatarByUserId(long userId, String avatar)
            throws NotFoundUserProfileException;

    PagedRelationResult getRelations(long sourceUserId, String nextPageToken, int pageSize);

    Relation getRelationBetweenUser(long userId, long targetUserId);

    List<RelationVersionChange> getRelationChanges(long userId, String afterVersion, int limit)
            throws InvalidVersionException;

    String getLatestRelationVersion(long userId);

    Messages getMessages(String conversationId, long beforeMessageId, int pageSize);

    void markLastReadMessageId(String conversationId, long ownerId, long readMessageId);

    boolean existsByConversationId(String conversationId, long ownerId);

    void createSingeMessageConversation(String conversationId, long ownerId, long peerId)
            throws NotFoundUserProfileException;

    void saveMessage(
            String conversationId,
            long messageId,
            long senderId,
            long receiverId,
            long groupId,
            long timestamp,
            SingleMessageContent content);

    PagedConversationResult getConversations(long userId, String nextPageToken, int pageSize);

    List<ConversationVersionChange> getConversationChanges(
            long userId, String afterVersion, int limit) throws InvalidVersionException;

    String getLatestConversationVersion(long userId);

    Optional<UserIds> getFriendIds(long userId);

    void addFriend(RelationEvent event)
            throws NotFoundUserProfileException, RelationAlreadyExistsException;

    void removeFriend(RelationEvent event) throws NotFoundRelationException;

    UpdateFriendRemarkNameResult updateFriendRemarkName(RelationEvent event)
            throws NotFoundRelationException;

    void updateFriendNickName(long sourceUserId, long targetUserId, String nickName)
            throws NotFoundRelationException;

    void updateFriendAvatar(long sourceUserId, long targetUserId, String avatar)
            throws NotFoundRelationException;

    void blockFriend(RelationEvent event) throws BlockAlreadyExistsException;

    void blockStranger(RelationEvent event)
            throws StrangerHasRelationshipException, NotFoundUserProfileException;

    void unblockUser(RelationEvent event) throws NotFoundBlockException;

    void hideBlockedUser(RelationEvent event) throws NotFoundBlockException;

    boolean isBlocked(long userId, long targetUserId);

    void syncFriendInfo(long sourceUserId, long targetUserId, String nickName, String avatar)
            throws NotFoundRelationException;
}
