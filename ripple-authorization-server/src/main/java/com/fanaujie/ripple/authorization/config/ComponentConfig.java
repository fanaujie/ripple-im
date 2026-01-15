package com.fanaujie.ripple.authorization.config;

import com.fanaujie.ripple.storage.service.RippleStorageFacade;
import com.fanaujie.ripple.storage.spi.RippleStorageLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;


@Configuration
public class ComponentConfig {

    @Bean
    RippleStorageFacade userStorage(Environment env) {
        return RippleStorageLoader.load(env::getProperty);
    }
}
