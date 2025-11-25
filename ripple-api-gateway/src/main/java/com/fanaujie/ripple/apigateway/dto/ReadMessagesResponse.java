package com.fanaujie.ripple.apigateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReadMessagesResponse {
    private int code;
    private String message;
    private ReadMessagesData data;

    public static ReadMessagesResponse success(ReadMessagesData data) {
        return new ReadMessagesResponse(200, "success", data);
    }

    public static ReadMessagesResponse error(int code, String message) {
        return new ReadMessagesResponse(code, message, null);
    }
}
