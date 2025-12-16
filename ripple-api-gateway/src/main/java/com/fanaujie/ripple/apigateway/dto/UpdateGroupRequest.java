package com.fanaujie.ripple.apigateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to update group attributes")
public class UpdateGroupRequest {

    @NotBlank(message = "Sender ID is required")
    @Schema(description = "ID of the user making the request", example = "123456789", required = true)
    private String senderId;

    @Schema(description = "New name for the group", example = "My Group")
    private String name;
}
