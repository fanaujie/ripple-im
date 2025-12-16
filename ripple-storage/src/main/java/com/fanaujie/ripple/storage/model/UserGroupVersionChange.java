package com.fanaujie.ripple.storage.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserGroupVersionChange {
    private long groupId;
    private byte operation; // 1: join, 2: quit, 3: group_info_update
    private String groupName;
    private String groupAvatar;
    private String version;
}
