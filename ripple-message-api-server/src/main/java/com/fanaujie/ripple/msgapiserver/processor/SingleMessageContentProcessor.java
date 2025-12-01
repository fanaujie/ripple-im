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
        long receiverId = request.getReceiverId();
        if (!this.storageFacade.isBlocked(senderId, receiverId)) {
            MessageData.Builder b = MessageData.newBuilder().setSendUserId(senderId);
            b.addReceiveUserIds(receiverId);
            b.addReceiveUserIds(senderId); // also notify self for multi-device sync
            b.setData(request);
            MessagePayload messageData =
                    MessagePayload.newBuilder().setMessageData(b.build()).build();
            this.executorService
                    .submit(
                            () ->
                                    this.producer.send(
                                            this.topicName, String.valueOf(senderId), messageData))
                    .get();
            return SendMessageResp.newBuilder().build();
        }
        return SendMessageResp.newBuilder().build();
    }
}
