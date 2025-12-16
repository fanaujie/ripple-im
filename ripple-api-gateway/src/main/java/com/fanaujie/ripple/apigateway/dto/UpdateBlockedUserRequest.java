package com.fanaujie.ripple.apigateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to update blocked user attributes")
public class UpdateBlockedUserRequest {

    @Schema(description = "Whether to hide the blocked user from the list", example = "true")
    private Boolean hidden;
}
