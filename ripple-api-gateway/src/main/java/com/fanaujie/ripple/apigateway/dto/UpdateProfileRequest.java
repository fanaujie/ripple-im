package com.fanaujie.ripple.apigateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to update user profile")
public class UpdateProfileRequest {

    @Schema(description = "New nickname for the user", example = "John Doe")
    private String nickname;
}
