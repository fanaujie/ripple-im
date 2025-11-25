package com.fanaujie.ripple.apigateway.dto;

import com.fanaujie.ripple.protobuf.msgapiserver.SingleMessageContent;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class SendMessageRequest {
    @NotNull(message = "senderId is required")
    @Positive(message = "senderId must be positive")
    private String senderId;

    @NotNull(message = "conversationId is required")
    private String conversationId;

    @NotNull(message = "receiverId is required")
    @Positive(message = "receiverId must be positive")
    private String receiverId;

    private String textContent;
    private String fileUrl;
    private String fileName;

    public SingleMessageContent toSingleMessageContent() {
        SingleMessageContent.Builder builder = SingleMessageContent.newBuilder();
        builder.setText(this.textContent != null ? this.textContent : "");
        builder.setFileUrl(this.fileUrl != null ? this.fileUrl : "");
        builder.setFileName(this.fileName != null ? this.fileName : "");
        return builder.build();
    }
}
