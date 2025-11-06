package com.fanaujie.ripple.uploadgateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

@Component
@ConfigurationProperties(prefix = "message-attachment")
@Data
public class MessageAttachmentProperties {

    private DataSize maxFileSize = DataSize.ofMegabytes(100);
    private DataSize chunkSize = DataSize.ofMegabytes(5);
    private int maxExtensionLength = 10;
}
