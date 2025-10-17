package com.fanaujie.ripple.apigateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private String userId;
    private String nickName;
    private String avatar;
    private String remarkName;
    private int relation; // bit0: is_friend, bit1: is_blocked, bit2: is_hidden
}
