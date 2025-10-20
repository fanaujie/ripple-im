package com.fanaujie.ripple.authorization.config;

import com.datastax.oss.driver.api.core.CqlSession;
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
    public CqlSession cqlSession(
            @Value("${cassandra.contact-points}") List<String> contactPoints,
            @Value("${cassandra.keyspace-name}") String keyspace,
            @Value("${cassandra.local-datacenter}") String localDatacenter) {
        return CassandraDriver.createCqlSession(contactPoints, keyspace, localDatacenter);
    }

    @Bean
    UserRepository userStorage(CqlSession cqlSession) {
        return new CassandraUserRepository(cqlSession);
    }
}
