package com.fanaujie.ripple.authorization.config;

import com.fanaujie.ripple.storage.repository.UserRepository;
import com.fanaujie.ripple.storage.repository.impl.CassandraUserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ComponentConfig {

    @Bean
    UserRepository userStorage() {
        return new CassandraUserRepository(null);
    }
}
