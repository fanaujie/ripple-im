package com.fanaujie.ripple.communication.msgqueue.uitls;

import com.fanaujie.ripple.protobuf.msgdispatcher.GroupCommandData;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessageData;
import com.fanaujie.ripple.protobuf.push.*;

public class MessageConverter {

    public static PushMessage toPushMessage(long senderId, UserNotifications userNotifications) {
        return PushMessage.newBuilder()
                .setEventData(
                        PushEventData.newBuilder()
                                .setSendUserId(senderId)
                                .putUserNotifications(
                                        userNotifications.getReceiveUserId(),
                                        userNotifications.getNotification())
                                .build())
                .build();
    }

    public static PushMessage toPushMessage(MessageData messageData) {
        PushMessageData.Builder builder =
                PushMessageData.newBuilder()
                        .setMessageType(MessageType.MESSAGE_TYPE_SINGLE_MESSAGE)
                        .setSendUserId(messageData.getSendUserId())
                        .addAllReceiveUserIds(messageData.getReceiveUserIdsList())
                        .setData(messageData.getData());
        return PushMessage.newBuilder().setMessageData(builder).build();
    }

    public static PushMessage toPushMessage(GroupCommandData groupCommandData) {
        return null;
    }
}
