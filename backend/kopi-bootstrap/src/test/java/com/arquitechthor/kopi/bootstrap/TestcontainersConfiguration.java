package com.arquitechthor.kopi.bootstrap;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/// A throwaway PostgreSQL for integration tests and for local development.
///
/// `@ServiceConnection` makes Spring Boot point the datasource at the
/// container, so no URL or password is configured anywhere.
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /// Same image as `compose.yaml`; keep both in sync.
    static final String POSTGRES_IMAGE = "postgres:18.6-alpine3.24";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(POSTGRES_IMAGE);
    }
}
