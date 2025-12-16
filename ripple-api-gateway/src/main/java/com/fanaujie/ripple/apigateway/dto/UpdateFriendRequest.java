package com.fanaujie.ripple.apigateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to update friend attributes")
public class UpdateFriendRequest {

    @Schema(description = "New remark name for the friend", example = "Best Friend")
    private String remarkName;
}
