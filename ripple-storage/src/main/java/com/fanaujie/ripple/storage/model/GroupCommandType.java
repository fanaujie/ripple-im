package com.fanaujie.ripple.storage.model;

import lombok.Getter;

@Getter
public enum GroupCommandType {
    GROUP_COMMAND_TYPE_MEMBER_JOIN((byte) 1),
    GROUP_COMMAND_TYPE_MEMBER_QUIT((byte) 2),
    GROUP_COMMAND_TYPE_INFO_UPDATE((byte) 3);
    private final byte value;

    GroupCommandType(byte value) {
        this.value = value;
    }
}
