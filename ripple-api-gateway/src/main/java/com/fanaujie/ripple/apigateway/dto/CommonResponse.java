package com.fanaujie.ripple.apigateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommonResponse {
    private int code;
    private String message;

    public static CommonResponse success() {
        return new CommonResponse(200, "success");
    }

    public static CommonResponse error(int code, String message) {
        return new CommonResponse(code, message);
    }
}
