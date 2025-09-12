package com.fanaujie.ripple.apigateway.config;

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
    public OpenAPI rippleApiGatewayOpenAPI() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Ripple API Gateway")
                                .description(
                                        "Main API gateway for Ripple IM system, providing user profile and friend relationship management APIs")
                                .version("1.0.0")
                                .contact(
                                        new Contact()
                                                .name("Ripple Team")
                                                .email("junjie725@gmail.com")))
                .servers(
                        List.of(
                                new Server()
                                        .url("http://localhost:10002")
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
