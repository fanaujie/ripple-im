package com.fanaujie.ripple.storage.cache;

public enum RelationCachePrefixKey {
    FRIEND_IDS("FRIEND_IDS:");

    private String value;

    RelationCachePrefixKey(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
