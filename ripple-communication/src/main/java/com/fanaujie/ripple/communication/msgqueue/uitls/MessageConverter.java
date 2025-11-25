package com.fanaujie.ripple.communication.msgqueue.uitls;

import com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq;
import com.fanaujie.ripple.protobuf.msgdispatcher.EventData;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessageData;
import com.fanaujie.ripple.protobuf.push.*;

public class MessageConverter {
    public static PushMessage toPushMessage(EventData eventData) {
        PushEventData.Builder builder =
                PushEventData.newBuilder()
                        .setSendUserId(eventData.getSendUserId())
                        .addAllReceiveUserIds(eventData.getReceiveUserIdsList());

        // Determine event type based on the SendEventReq oneof field
        SendEventReq sendEventReq = eventData.getData();
        switch (sendEventReq.getEventCase()) {
            case SELF_INFO_UPDATE_EVENT:
                builder.setEventType(EventType.EVENT_TYPE_SELF_INFO_UPDATE);
                break;
            case RELATION_EVENT:
                builder.setEventType(EventType.EVENT_TYPE_RELATION_UPDATE);
                break;
            case EVENT_NOT_SET:
            default:
                builder.setEventType(EventType.EVENT_TYPE_UNSPECIFIED);
                break;
        }
        return PushMessage.newBuilder().setEventData(builder).build();
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
}
