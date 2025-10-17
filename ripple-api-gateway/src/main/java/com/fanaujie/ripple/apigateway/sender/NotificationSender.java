package com.fanaujie.ripple.apigateway.sender;

import com.fanaujie.ripple.protobuf.msgapiserver.RelationEvent;

public interface NotificationSender {
    void sendFriendNotification(long initiatorId, long friendId, RelationEvent.EventType eventType);

    void sendBlockNotification(
            long initiatorId, long targetUserId, RelationEvent.EventType eventType);

    void sendSelfInfoUpdatedNotification(long userId);
}
