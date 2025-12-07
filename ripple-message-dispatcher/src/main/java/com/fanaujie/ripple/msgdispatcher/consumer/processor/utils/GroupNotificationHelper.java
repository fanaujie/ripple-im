package com.fanaujie.ripple.msgdispatcher.consumer.processor.utils;

import com.fanaujie.ripple.communication.msgqueue.GenericProducer;
import com.fanaujie.ripple.protobuf.msgapiserver.GroupCommandMessageContent;
import com.fanaujie.ripple.protobuf.msgapiserver.SendGroupCommandReq;
import com.fanaujie.ripple.protobuf.msgapiserver.SendMessageReq;
import com.fanaujie.ripple.protobuf.profileupdater.GroupMemberBatchInsertData;
import com.fanaujie.ripple.protobuf.profileupdater.ProfileUpdatePayload;
import com.fanaujie.ripple.protobuf.push.MessageType;
import com.fanaujie.ripple.protobuf.push.PushMessage;
import com.fanaujie.ripple.protobuf.push.PushMessageData;
import com.fanaujie.ripple.storage.model.UserProfile;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import com.fanaujie.ripple.storage.service.utils.ConversationUtils;

import java.util.List;
import java.util.stream.Collectors;

import static com.fanaujie.ripple.storage.model.GroupCommandType.GROUP_COMMAND_TYPE_MEMBER_JOIN;

public class GroupNotificationHelper {
    private static final int MAX_BATCH_SIZE = 5;
    private final GenericProducer<String, ProfileUpdatePayload> profileUpdateProducer;
    private final String profileUpdateTopic;
    private final GenericProducer<String, PushMessage> pushMessageProducer;
    private final String pushTopic;
    private final RippleStorageFacade storageFacade;

    public GroupNotificationHelper(
            GenericProducer<String, ProfileUpdatePayload> profileUpdateProducer,
            String profileUpdateTopic,
            GenericProducer<String, PushMessage> pushMessageProducer,
            String pushTopic,
            RippleStorageFacade storageFacade) {
        this.profileUpdateProducer = profileUpdateProducer;
        this.profileUpdateTopic = profileUpdateTopic;
        this.pushMessageProducer = pushMessageProducer;
        this.pushTopic = pushTopic;
        this.storageFacade = storageFacade;
    }

    public void sendBatchedProfileUpdates(
            long groupId,
            String groupName,
            String groupAvatar,
            List<UserProfile> newMembers,
            long creatorUserId) {
        int totalBatches = (int) Math.ceil(newMembers.size() / (float) MAX_BATCH_SIZE);

        for (int i = 0; i < totalBatches; i++) {
            int start = i * MAX_BATCH_SIZE;
            int end = Math.min((i + 1) * MAX_BATCH_SIZE, newMembers.size());
            List<Long> batchMembers =
                    newMembers.subList(start, end).stream().map(UserProfile::getUserId).toList();
            GroupMemberBatchInsertData batchData =
                    GroupMemberBatchInsertData.newBuilder()
                            .setGroupId(groupId)
                            .setGroupName(groupName)
                            .setGroupAvatar(groupAvatar)
                            .addAllMemberIds(batchMembers)
                            .setBatchIndex(i)
                            .setTotalBatches(totalBatches)
                            .setCreatorUserId(creatorUserId)
                            .build();

            ProfileUpdatePayload payload =
                    ProfileUpdatePayload.newBuilder()
                            .setGroupMemberBatchInsertData(batchData)
                            .build();
            this.profileUpdateProducer.send(
                    this.profileUpdateTopic, String.valueOf(groupId), payload);
        }
    }

    public void writeJoinGroupCommandMessageAndPush(
            SendGroupCommandReq sendGroupCommandReq,
            long groupId,
            List<UserProfile> newMembers,
            List<Long> allRecipientIds) {

        String memberNames =
                newMembers.stream().map(UserProfile::getNickName).collect(Collectors.joining("、"));
        String commandText = memberNames + " joined the group";
        String conversationId = ConversationUtils.generateGroupConversationId(groupId);
        this.storageFacade.saveGroupCommandMessage(
                conversationId,
                sendGroupCommandReq.getMessageId(),
                sendGroupCommandReq.getSenderId(),
                groupId,
                sendGroupCommandReq.getSendTimestamp(),
                GROUP_COMMAND_TYPE_MEMBER_JOIN.getValue(),
                commandText);
        GroupCommandMessageContent.Builder commandContentBuilder =
                GroupCommandMessageContent.newBuilder()
                        .setCommandType(GROUP_COMMAND_TYPE_MEMBER_JOIN.getValue())
                        .setText(commandText);
        SendMessageReq sendMessageReq =
                SendMessageReq.newBuilder()
                        .setSenderId(sendGroupCommandReq.getSenderId())
                        .setConversationId(conversationId)
                        .setGroupId(groupId)
                        .setMessageId(sendGroupCommandReq.getMessageId())
                        .setSendTimestamp(sendGroupCommandReq.getSendTimestamp())
                        .setGroupCommandMessageContent(commandContentBuilder.build())
                        .build();

        PushMessageData messageData =
                PushMessageData.newBuilder()
                        .setMessageType(MessageType.MESSAGE_TYPE_GROUP_MESSAGE)
                        .setSendUserId(sendGroupCommandReq.getSenderId())
                        .addAllReceiveUserIds(allRecipientIds)
                        .setData(sendMessageReq)
                        .build();
        PushMessage pushMessage = PushMessage.newBuilder().setMessageData(messageData).build();
        this.pushMessageProducer.send(this.pushTopic, String.valueOf(groupId), pushMessage);
    }
}
