package com.fanaujie.ripple.storage.model;

import lombok.Getter;

@Getter
public enum MessageType {
    MESSAGE_TYPE_TEXT((byte) 1),
    MESSAGE_TYPE_GROUP_COMMAND((byte) 2);

    private final byte value;

    MessageType(byte value) {
        this.value = value;
    }
}
