package com.fanaujie.ripple.storage.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Date;

/**
 * Bot configuration data. Identity info (name, avatar) is stored in UserProfile.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BotConfig {
    private Long botId;           // Same as user_id in user_profile
    private String endpoint;
    private String secret;
    private boolean requireAuth;
    private String authConfig;
    private boolean enabled;
    private String category;
    private String description;
    private String developerName;
    private Date createdAt;
}
