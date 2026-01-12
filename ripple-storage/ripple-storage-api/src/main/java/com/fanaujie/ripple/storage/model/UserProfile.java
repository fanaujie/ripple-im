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

    private long userId;
    private String account;
    private String nickName;
    private String avatar;
}
