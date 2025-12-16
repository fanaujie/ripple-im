package com.fanaujie.ripple.apigateway.dto;

import com.fanaujie.ripple.storage.model.Message;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageItem {
    private String senderId;
    private String receiverId;
    private String groupId;
    private String messageId;
    private String sendTimestamp;
    private String conversationId;
    private int messageType;
    private String text;
    private String fileUrl;
    private String fileName;
    private int commandType;
    private String commandData;

    public MessageItem(Message message) {
        this.senderId = String.valueOf(message.getSenderId());
        this.receiverId =
                message.getReceiverId() == 0 ? null : String.valueOf(message.getReceiverId());
        this.groupId = message.getGroupId() == 0 ? null : String.valueOf(message.getGroupId());
        this.messageId = String.valueOf(message.getMessageId());
        this.sendTimestamp = String.valueOf(message.getSendTimestamp());
        this.conversationId = message.getConversationId();
        this.messageType = message.getMessageType();
        this.text = message.getText();
        this.fileUrl = message.getFileUrl();
        this.fileName = message.getFileName();
        this.commandType = message.getCommandType();
        this.commandData = message.getCommandData();
    }
}
