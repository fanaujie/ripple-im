package com.fanaujie.ripple.database.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelationWithProfile {
    private long sourceUserId;
    private long targetUserId;
    private String targetUserDisplayName;
    private int relationFlags;
    // UserProfile fields
    private String targetNickName;
    private String targetAvatar;
}
