package com.fanaujie.ripple.storage.model;

import lombok.NoArgsConstructor;
import lombok.Data;
import lombok.AllArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class GroupMember {
    private long groupId;
    private long userId;
}
