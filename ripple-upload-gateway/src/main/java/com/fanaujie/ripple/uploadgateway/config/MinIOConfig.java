package com.fanaujie.ripple.uploadgateway.config;

import com.fanaujie.ripple.uploadgateway.service.MinioStorageService;
import io.minio.MinioAsyncClient;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class MinIOConfig {

    @Value("${minio.endpoint}")
    private String endpoint;

    @Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKey;

    @Bean
    public MinioClient minioClient() {
        log.info("Creating MinIO client with endpoint: {}", endpoint);
        return MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
    }

    @Bean
    public ApplicationRunner ensureMessageBucketExists(MinioStorageService minioStorageService) {
        return args -> {
            log.info("Ensuring message bucket exists...");
            minioStorageService.ensureBucketExists(MinioStorageService.BucketType.AVATAR);
            minioStorageService.ensureBucketExists(
                    MinioStorageService.BucketType.MESSAGE_ATTACHMENT);
        };
    }
}
