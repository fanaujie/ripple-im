package com.fanaujie.ripple.storage.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LastMessageInfo {
    private String text;
    private long timestamp;
    private String messageId;

    public LastMessageInfo(String text, long timestamp) {
        this.text = text;
        this.timestamp = timestamp;
        this.messageId = null;
    }
}
