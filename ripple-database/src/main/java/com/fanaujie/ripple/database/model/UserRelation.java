package com.fanaujie.ripple.database.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRelation {
    public static final byte FRIEND_FLAG = 0x01;
    public static final byte BLOCKED_FLAG = 0x02;
    public static final byte HIDDEN_FLAG = 0x04;
    
    private long id;
    private long sourceUserId;
    private long targetUserId;
    private String targetUserDisplayName;
    private byte relationFlags;
    private Instant createdTime;
    private Instant updatedTime;
}