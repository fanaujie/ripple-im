package com.fanaujie.ripple.apigateway.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class InviteGroupMemberRequest {
    @NotNull(message = "senderId is required")
    private String senderId;

    @NotNull(message = "newMemberIds is required")
    @NotEmpty(message = "newMemberIds must contain at least one member")
    private List<String> newMemberIds;

    @NotNull(message = "groupName is required")
    private String groupName;

    private String groupAvatar;
}
