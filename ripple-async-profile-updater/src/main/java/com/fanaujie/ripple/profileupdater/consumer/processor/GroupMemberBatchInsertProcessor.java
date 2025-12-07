package com.fanaujie.ripple.profileupdater.consumer.processor;

import com.fanaujie.ripple.communication.msgqueue.GenericProducer;
import com.fanaujie.ripple.communication.processor.Processor;
import com.fanaujie.ripple.protobuf.profileupdater.GroupMemberBatchInsertData;
import com.fanaujie.ripple.protobuf.profileupdater.ProfileUpdatePayload;
import com.fanaujie.ripple.protobuf.push.MultiNotifications;
import com.fanaujie.ripple.protobuf.push.PushEventData;
import com.fanaujie.ripple.protobuf.push.PushMessage;
import com.fanaujie.ripple.protobuf.push.UserNotificationType;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class GroupMemberBatchInsertProcessor implements Processor<ProfileUpdatePayload, Void> {
    private static final Logger logger =
            LoggerFactory.getLogger(GroupMemberBatchInsertProcessor.class);

    private final RippleStorageFacade storageFacade;
    private final ExecutorService executorService;
    private final GenericProducer<String, PushMessage> pushMessageProducer;
    private final String pushTopic;

    public GroupMemberBatchInsertProcessor(
            RippleStorageFacade storageFacade,
            ExecutorService executorService,
            GenericProducer<String, PushMessage> pushMessageProducer,
            String pushTopic) {
        this.storageFacade = storageFacade;
        this.executorService = executorService;
        this.pushMessageProducer = pushMessageProducer;
        this.pushTopic = pushTopic;
    }

    @Override
    public Void handle(ProfileUpdatePayload payload) throws Exception {
        GroupMemberBatchInsertData batchData = payload.getGroupMemberBatchInsertData();
        ArrayList<Future<Void>> futures = new ArrayList<>();
        for (Long memberId : batchData.getMemberIdsList()) {
            Future<Void> future =
                    executorService.submit(
                            () -> {
                                storageFacade.createUserGroupAndConversation(
                                        memberId,
                                        batchData.getGroupId(),
                                        batchData.getGroupName(),
                                        batchData.getGroupAvatar());
                                return null;
                            });
            futures.add(future);
        }
        for (Future<Void> f : futures) {
            f.get();
        }
        return null;
    }

    //    private void sendGroupJoinNotification(Long creatorId, Long memberId) {
    //        MultiNotifications notifications =
    //                MultiNotifications.newBuilder()
    //                        .addNotificationTypes(
    //
    // UserNotificationType.USER_NOTIFICATION_TYPE_CONVERSATION_UPDATE)
    //                        .build();
    //        PushEventData eventData =
    //                PushEventData.newBuilder()
    //                        .setSendUserId(creatorId)
    //                        .putUserNotifications(memberId, notifications)
    //                        .build();
    //        PushMessage pushMessage = PushMessage.newBuilder().setEventData(eventData).build();
    //        pushMessageProducer.send(pushTopic, String.valueOf(memberId), pushMessage);
    //    }
}
