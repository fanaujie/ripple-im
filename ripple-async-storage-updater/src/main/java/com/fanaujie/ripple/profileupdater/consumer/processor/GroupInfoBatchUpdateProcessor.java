package com.fanaujie.ripple.profileupdater.consumer.processor;

import com.fanaujie.ripple.communication.msgqueue.GenericProducer;
import com.fanaujie.ripple.communication.processor.Processor;
import com.fanaujie.ripple.protobuf.msgapiserver.GroupCommandMessageContent;
import com.fanaujie.ripple.protobuf.msgapiserver.SendMessageReq;
import com.fanaujie.ripple.protobuf.profileupdater.GroupInfoBatchUpdateData;
import com.fanaujie.ripple.protobuf.profileupdater.ProfileUpdatePayload;
import com.fanaujie.ripple.protobuf.push.MessageType;
import com.fanaujie.ripple.protobuf.push.PushMessage;
import com.fanaujie.ripple.protobuf.push.PushMessageData;
import com.fanaujie.ripple.storage.model.Message;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import com.fanaujie.ripple.storage.service.utils.ConversationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static com.fanaujie.ripple.storage.model.GroupCommandType.GROUP_COMMAND_TYPE_INFO_UPDATE;

public class GroupInfoBatchUpdateProcessor implements Processor<ProfileUpdatePayload, Void> {
    private static final Logger logger =
            LoggerFactory.getLogger(GroupInfoBatchUpdateProcessor.class);

    private final RippleStorageFacade storageFacade;
    private final ExecutorService executorService;
    private final GenericProducer<String, PushMessage> pushMessageProducer;
    private final String pushTopic;

    public GroupInfoBatchUpdateProcessor(
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
        GroupInfoBatchUpdateData batchData = payload.getGroupInfoBatchUpdateData();
        List<Future<Void>> futures = new ArrayList<>();

        for (Long memberId : batchData.getMemberIdsList()) {
            Future<Void> future =
                    executorService.submit(
                            () -> {
                                switch (batchData.getUpdateType()) {
                                    case UPDATE_NAME:
                                        storageFacade.updateUserGroupName(
                                                memberId,
                                                batchData.getGroupId(),
                                                batchData.getGroupName(),
                                                batchData.getSendTimestamp());
                                        break;
                                    case UPDATE_AVATAR:
                                        storageFacade.updateUserGroupAvatar(
                                                memberId,
                                                batchData.getGroupId(),
                                                batchData.getGroupAvatar(),
                                                batchData.getSendTimestamp());
                                        break;
                                    default:
                                        logger.error(
                                                "Unknown update type: {} for group {}",
                                                batchData.getUpdateType(),
                                                batchData.getGroupId());
                                        throw new InvalidParameterException(
                                                "Unknown update type: "
                                                        + batchData.getUpdateType());
                                }
                                return null;
                            });
            futures.add(future);
        }

        for (Future<Void> f : futures) {
            f.get();
        }

        // Send push notification on the last batch
        if (batchData.getBatchIndex() + 1 == batchData.getTotalBatches()) {
            sendPushNotification(batchData);
        }

        return null;
    }

    private void sendPushNotification(GroupInfoBatchUpdateData batchData) throws Exception {
        long groupId = batchData.getGroupId();
        long messageId = batchData.getMessageId();
        long senderId = batchData.getSenderId();
        String conversationId = ConversationUtils.generateGroupConversationId(groupId);

        List<Long> allRecipientIds = storageFacade.getGroupMemberIds(groupId);
        Message msg = this.storageFacade.getMessages(conversationId, messageId);

        GroupCommandMessageContent commandContent =
                GroupCommandMessageContent.newBuilder()
                        .setCommandType(GROUP_COMMAND_TYPE_INFO_UPDATE.getValue())
                        .setText(msg.getCommandData())
                        .build();

        SendMessageReq sendMessageReq =
                SendMessageReq.newBuilder()
                        .setSenderId(senderId)
                        .setConversationId(conversationId)
                        .setGroupId(groupId)
                        .setMessageId(messageId)
                        .setSendTimestamp(batchData.getSendTimestamp())
                        .setGroupCommandMessageContent(commandContent)
                        .build();

        PushMessageData messageData =
                PushMessageData.newBuilder()
                        .setMessageType(MessageType.MESSAGE_TYPE_GROUP_MESSAGE)
                        .setSendUserId(senderId)
                        .addAllReceiveUserIds(allRecipientIds)
                        .setData(sendMessageReq)
                        .build();

        PushMessage pushMessage = PushMessage.newBuilder().setMessageData(messageData).build();
        pushMessageProducer.send(pushTopic, String.valueOf(groupId), pushMessage);
    }
}
