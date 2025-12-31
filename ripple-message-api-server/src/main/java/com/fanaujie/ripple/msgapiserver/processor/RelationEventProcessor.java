package com.fanaujie.ripple.msgapiserver.processor;

import com.fanaujie.ripple.communication.msgqueue.GenericProducer;
import com.fanaujie.ripple.communication.processor.Processor;
import com.fanaujie.ripple.protobuf.msgapiserver.RelationEvent;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventResp;
import com.fanaujie.ripple.protobuf.msgdispatcher.EventData;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;
import com.fanaujie.ripple.storage.exception.NotFoundUserProfileException;
import com.fanaujie.ripple.storage.model.UserProfile;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class RelationEventProcessor implements Processor<SendEventReq, SendEventResp> {

    private final Logger logger = LoggerFactory.getLogger(RelationEventProcessor.class);
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
        RelationEvent event = request.getRelationEvent();

        // Guard: Bots cannot participate in the relation system
        switch (event.getEventType()) {
            case ADD_FRIEND:
            case BLOCK_STRANGER:
            case BLOCK_FRIEND:
                if (isBot(event.getTargetUserId())) {
                    logger.warn("Rejected {} for bot target: user {} -> bot {}",
                            event.getEventType(), event.getUserId(), event.getTargetUserId());
                    return SendEventResp.newBuilder()
                            .setSuccess(false)
                            .setErrorMessage("Cannot " + event.getEventType().name().toLowerCase().replace('_', ' ') + " a bot")
                            .build();
                }
                break;
            default:
                break;
        }

        switch (event.getEventType()) {
            case ADD_FRIEND:
            case REMOVE_FRIEND:
            case UPDATE_FRIEND_REMARK_NAME:
            case BLOCK_STRANGER:
            case BLOCK_FRIEND:
            case UNBLOCK_USER:
            case HIDE_BLOCKED_USER:
                long userId = event.getUserId();
                EventData.Builder b = EventData.newBuilder().setSendUserId(userId).setData(request);
                b.addReceiveUserIds(userId); // notify self for multi-device sync
                MessagePayload messageData =
                        MessagePayload.newBuilder().setEventData(b.build()).build();
                this.producer.send(this.topicName, String.valueOf(userId), messageData);
                return SendEventResp.newBuilder().setSuccess(true).build();
            default:
                throw new IllegalArgumentException("Unknown relation event type");
        }
    }

    /**
     * Check if the target user is a bot.
     */
    private boolean isBot(long userId) {
        try {
            UserProfile profile = storageFacade.getUserProfile(userId);
            return profile.isBot();
        } catch (NotFoundUserProfileException e) {
            return false;
        }
    }
}
