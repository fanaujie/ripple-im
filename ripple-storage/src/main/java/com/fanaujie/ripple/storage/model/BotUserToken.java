package com.fanaujie.ripple.storage.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BotUserToken {
    private Long botId;
    private Long userId;
    private String accessToken;
    private String refreshToken;
    private Date expiresAt;
}
