package com.arquitechthor.kopi.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class KopiApplicationIT {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcClient jdbc;

    @Test
    void healthIsUp() {
        assertThat(mvc.get().uri("/actuator/health"))
                .hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.status").isEqualTo("UP");
    }

    @Test
    void readinessIncludesTheDatabase() {
        assertThat(mvc.get().uri("/actuator/health/readiness"))
                .hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.components.db.status").isEqualTo("UP");
    }

    @Test
    void servesTheHomePageInBothLanguages() {
        assertThat(mvc.get().uri("/?lang=en")).hasStatusOk().bodyText().contains("Play chess.");
        assertThat(mvc.get().uri("/?lang=es")).hasStatusOk().bodyText().contains("Juega al ajedrez.");
    }

    @Test
    void flywayAppliedEveryMigration() {
        var versions = jdbc.sql("select version from flyway_schema_history where success order by installed_rank")
                .query(String.class)
                .list();

        assertThat(versions).containsExactly("1", "2", "3");
    }
}
