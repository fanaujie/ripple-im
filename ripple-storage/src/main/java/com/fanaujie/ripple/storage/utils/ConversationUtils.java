package com.fanaujie.ripple.storage.utils;

import org.springframework.util.DigestUtils;

public class ConversationUtils {
    public static String generateConversationId(long userId1, long userId2) {
        long minId = Math.min(userId1, userId2);
        long maxId = Math.max(userId1, userId2);
        String temp = minId + "_" + maxId;
        String hash = DigestUtils.md5DigestAsHex(temp.getBytes());
        return hash.substring(0, 16);
    }
}
