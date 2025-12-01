package com.fanaujie.ripple.storage.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UpdateFriendAvatarResult {
    private boolean conversationUpdated;
    private String conversationId;
}
