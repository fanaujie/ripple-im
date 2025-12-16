package com.fanaujie.ripple.storage.model;

import lombok.Getter;

@Getter
public enum GroupChangeOperation {
    CREATE_GROUP(1),
    DELETE_GROUP(2),
    MEMBER_JOIN(3),
    MEMBER_QUIT(4),
    MEMBER_UPDATE_NAME(5),
    MEMBER_UPDATE_AVATAR(6);

    private final byte value;

    GroupChangeOperation(int value) {
        this.value = (byte) value;
    }

    public static GroupChangeOperation fromValue(byte value) {
        for (GroupChangeOperation op : values()) {
            if (op.value == value) {
                return op;
            }
        }
        throw new IllegalArgumentException("Unknown group change operation: " + value);
    }
}
