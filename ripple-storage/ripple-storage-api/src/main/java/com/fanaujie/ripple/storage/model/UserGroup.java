package com.fanaujie.ripple.storage.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserGroup {
    private final long groupId;
    private final String groupName;
    private final String groupAvatar;
}
