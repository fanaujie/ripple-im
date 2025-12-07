package com.fanaujie.ripple.storage.cache;

import lombok.Getter;

@Getter
public enum CachePrefixKey {
    FRIEND_IDS("FRIEND_IDS:"),
    USER_PROFILE("USER_PROFILE:");
    private final String value;

    CachePrefixKey(String value) {
        this.value = value;
    }

    public String withSuffix(String suffix) {
        return value + suffix;
    }
}
