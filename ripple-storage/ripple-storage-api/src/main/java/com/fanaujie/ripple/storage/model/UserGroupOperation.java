package com.fanaujie.ripple.storage.model;

import lombok.Getter;

@Getter
public enum UserGroupOperation {
    JOIN(1),
    QUIT(2),
    UPDATE_GROUP_NAME(3),
    UPDATE_GROUP_AVATAR(4);

    private final byte value;

    UserGroupOperation(int value) {
        this.value = (byte) value;
    }

    public static UserGroupOperation fromValue(byte value) {
        for (UserGroupOperation op : values()) {
            if (op.value == value) {
                return op;
            }
        }
        throw new IllegalArgumentException("Unknown user group operation: " + value);
    }
}
