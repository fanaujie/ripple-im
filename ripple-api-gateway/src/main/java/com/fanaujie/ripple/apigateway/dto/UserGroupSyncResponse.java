package com.fanaujie.ripple.apigateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserGroupSyncResponse {
    private int code;
    private String message;
    private UserGroupSyncData data;

    public static UserGroupSyncResponse success(UserGroupSyncData data) {
        return new UserGroupSyncResponse(200, "success", data);
    }

    public static UserGroupSyncResponse error(int code, String message) {
        return new UserGroupSyncResponse(code, message, null);
    }
}
