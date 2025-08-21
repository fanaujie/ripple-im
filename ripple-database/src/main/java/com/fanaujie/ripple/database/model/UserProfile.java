package com.fanaujie.ripple.database.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {
    private long userId;
    private Integer userType;
    private String nickName;
    private String avatar;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
