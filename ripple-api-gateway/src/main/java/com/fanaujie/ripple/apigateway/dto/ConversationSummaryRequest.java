package com.fanaujie.ripple.apigateway.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSummaryRequest {
    @NotEmpty(message = "conversationIds cannot be empty")
    private List<String> conversationIds;
}
