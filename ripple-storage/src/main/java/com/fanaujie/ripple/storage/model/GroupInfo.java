package com.fanaujie.ripple.storage.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class GroupInfo {
    private final long groupId;
    private final String groupName;
    private final String groupAvatar;
    private final List<Long> memberIds;
}
