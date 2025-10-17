package com.fanaujie.ripple.storage.model;

import lombok.Getter;

@Getter
public enum RelationOperation {
    ADD(1),
    UPDATE(2),
    DELETE(3);

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
