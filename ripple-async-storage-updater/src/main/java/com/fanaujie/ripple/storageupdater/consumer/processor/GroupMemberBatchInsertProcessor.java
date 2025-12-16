package com.fanaujie.ripple.storageupdater.consumer.processor;

import com.fanaujie.ripple.communication.msgqueue.GenericProducer;
import com.fanaujie.ripple.communication.processor.Processor;
import com.fanaujie.ripple.protobuf.msgapiserver.GroupCommandMessageContent;
import com.fanaujie.ripple.protobuf.msgapiserver.SendMessageReq;
import com.fanaujie.ripple.protobuf.storageupdater.GroupMemberBatchInsertData;
import com.fanaujie.ripple.protobuf.storageupdater.StorageUpdatePayload;
import com.fanaujie.ripple.protobuf.push.MessageType;
import com.fanaujie.ripple.protobuf.push.PushMessage;
import com.fanaujie.ripple.protobuf.push.PushMessageData;
import com.fanaujie.ripple.storage.service.impl.CachingUserProfileStorage;
import com.fanaujie.ripple.storage.model.Message;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import com.fanaujie.ripple.storage.service.utils.ConversationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static com.fanaujie.ripple.storage.model.GroupCommandType.GROUP_COMMAND_TYPE_MEMBER_JOIN;

public class GroupMemberBatchInsertProcessor implements Processor<StorageUpdatePayload, Void> {
    private static final Logger logger =
            LoggerFactory.getLogger(GroupMemberBatchInsertProcessor.class);

    private final RippleStorageFacade storageFacade;
    private final ExecutorService executorService;
    private final GenericProducer<String, PushMessage> pushMessageProducer;
    private final String pushTopic;
    private final CachingUserProfileStorage userProfileCache;

    public GroupMemberBatchInsertProcessor(
            RippleStorageFacade storageFacade,
            ExecutorService executorService,
            GenericProducer<String, PushMessage> pushMessageProducer,
            String pushTopic,
            CachingUserProfileStorage userProfileCache) {
        this.storageFacade = storageFacade;
        this.executorService = executorService;
        this.pushMessageProducer = pushMessageProducer;
        this.pushTopic = pushTopic;
        this.userProfileCache = userProfileCache;
    }

    @Override
    public Void handle(StorageUpdatePayload payload) throws Exception {
        GroupMemberBatchInsertData batchData = payload.getGroupMemberBatchInsertData();
        long groupId = batchData.getGroupId();

        ArrayList<Future<Void>> futures = new ArrayList<>();
        for (Long memberId : batchData.getMemberIdsList()) {
            Future<Void> future =
                    executorService.submit(
                            () -> {
                                storageFacade.createUserGroupAndConversation(
                                        memberId,
                                        groupId,
                                        batchData.getGroupName(),
                                        batchData.getGroupAvatar(),
                                        batchData.getSendTimestamp());
                                return null;
                            });
            futures.add(future);
        }
        for (Future<Void> f : futures) {
            f.get();
        }

        if (batchData.getBatchIndex() + 1 == batchData.getTotalBatches()) {
            String conversationId = ConversationUtils.generateGroupConversationId(groupId);
            sendPushNotification(batchData, conversationId, batchData.getMessageId());
        }

        return null;
    }

    private void sendPushNotification(
            GroupMemberBatchInsertData batchData, String conversationId, long messageId)
            throws Exception {
        long groupId = batchData.getGroupId();

        List<Long> allRecipientIds = storageFacade.getGroupMemberIds(groupId);
        Message msg = this.storageFacade.getMessages(conversationId, messageId);
        GroupCommandMessageContent commandContent =
                GroupCommandMessageContent.newBuilder()
                        .setCommandType(GROUP_COMMAND_TYPE_MEMBER_JOIN.getValue())
                        .setText(msg.getCommandData())
                        .build();
        SendMessageReq sendMessageReq =
                SendMessageReq.newBuilder()
                        .setSenderId(batchData.getSenderId())
                        .setConversationId(conversationId)
                        .setGroupId(groupId)
                        .setMessageId(batchData.getMessageId())
                        .setSendTimestamp(batchData.getSendTimestamp())
                        .setGroupCommandMessageContent(commandContent)
                        .build();

        PushMessageData messageData =
                PushMessageData.newBuilder()
                        .setMessageType(MessageType.MESSAGE_TYPE_GROUP_MESSAGE)
                        .setSendUserId(batchData.getSenderId())
                        .addAllReceiveUserIds(allRecipientIds)
                        .setData(sendMessageReq)
                        .build();

        PushMessage pushMessage = PushMessage.newBuilder().setMessageData(messageData).build();
        pushMessageProducer.send(pushTopic, String.valueOf(groupId), pushMessage);
    }
}
