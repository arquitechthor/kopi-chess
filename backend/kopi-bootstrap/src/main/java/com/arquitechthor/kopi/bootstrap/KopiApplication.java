package com.arquitechthor.kopi.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/// Entry point of Kopi Chess.
///
/// Component scanning is limited to this package on purpose: the domain and
/// application rings contain no Spring annotations, so their classes are
/// registered as beans explicitly with `@Configuration` classes in this
/// module (see ADR-002).
@SpringBootApplication
public class KopiApplication {

    static void main(String[] args) {
        SpringApplication.run(KopiApplication.class, args);
    }
}
