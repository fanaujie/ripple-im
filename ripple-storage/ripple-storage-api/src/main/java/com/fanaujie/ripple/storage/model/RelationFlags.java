package com.fanaujie.ripple.storage.model;

import lombok.Getter;

@Getter
public enum RelationFlags {
    FRIEND(1), // bit0: is_friend
    BLOCKED(1 << 1), // bit1: is_blocked
    HIDDEN(1 << 2); // bit2: is_hidden

    private final byte value;

    RelationFlags(int value) {
        this.value = (byte) value;
    }

    public boolean isSet(byte flags) {
        return (flags & value) != 0;
    }

    public static byte setFlag(byte flags, RelationFlags flag) {
        return (byte) (flags | flag.getValue());
    }

    public static byte clearFlag(byte flags, RelationFlags flag) {
        return (byte) (flags & ~flag.getValue());
    }
}
