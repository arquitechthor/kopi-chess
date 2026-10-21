package com.arquitechthor.kopi.bootstrap;

import org.springframework.boot.SpringApplication;

/// Runs Kopi Chess locally with a PostgreSQL started by Testcontainers.
///
/// Run this class from the IDE (it only needs Docker running) instead of
/// `KopiApplication` when developing: no compose file, no manual database.
public class TestKopiApplication {

    static void main(String[] args) {
        SpringApplication.from(KopiApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
