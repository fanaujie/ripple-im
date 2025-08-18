package com.fanaujie.ripple.uploadgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.fanaujie.ripple.uploadgateway", "com.fanaujie.ripple.database"})
@EntityScan(basePackages = "com.fanaujie.ripple.database.model")
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}