package com.fanaujie.ripple.storage.model;

import lombok.NoArgsConstructor;
import lombok.Data;
import lombok.AllArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class Message {
    private String conversationId;
    private long messageId;
    private long senderId;
    private long receiverId; // 0 for group messages
    private long groupId; // 0 for single messages
    private long sendTimestamp;
    private String text;
    private String fileUrl;
    private String fileName;
}
