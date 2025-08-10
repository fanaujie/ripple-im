package com.fanaujie.ripple.uploadgateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvatarUploadResponse {
    private int code;
    private String message;
    private AvatarUploadData data;
}
