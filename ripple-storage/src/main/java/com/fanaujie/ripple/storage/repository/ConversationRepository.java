package com.fanaujie.ripple.storage.repository;

import com.fanaujie.ripple.protobuf.msgapiserver.SingleMessageContent;
import com.fanaujie.ripple.storage.exception.InvalidVersionException;
import com.fanaujie.ripple.storage.model.ConversationVersionChange;
import com.fanaujie.ripple.storage.model.PagedConversationResult;
import com.fanaujie.ripple.storage.model.Messages;

import java.util.List;

public interface ConversationRepository {
    boolean existsById(String conversationId, long ownerId);

    void createSingeMessageConversation(String conversationId, long ownerId, long peerId);

    void updateSingeMessageConversation(
            String conversationId,
            long ownerId,
            long peerId,
            long lastMessageId,
            long lastMessageTimestamp,
            SingleMessageContent singleMessageContent);

    void markLastReadMessageId(String conversationId, long ownerId, long readMessageId);

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

    Messages getMessages(String conversationId, long beforeMessageId, int pageSize);
}
