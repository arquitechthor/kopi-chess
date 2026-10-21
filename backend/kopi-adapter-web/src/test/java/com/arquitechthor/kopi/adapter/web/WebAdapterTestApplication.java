package com.arquitechthor.kopi.adapter.web;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

/// Minimal Spring Boot configuration so the web adapter can be tested on its
/// own (`@WebMvcTest`), without `kopi-bootstrap` or a database.
@SpringBootConfiguration
@EnableAutoConfiguration
@Import(WebAdapterConfiguration.class)
class WebAdapterTestApplication {
}
