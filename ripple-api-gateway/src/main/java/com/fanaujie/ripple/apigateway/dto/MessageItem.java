package com.fanaujie.ripple.apigateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageItem {
    private String conversationId;
    private String messageId;
    private String senderId;
    private String receiverId;
    private String groupId;
    private long sendTimestamp;
    private String textContent;
    private String fileUrl;
    private String fileName;
}
