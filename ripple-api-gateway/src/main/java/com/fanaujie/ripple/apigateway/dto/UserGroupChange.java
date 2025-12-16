package com.fanaujie.ripple.apigateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserGroupChange {
    private String version;
    private int operation; // 1: join, 2: quit, 3: group_info_update
    private String groupId;
    private String groupName;
    private String groupAvatar;
}
