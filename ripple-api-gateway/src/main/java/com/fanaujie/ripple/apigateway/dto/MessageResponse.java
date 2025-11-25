package com.fanaujie.ripple.apigateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {
    private int code;
    private String message;
    private MessageResponseData data;

    public static MessageResponse success(String messageId) {
        return new MessageResponse(200, "success", new MessageResponseData(messageId));
    }

    public static MessageResponse error(int code, String message) {
        return new MessageResponse(code, message, null);
    }
}
