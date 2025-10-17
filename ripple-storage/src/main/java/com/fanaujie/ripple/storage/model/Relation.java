package com.fanaujie.ripple.storage.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Relation {
    private long sourceUserId;
    private long relationUserId;
    private String relationNickName;
    private String relationAvatar;
    private String relationRemarkName;
    private int relationFlags; // bit0: is_friend, bit1: is_blocked, bit2: is_hidden
}
