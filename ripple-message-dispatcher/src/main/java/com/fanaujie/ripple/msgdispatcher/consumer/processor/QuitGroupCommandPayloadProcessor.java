package com.fanaujie.ripple.msgdispatcher.consumer.processor;

import com.fanaujie.ripple.communication.processor.Processor;
import com.fanaujie.ripple.communication.msgqueue.GenericProducer;
import com.fanaujie.ripple.protobuf.msgapiserver.GroupCommandMessageContent;
import com.fanaujie.ripple.protobuf.msgapiserver.SendGroupCommandReq;
import com.fanaujie.ripple.protobuf.msgapiserver.SendMessageReq;
import com.fanaujie.ripple.protobuf.msgdispatcher.GroupCommandData;
import com.fanaujie.ripple.protobuf.push.MessageType;
import com.fanaujie.ripple.protobuf.push.PushMessage;
import com.fanaujie.ripple.protobuf.push.PushMessageData;
import com.fanaujie.ripple.storage.model.GroupMemberInfo;
import com.fanaujie.ripple.storage.service.ConversationStateFacade;
import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import com.fanaujie.ripple.storage.service.utils.ConversationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.fanaujie.ripple.protobuf.msgapiserver.SendGroupCommandReq.CommandContentCase.GROUP_QUIT_COMMAND;
import static com.fanaujie.ripple.storage.model.GroupCommandType.GROUP_COMMAND_TYPE_MEMBER_QUIT;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class QuitGroupCommandPayloadProcessor implements Processor<GroupCommandData, Void> {
    private final Logger logger = LoggerFactory.getLogger(QuitGroupCommandPayloadProcessor.class);
    private final RippleStorageFacade storageFacade;
    private final ConversationStateFacade conversationStorage;
    private final GenericProducer<String, PushMessage> pushMessageProducer;
    private final String pushTopic;

    public QuitGroupCommandPayloadProcessor(
            RippleStorageFacade storageFacade,
            ConversationStateFacade conversationStorage,
            GenericProducer<String, PushMessage> pushMessageProducer,
            String pushTopic) {
        this.storageFacade = storageFacade;
        this.conversationStorage = conversationStorage;
        this.pushMessageProducer = pushMessageProducer;
        this.pushTopic = pushTopic;
    }

    @Override
    public Void handle(GroupCommandData groupCommandData) throws Exception {
        SendGroupCommandReq sendGroupCommandReq = groupCommandData.getData();
        if (sendGroupCommandReq.getCommandContentCase() == GROUP_QUIT_COMMAND) {
            this.updateGroupStorage(sendGroupCommandReq);
            return null;
        }
        throw new IllegalArgumentException(
                "Unknown message type for QuitGroupCommandPayloadProcessor");
    }

    private void updateGroupStorage(SendGroupCommandReq sendGroupCommandReq) throws Exception {
        long groupId = sendGroupCommandReq.getGroupId();
        long userId = sendGroupCommandReq.getSenderId();

        String conversationId = ConversationUtils.generateGroupConversationId(groupId);
        List<GroupMemberInfo> groupMembers = this.storageFacade.getGroupMembersInfo(groupId);
        String quitterName =
                groupMembers.stream()
                        .filter(m -> m.getUserId() == userId)
                        .findFirst()
                        .map(GroupMemberInfo::getName)
                        .orElse("User");

        String commandText = quitterName + " left the group";
        this.storageFacade.removeUserGroup(userId, groupId, sendGroupCommandReq.getSendTimestamp());
        this.storageFacade.removeGroupMember(
                groupId, userId, sendGroupCommandReq.getSendTimestamp());
        this.storageFacade.removeGroupConversation(userId, groupId);
        this.storageFacade.saveGroupCommandMessage(
                conversationId,
                sendGroupCommandReq.getMessageId(),
                userId,
                groupId,
                sendGroupCommandReq.getSendTimestamp(),
                GROUP_COMMAND_TYPE_MEMBER_QUIT.getValue(),
                commandText);

        conversationStorage.batchUpdateConversation(
                Collections.emptyList(),
                conversationId,
                commandText,
                sendGroupCommandReq.getSendTimestamp(),
                String.valueOf(sendGroupCommandReq.getMessageId()),
                false);

        GroupCommandMessageContent.Builder commandContentBuilder =
                GroupCommandMessageContent.newBuilder()
                        .setCommandType(GROUP_COMMAND_TYPE_MEMBER_QUIT.getValue())
                        .setText(commandText);

        SendMessageReq sendMessageReq =
                SendMessageReq.newBuilder()
                        .setSenderId(userId)
                        .setConversationId(conversationId)
                        .setGroupId(groupId)
                        .setMessageId(sendGroupCommandReq.getMessageId())
                        .setSendTimestamp(sendGroupCommandReq.getSendTimestamp())
                        .setGroupCommandMessageContent(commandContentBuilder.build())
                        .build();
        List<Long> recipientIds =
                groupMembers.stream().map(GroupMemberInfo::getUserId).collect(Collectors.toList());
        PushMessageData messageData =
                PushMessageData.newBuilder()
                        .setMessageType(MessageType.MESSAGE_TYPE_GROUP_MESSAGE)
                        .setSendUserId(userId)
                        .addAllReceiveUserIds(recipientIds)
                        .setData(sendMessageReq)
                        .build();
        PushMessage pushMessage = PushMessage.newBuilder().setMessageData(messageData).build();
        this.pushMessageProducer.send(this.pushTopic, String.valueOf(groupId), pushMessage);
    }
}
