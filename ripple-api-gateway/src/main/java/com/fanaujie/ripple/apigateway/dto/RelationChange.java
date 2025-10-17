package com.fanaujie.ripple.apigateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RelationChange {
    private String operation; // ADD, UPDATE, DELETE
    private String userId;
    private String nickName;
    private String avatar;
    private String remarkName;
    private Integer relationFlags;
}
