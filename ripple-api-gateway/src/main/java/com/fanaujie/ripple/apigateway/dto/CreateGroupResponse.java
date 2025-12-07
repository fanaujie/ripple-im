package com.fanaujie.ripple.apigateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateGroupResponse {
    private int code;
    private String message;
    private GroupData data;

    public static CreateGroupResponse success(String groupId) {
        return new CreateGroupResponse(200, "success", new GroupData(groupId));
    }

    public static CreateGroupResponse error(int code, String message) {
        return new CreateGroupResponse(code, message, null);
    }
}
