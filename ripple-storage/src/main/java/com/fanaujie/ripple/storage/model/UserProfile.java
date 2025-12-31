package com.fanaujie.ripple.storage.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {
    public static final byte STATUS_NORMAL = 0;
    public static final byte STATUS_FORBIDDEN = 1;
    public static final byte STATUS_DELETED = 2;

    public static final byte USER_TYPE_USER = 0;
    public static final byte USER_TYPE_BOT = 1;

    private long userId;
    private String account;
    private String nickName;
    private String avatar;
    private byte userType;

    public UserProfile(long userId, String account, String nickName, String avatar) {
        this.userId = userId;
        this.account = account;
        this.nickName = nickName;
        this.avatar = avatar;
        this.userType = USER_TYPE_USER;
    }

    public boolean isBot() {
        return userType == USER_TYPE_BOT;
    }
}
