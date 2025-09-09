package com.fanaujie.ripple.database.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelationWithProfile {
    private long id;
    private long sourceUserId;
    private long targetUserId;
    private String targetUserDisplayName;
    private byte relationFlags;
    private Instant createdTime;
    private Instant updatedTime;

    // UserProfile fields
    private String targetNickName;
    private String targetAvatar;
}
