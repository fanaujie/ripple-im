package com.fanaujie.ripple.msgapiserver.processor;

import com.fanaujie.ripple.communication.msgqueue.GenericProducer;
import com.fanaujie.ripple.communication.processor.Processor;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventResp;
import com.fanaujie.ripple.protobuf.msgdispatcher.EventData;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;
import com.fanaujie.ripple.protobuf.storage.UserIds;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.ExecutorService;

public class SelfInfoUpdateEventProcessor implements Processor<SendEventReq, SendEventResp> {

    private final Logger logger = LoggerFactory.getLogger(SelfInfoUpdateEventProcessor.class);
    private final String topicName;
    private final GenericProducer<String, MessagePayload> producer;
    private final ExecutorService executorService;
    private final RippleStorageFacade storageFacade;

    public SelfInfoUpdateEventProcessor(
            String topicName,
            RippleStorageFacade storageFacade,
            GenericProducer<String, MessagePayload> producer,
            ExecutorService executorService) {
        this.topicName = topicName;
        this.storageFacade = storageFacade;
        this.producer = producer;
        this.executorService = executorService;
    }

    @Override
    public SendEventResp handle(SendEventReq request) throws Exception {
        long userId = request.getSelfInfoUpdateEvent().getUserId();
        Optional<UserIds> friendIds = this.storageFacade.getFriendIds(userId);
        EventData.Builder b = EventData.newBuilder().setSendUserId(userId).setData(request);
        friendIds.ifPresent(
                friends -> {
                    for (long friendUserId : friends.getUserIdsList()) {
                        b.addReceiveUserIds(friendUserId);
                    }
                });
        b.addReceiveUserIds(userId); // also notify self for multi-device sync
        MessagePayload messageData = MessagePayload.newBuilder().setEventData(b.build()).build();
        this.executorService
                .submit(
                        () ->
                                this.producer.send(
                                        this.topicName, String.valueOf(userId), messageData))
                .get();
        return SendEventResp.newBuilder().build();
    }
}
