package com.fanaujie.ripple.apigateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationsData {
    private List<ConversationItem> conversations;
    private String nextPageToken;
    private boolean hasMore;
}
