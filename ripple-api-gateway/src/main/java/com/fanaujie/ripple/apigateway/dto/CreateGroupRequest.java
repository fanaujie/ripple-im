package com.fanaujie.ripple.apigateway.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreateGroupRequest {
    @NotNull(message = "senderId is required")
    private String senderId;

    @NotEmpty(message = "groupName is required")
    @Size(max = 100, message = "groupName must not exceed 100 characters")
    private String groupName;

    private String groupAvatar;

    @NotNull(message = "memberIds is required")
    @NotEmpty(message = "memberIds must contain at least one member")
    private List<Long> memberIds;
}
