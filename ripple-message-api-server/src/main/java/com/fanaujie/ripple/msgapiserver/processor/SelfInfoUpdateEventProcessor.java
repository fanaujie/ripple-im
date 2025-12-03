package com.fanaujie.ripple.msgapiserver.processor;

import com.fanaujie.ripple.communication.msgqueue.GenericProducer;
import com.fanaujie.ripple.communication.processor.Processor;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventReq;
import com.fanaujie.ripple.protobuf.msgapiserver.SendEventResp;
import com.fanaujie.ripple.protobuf.msgdispatcher.EventData;
import com.fanaujie.ripple.protobuf.msgdispatcher.MessagePayload;
import com.fanaujie.ripple.protobuf.profileupdater.GroupMemberProfileUpdateData;
import com.fanaujie.ripple.protobuf.profileupdater.ProfileUpdatePayload;
import com.fanaujie.ripple.protobuf.profileupdater.RelationProfileUpdateData;

import com.fanaujie.ripple.protobuf.storage.UserIds;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.ExecutorService;

public class SelfInfoUpdateEventProcessor implements Processor<SendEventReq, SendEventResp> {

    private final Logger logger = LoggerFactory.getLogger(SelfInfoUpdateEventProcessor.class);
    private final String topicName;
    private final String profileUpdateTopic;
    private final GenericProducer<String, MessagePayload> producer;
    private final GenericProducer<String, ProfileUpdatePayload> profileUpdateProducer;
    private final ExecutorService executorService;
    private final RippleStorageFacade storageFacade;

    public SelfInfoUpdateEventProcessor(
            String topicName,
            String profileUpdateTopic,
            RippleStorageFacade storageFacade,
            GenericProducer<String, MessagePayload> producer,
            GenericProducer<String, ProfileUpdatePayload> profileUpdateProducer,
            ExecutorService executorService) {
        this.topicName = topicName;
        this.profileUpdateTopic = profileUpdateTopic;
        this.storageFacade = storageFacade;
        this.producer = producer;
        this.profileUpdateProducer = profileUpdateProducer;
        this.executorService = executorService;
    }

    @Override
    public SendEventResp handle(SendEventReq request) throws Exception {
        long userId = request.getSelfInfoUpdateEvent().getUserId();

        EventData.Builder b = EventData.newBuilder().setSendUserId(userId).setData(request);
        b.addReceiveUserIds(userId); // notify self for multi-device sync
        MessagePayload messageData = MessagePayload.newBuilder().setEventData(b.build()).build();
        RelationProfileUpdateData.Builder relationProfileUpdateBuilder =
                RelationProfileUpdateData.newBuilder().setUserId(userId);
        GroupMemberProfileUpdateData.Builder groupMemberProfileUpdateBuilder =
                GroupMemberProfileUpdateData.newBuilder().setUserId(userId);
        switch (request.getSelfInfoUpdateEvent().getEventType()) {
            case UPDATE_NICK_NAME:
                String nickName = request.getSelfInfoUpdateEvent().getNickName();
                relationProfileUpdateBuilder.setUpdateType(
                        RelationProfileUpdateData.UpdateType.UPDATE_NICKNAME);
                relationProfileUpdateBuilder.setNickname(nickName);
                groupMemberProfileUpdateBuilder.setUpdateType(
                        GroupMemberProfileUpdateData.UpdateType.UPDATE_NICKNAME);
                groupMemberProfileUpdateBuilder.setNickname(nickName);
                break;
            case UPDATE_AVATAR:
                relationProfileUpdateBuilder.setUpdateType(
                        RelationProfileUpdateData.UpdateType.UPDATE_AVATAR);
                groupMemberProfileUpdateBuilder.setUpdateType(
                        GroupMemberProfileUpdateData.UpdateType.UPDATE_AVATAR);
                String newAvatar = request.getSelfInfoUpdateEvent().getAvatar();
                relationProfileUpdateBuilder.setAvatar(newAvatar);
                groupMemberProfileUpdateBuilder.setAvatar(newAvatar);
                break;
            case DELETE_AVATAR:
                relationProfileUpdateBuilder.setUpdateType(
                        RelationProfileUpdateData.UpdateType.DELETE_AVATAR);
                groupMemberProfileUpdateBuilder.setUpdateType(
                        GroupMemberProfileUpdateData.UpdateType.DELETE_AVATAR);
                break;
            default:
                break;
        }
        this.executorService
                .submit(
                        () -> {
                            this.producer.send(this.topicName, String.valueOf(userId), messageData);
                            this.profileUpdateProducer.send(
                                    this.profileUpdateTopic,
                                    String.valueOf(userId),
                                    ProfileUpdatePayload.newBuilder()
                                            .setRelationProfileUpdateData(
                                                    relationProfileUpdateBuilder.build())
                                            .build());
                            this.profileUpdateProducer.send(
                                    this.profileUpdateTopic,
                                    String.valueOf(userId),
                                    ProfileUpdatePayload.newBuilder()
                                            .setGroupMemberProfileUpdateData(
                                                    groupMemberProfileUpdateBuilder.build())
                                            .build());
                        })
                .get();
        return SendEventResp.newBuilder().build();
    }
}
