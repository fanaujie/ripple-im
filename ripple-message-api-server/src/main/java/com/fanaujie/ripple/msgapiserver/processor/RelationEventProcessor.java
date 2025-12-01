package com.fanaujie.ripple.msgapiserver.processor;

import com.fanaujie.ripple.communication.msgqueue.GenericProducer;
import com.fanaujie.ripple.communication.processor.Processor;
import com.fanaujie.ripple.protobuf.msgapiserver.RelationEvent;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventResp;
import com.fanaujie.ripple.protobuf.msgdispatcher.EventData;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;
import com.fanaujie.ripple.storage.model.Relation;
import com.fanaujie.ripple.storage.model.RelationFlags;
import com.fanaujie.ripple.storage.model.UserProfile;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class RelationEventProcessor implements Processor<SendEventReq, SendEventResp> {

    private final String topicName;
    private final GenericProducer<String, MessagePayload> producer;
    private final ExecutorService executorService;
    private final RippleStorageFacade storageFacade;

    public RelationEventProcessor(
            String topicName,
            GenericProducer<String, MessagePayload> producer,
            ExecutorService executorService,
            RippleStorageFacade storageFacade) {
        this.topicName = topicName;
        this.producer = producer;
        this.executorService = executorService;
        this.storageFacade = storageFacade;
    }

    @Override
    public SendEventResp handle(SendEventReq request) throws Exception {
        List<Future<?>> futures = new ArrayList<>();
        switch (request.getRelationEvent().getEventType()) {
            case ADD_FRIEND:
                // When user A adds user B as a friend, we need to check if B has already added A
                // If B has already added A (bidirectional friendship exists), we need to notify B
                // to update A's friend information (nickname and avatar) in B's friend list

                RelationEvent e = request.getRelationEvent();
                Relation r =
                        this.storageFacade.getRelationBetweenUser(
                                e.getTargetUserId(), e.getUserId());
                if (r != null && RelationFlags.FRIEND.isSet(r.getRelationFlags())) {
                    UserProfile userProfile = this.storageFacade.getUserProfile(e.getUserId());
                    SendEventReq req =
                            SendEventReq.newBuilder()
                                    .setRelationEvent(
                                            RelationEvent.newBuilder()
                                                    .setEventType(
                                                            RelationEvent.EventType
                                                                    .UPDATE_FRIEND_INFO)
                                                    .setUserId(e.getTargetUserId())
                                                    .setTargetUserId(e.getUserId())
                                                    .setTargetUserNickName(
                                                            userProfile.getNickName())
                                                    .setTargetUserAvatar(userProfile.getAvatar())
                                                    .build())
                                    .build();
                    EventData.Builder b =
                            EventData.newBuilder()
                                    .setSendUserId(e.getUserId())
                                    .addReceiveUserIds(e.getTargetUserId())
                                    .setData(req);
                    MessagePayload messageData =
                            MessagePayload.newBuilder().setEventData(b.build()).build();
                    futures.add(
                            this.executorService.submit(
                                    () ->
                                            this.producer.send(
                                                    this.topicName,
                                                    String.valueOf(e.getTargetUserId()),
                                                    messageData)));
                }
            case REMOVE_FRIEND:
            case UPDATE_FRIEND_REMARK_NAME:
            case BLOCK_STRANGER:
            case BLOCK_FRIEND:
            case UNBLOCK_USER:
            case HIDE_BLOCKED_USER:
                long userId = request.getRelationEvent().getUserId();
                EventData.Builder b = EventData.newBuilder().setSendUserId(userId).setData(request);
                b.addReceiveUserIds(userId); // notify self for multi-device sync
                MessagePayload messageData =
                        MessagePayload.newBuilder().setEventData(b.build()).build();
                futures.add(
                        this.executorService.submit(
                                () ->
                                        this.producer.send(
                                                this.topicName,
                                                String.valueOf(userId),
                                                messageData)));
                for (Future<?> f : futures) {
                    f.get();
                }
                return SendEventResp.newBuilder().build();
            default:
                throw new IllegalArgumentException("Unknown relation event type");
        }
    }
}
