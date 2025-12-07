package com.fanaujie.ripple.msgapiserver.processor;

import com.fanaujie.ripple.communication.msgqueue.GenericProducer;
import com.fanaujie.ripple.communication.processor.Processor;
import com.fanaujie.ripple.protobuf.msgapiserver.*;
import com.fanaujie.ripple.protobuf.msgdispatcher.GroupCommandData;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;

public class InviteMembersProcessor
        implements Processor<SendGroupCommandReq, SendGroupCommandResp> {

    private final Logger logger = LoggerFactory.getLogger(InviteMembersProcessor.class);
    private final String topicName;
    private final GenericProducer<String, MessagePayload> producer;
    private final ExecutorService executorService;
    private final RippleStorageFacade storageFacade;

    public InviteMembersProcessor(
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
    public SendGroupCommandResp handle(SendGroupCommandReq request) throws Exception {
        GroupCommandData.Builder b =
                GroupCommandData.newBuilder()
                        .setSenderUserId(request.getSenderId())
                        .setData(request);
        List<Long> receiverIds = storageFacade.getGroupMemberIds(request.getGroupId());
        receiverIds.addAll(request.getGroupInviteCommand().getNewMemberIdsList());
        b.addAllReceiveUserIds(receiverIds);
        MessagePayload messagePayload =
                MessagePayload.newBuilder().setGroupCommandData(b.build()).build();
        this.producer.send(this.topicName, String.valueOf(request.getSenderId()), messagePayload);
        return SendGroupCommandResp.newBuilder().build();
    }
}
