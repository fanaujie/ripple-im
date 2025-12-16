package com.fanaujie.ripple.apigateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GetGroupMembersResponse {
    private int code;
    private String message;
    private GroupMembersData data;

    public static GetGroupMembersResponse success(GroupMembersData data) {
        return new GetGroupMembersResponse(200, "success", data);
    }

    public static GetGroupMembersResponse error(int code, String message) {
        return new GetGroupMembersResponse(code, message, null);
    }
}
