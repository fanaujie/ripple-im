package com.fanaujie.ripple.uploadgateway.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

@EnableConfigurationProperties
@Configuration
@ConfigurationProperties(prefix = "avatar")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AvatarProperties {
    private DataSize maxSize;
    private int maxWidth;
    private int maxHeight;
    private int maxFileNameLength;

    public AvatarProperties(DataSize maxSize, AvatarProperties avatarProperties) {
        this.maxSize = maxSize;
        this.maxWidth = avatarProperties.getMaxWidth();
        this.maxHeight = avatarProperties.getMaxHeight();
    }
}
