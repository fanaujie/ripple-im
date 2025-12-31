package com.fanaujie.ripple.storage.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Date;

/**
 * DTO that combines UserProfile (identity) and BotConfig (configuration) for API responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BotInfo {
    // Identity from UserProfile
    private Long botId;
    private String name;
    private String avatar;

    // Configuration from BotConfig
    private String endpoint;
    private String secret;
    private boolean requireAuth;
    private String authConfig;
    private boolean enabled;
    private String category;
    private String description;
    private String developerName;
    private Date createdAt;

    /**
     * Creates a BotInfo from UserProfile and BotConfig.
     */
    public static BotInfo from(UserProfile profile, BotConfig config) {
        return BotInfo.builder()
                .botId(profile.getUserId())
                .name(profile.getNickName())
                .avatar(profile.getAvatar())
                .endpoint(config.getEndpoint())
                .secret(config.getSecret())
                .requireAuth(config.isRequireAuth())
                .authConfig(config.getAuthConfig())
                .enabled(config.isEnabled())
                .category(config.getCategory())
                .description(config.getDescription())
                .developerName(config.getDeveloperName())
                .createdAt(config.getCreatedAt())
                .build();
    }
}
