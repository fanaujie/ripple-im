package com.fanaujie.ripple.storage.service;

import com.fanaujie.ripple.protobuf.msgapiserver.RelationEvent;
import com.fanaujie.ripple.protobuf.msgapiserver.SingleMessageContent;
import com.fanaujie.ripple.protobuf.storage.UserIds;
import com.fanaujie.ripple.storage.exception.*;
import com.fanaujie.ripple.storage.model.*;

import java.util.List;

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

    Message getMessages(String conversationId, long messageId) throws NotMessageException;

    void markLastReadMessageId(String conversationId, long ownerId, long readMessageId, long version);

    boolean existsByConversationId(String conversationId, long ownerId);

    void createSingeMessageConversation(String conversationId, long ownerId, long peerId, long version)
            throws NotFoundUserProfileException;

    void saveTextMessage(
            String conversationId,
            long messageId,
            long senderId,
            long receiverId,
            long timestamp,
            String messageText,
            String fileUrl,
            String fileName);

    void saveGroupTextMessage(
            String conversationId,
            long messageId,
            long senderId,
            long groupId,
            long timestamp,
            String messageText,
            String fileUrl,
            String fileName);

    PagedConversationResult getConversations(long userId, String nextPageToken, int pageSize);

    List<ConversationVersionChange> getConversationChanges(
            long userId, String afterVersion, int limit) throws InvalidVersionException;

    String getLatestConversationVersion(long userId);

    UserIds getFriendIds(long userId);

    void addFriend(RelationEvent event, long version)
            throws NotFoundUserProfileException, RelationAlreadyExistsException;

    void removeFriend(RelationEvent event, long version) throws NotFoundRelationException;

    UpdateFriendRemarkNameResult updateFriendRemarkName(RelationEvent event, long version)
            throws NotFoundRelationException;

    void updateFriendNickName(long sourceUserId, long targetUserId, String nickName, long version)
            throws NotFoundRelationException;

    void updateFriendAvatar(long sourceUserId, long targetUserId, String avatar, long version)
            throws NotFoundRelationException;

    void blockFriend(RelationEvent event, long version) throws BlockAlreadyExistsException;

    void blockStranger(RelationEvent event, long version)
            throws StrangerHasRelationshipException, NotFoundUserProfileException;

    void unblockUser(RelationEvent event, long version) throws NotFoundBlockException;

    void hideBlockedUser(RelationEvent event, long version) throws NotFoundBlockException;

    boolean isBlocked(long userId, long targetUserId);

    void syncFriendInfo(long sourceUserId, long targetUserId, String nickName, String avatar, long version)
            throws NotFoundRelationException;

    List<Long> getGroupMemberIds(long groupId) throws NotFoundGroupException;

    List<GroupMemberInfo> getGroupMembersInfo(long groupId) throws NotFoundGroupException;

    PagedGroupMemberResult getGroupMembersPaged(long groupId, String nextPageToken, int pageSize)
            throws NotFoundGroupException;

    List<Long> getUserGroupIds(long userId);

    List<UserGroup> getUserGroups(long userId);

    PagedUserGroupResult getUserGroupsPaged(long userId, String nextPageToken, int pageSize);

    String getLatestUserGroupVersion(long userId);

    List<GroupVersionChange> getGroupChanges(long groupId, String afterVersion, int limit)
            throws InvalidVersionException;

    String getLatestGroupVersion(long groupId);

    void createGroup(long groupId, List<UserProfile> members, long version);

    void createUserGroupAndConversation(
            long userId, long groupId, String groupName, String groupAvatar, long version);

    void createGroupMembersProfile(long groupId, List<UserProfile> members, long version);

    void updateGroupMemberName(long groupId, long userId, String name, long version);

    void updateGroupMemberAvatar(long groupId, long userId, String avatar, long version);

    void removeUserGroup(long userId, long groupId, long version);

    void updateUserGroupName(long userId, long groupId, String groupName, long version);

    void updateUserGroupAvatar(long userId, long groupId, String groupAvatar, long version);

    List<UserGroupVersionChange> getUserGroupChanges(long userId, String afterVersion, int limit)
            throws InvalidVersionException;

    void removeGroupMember(long groupId, long userId, long version);

    void removeGroupConversation(long userId, long groupId, long version);

    void saveGroupCommandMessage(
            String conversationId,
            long messageId,
            long senderId,
            long groupId,
            long timestamp,
            byte commandType,
            String commandData);

    int calculateUnreadCount(long userId, String conversationId);
}
