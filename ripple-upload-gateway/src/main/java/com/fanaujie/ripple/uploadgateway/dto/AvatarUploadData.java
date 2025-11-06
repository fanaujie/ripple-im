package com.fanaujie.ripple.uploadgateway.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Avatar upload data")
public class AvatarUploadData {
    @Schema(description = "URL of the uploaded avatar image", example = "https://cdn.example.com/avatars/a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e.png")
    private String avatarUrl;
}
