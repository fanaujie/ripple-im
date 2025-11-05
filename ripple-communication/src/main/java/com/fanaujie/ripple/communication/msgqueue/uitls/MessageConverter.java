package com.fanaujie.ripple.communication.msgqueue.uitls;

import com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq;
import com.fanaujie.ripple.protobuf.msgdispatcher.EventData;
import com.fanaujie.ripple.protobuf.push.EventType;
import com.fanaujie.ripple.protobuf.push.PushEventData;
import com.fanaujie.ripple.protobuf.push.PushMessage;

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
}
