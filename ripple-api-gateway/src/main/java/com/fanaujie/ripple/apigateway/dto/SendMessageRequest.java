package com.fanaujie.ripple.apigateway.dto;

import com.fanaujie.ripple.apigateway.service.utils.FileTypeUtils;
import com.fanaujie.ripple.protobuf.msgapiserver.SingleMessageContent;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class SendMessageRequest {
    @NotNull(message = "senderId is required")
    private String senderId;

    private String conversationId;
    private String receiverId;
    private String groupId;
    private String textContent;
    private String fileUrl;
    private String fileName;
    private String sessionId;  // Required for bot messages

    public SingleMessageContent toSingleMessageContent() {
        SingleMessageContent.Builder builder = SingleMessageContent.newBuilder();
        builder.setText(this.textContent != null ? this.textContent : "");
        builder.setFileUrl(this.fileUrl != null ? this.fileUrl : "");
        builder.setFileName(this.fileName != null ? this.fileName : "");
        builder.setText(getSummaryText(builder.getText(), builder.getFileName(), this.senderId));
        return builder.build();
    }

    private String getSummaryText(String text, String fileName, String senderId) {
        if (!fileName.isEmpty()) {
            return FileTypeUtils.generateFilePreviewText(senderId, fileName);
        }
        return text;
    }
}
