package com.fanaujie.ripple.uploadgateway.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

import java.util.Optional;

@EnableConfigurationProperties
@Configuration
@ConfigurationProperties(prefix = "avatar")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AvatarProperties {
    private DataSize maxSize;
    private String[] allowedContentTypes;
    private int maxWidth;
    private int maxHeight;

    public Optional<String> getExtension(String contentType) {
        for (String type : this.allowedContentTypes) {
            if (type.equalsIgnoreCase(contentType)) {
                return switch (type.toLowerCase()) {
                    case "image/jpeg" -> Optional.of("jpg");
                    case "image/png" -> Optional.of("png");
                    default -> Optional.empty();
                };
            }
        }
        return Optional.empty();
    }

    public boolean isContentTypeAllowed(String contentType) {
        for (String type : this.allowedContentTypes) {
            if (type.equalsIgnoreCase(contentType)) {
                return true;
            }
        }
        return false;
    }
}
