package com.fanaujie.ripple.storage.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelationVersionChange {
    private long relationUserId;
    private byte operation;
    private String nickName;
    private String avatar;
    private String remarkName;
    private Byte relationFlags;
    private String version;
}
