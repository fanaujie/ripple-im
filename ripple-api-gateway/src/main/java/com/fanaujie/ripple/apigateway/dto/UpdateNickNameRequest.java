package com.fanaujie.ripple.apigateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateNickNameRequest {
    @NotBlank(message = "Nickname cannot be blank")
    @Size(min = 1, max = 50, message = "Nickname must be between 1 and 50 characters")
    private String nickName;
}
