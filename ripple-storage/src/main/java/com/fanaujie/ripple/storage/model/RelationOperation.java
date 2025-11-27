package com.fanaujie.ripple.storage.model;

import lombok.Getter;

@Getter
public enum RelationOperation {
    ADD_FRIEND(1),
    UPDATE_FRIEND_REMARK_NAME(2),
    DELETE_FRIEND(3),
    ADD_BLOCK(4),
    DELETE_BLOCK(5),
    UNBLOCK_RESTORE_FRIEND(6),
    HIDE_BLOCK(7),
    UPDATE_FRIEND_NICK_NAME(8),
    UPDATE_FRIEND_AVATAR(9),
    BLOCK_STRANGER(10),
    UPDATE_FRIEND_INFO(11);
    private final byte value;

    RelationOperation(int value) {
        this.value = (byte) value;
    }

    public static RelationOperation fromValue(byte value) {
        for (RelationOperation op : values()) {
            if (op.value == value) {
                return op;
            }
        }
        throw new IllegalArgumentException("Unknown relation operation: " + value);
    }
}
