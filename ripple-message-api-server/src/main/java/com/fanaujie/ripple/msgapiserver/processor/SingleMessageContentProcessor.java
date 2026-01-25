package com.fanaujie.ripple.msgapiserver.processor;

import com.fanaujie.ripple.communication.msgqueue.GenericProducer;
import com.fanaujie.ripple.communication.processor.Processor;
import com.fanaujie.ripple.protobuf.msgapiserver.SendMessageReq;
import com.fanaujie.ripple.protobuf.msgapiserver.SendMessageResp;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessageData;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;

public class SingleMessageContentProcessor implements Processor<SendMessageReq, SendMessageResp> {

    private final Logger logger = LoggerFactory.getLogger(SingleMessageContentProcessor.class);
    private final String topicName;
    private final GenericProducer<String, MessagePayload> producer;
    private final ExecutorService executorService;
    private final RippleStorageFacade storageFacade;

    public SingleMessageContentProcessor(
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
    public SendMessageResp handle(SendMessageReq request) throws Exception {
        long senderId = request.getSenderId();
        long groupId = request.getGroupId();
        if (groupId > 0) {
            return handleGroupMessage(request, senderId, groupId);
        } else {
            return handleSingleMessage(request, senderId);
        }
    }

    private SendMessageResp handleSingleMessage(SendMessageReq request, long senderId)
            throws Exception {
        long receiverId = request.getReceiverId();
        // Bots never block anyone; skip block check if receiver is a bot
        boolean isReceiverBot = this.storageFacade.isBot(receiverId);
        if (isReceiverBot || !this.storageFacade.isBlocked(receiverId, senderId)) {
            MessageData.Builder b = MessageData.newBuilder().setSendUserId(senderId);
            b.addReceiveUserIds(receiverId);
            b.addReceiveUserIds(senderId); // also notify self for multi-device sync
            b.setData(request);
            MessagePayload messageData =
                    MessagePayload.newBuilder().setMessageData(b.build()).build();
            this.producer.send(this.topicName, String.valueOf(senderId), messageData);
            return SendMessageResp.newBuilder().build();
        }
        return SendMessageResp.newBuilder().build();
    }

    private SendMessageResp handleGroupMessage(SendMessageReq request, long senderId, long groupId)
            throws Exception {
        List<Long> memberIds = this.storageFacade.getGroupMemberIds(groupId);
        if (memberIds.isEmpty()) {
            logger.warn("Group {} has no members, skipping message", groupId);
            return SendMessageResp.newBuilder().build();
        }

        MessageData.Builder b = MessageData.newBuilder().setSendUserId(senderId);
        b.addAllReceiveUserIds(memberIds);
        b.setData(request);
        MessagePayload messageData = MessagePayload.newBuilder().setMessageData(b.build()).build();
        this.executorService
                .submit(
                        () ->
                                this.producer.send(
                                        this.topicName, String.valueOf(senderId), messageData))
                .get();
        return SendMessageResp.newBuilder().build();
    }
}
