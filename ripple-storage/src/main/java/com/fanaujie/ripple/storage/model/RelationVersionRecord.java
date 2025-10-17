package com.fanaujie.ripple.storage.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelationVersionRecord {
    private long relationUserId;
    private byte operation; // 1: ADD, 2: UPDATE, 3: DELETE
    private String nickName;
    private String avatar;
    private String remarkName;
    private Byte relationFlags;
    private UUID version;
}
