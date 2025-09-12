package com.fanaujie.ripple.uploadgateway.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI rippleUploadGatewayOpenAPI() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Ripple Upload Gateway")
                                .description(
                                        "File upload gateway for Ripple IM system, specialized in handling avatar and multimedia file uploads")
                                .version("1.0.0")
                                .contact(
                                        new Contact()
                                                .name("Ripple Team")
                                                .email("junjie725@gmail.com")))
                .servers(
                        List.of(
                                new Server()
                                        .url("http://localhost:10003")
                                        .description("Development Environment")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        "bearerAuth",
                                        new SecurityScheme()
                                                .name("bearerAuth")
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                                .description("JWT Bearer Token Authentication")));
    }
}
