package com.fanaujie.ripple.apigateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetUserGroupsResponse {
    private int code;
    private String message;
    private UserGroupsData data;

    public static GetUserGroupsResponse success(UserGroupsData data) {
        return new GetUserGroupsResponse(200, "success", data);
    }

    public static GetUserGroupsResponse error(int code, String message) {
        return new GetUserGroupsResponse(code, message, null);
    }
}
