package com.fanaujie.ripple.storage.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SyncFriendInfoResult {
    private boolean conversationUpdated;
    private String conversationId;
}
