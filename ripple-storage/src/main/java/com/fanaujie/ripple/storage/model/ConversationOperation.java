package com.fanaujie.ripple.storage.model;

import lombok.Getter;

@Getter
public enum ConversationOperation {
    CREATE_CONVERSATION(0),
    NEW_MESSAGE(1),
    READ_MESSAGES(2);

    private final byte value;

    ConversationOperation(int value) {
        this.value = (byte) value;
    }

    public static ConversationOperation fromValue(byte value) {
        for (ConversationOperation op : values()) {
            if (op.value == value) {
                return op;
            }
        }
        throw new IllegalArgumentException("Unknown conversation operation: " + value);
    }
}
