package com.fanaujie.ripple.uploadgateway.config;

import com.fanaujie.ripple.storage.driver.CassandraDriver;
import com.fanaujie.ripple.storage.repository.UserRepository;
import com.fanaujie.ripple.storage.repository.impl.CassandraUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class ComponentConfig {

    @Bean
    public UserRepository userRepository(
            @Value("${cassandra.contact-points}") List<String> contactPoints,
            @Value("${cassandra.keyspace-name}") String keyspace,
            @Value("${cassandra.local-datacenter}") String localDatacenter) {
        return new CassandraUserRepository(
                CassandraDriver.createCqlSession(contactPoints, keyspace, localDatacenter));
    }
}
