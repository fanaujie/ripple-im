package com.fanaujie.ripple.storage.model;

import lombok.Getter;

@Getter
public enum ConversationOperation {
    CREATE_CONVERSATION(1),
    READ_MESSAGES(2),
    UPDATE_CONVERSATION_NAME(3),
    UPDATE_CONVERSATION_AVATAR(4),
    UPDATE_CONVERSATION_NAME_AVATAR(5),
    REMOVE_CONVERSATION(6),
    UPDATE_BOT_SESSION_ID(7);

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
